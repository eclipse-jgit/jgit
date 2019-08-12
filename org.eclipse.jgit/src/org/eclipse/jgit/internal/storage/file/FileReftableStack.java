/*
 * Copyright (C) 2017 Google LLC
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.MergedReftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mutable stack of reftables on local filesystem storage. Not thread-safe.
 * This is an AutoCloseable because this object owns the file handles to the open reftables.
 */
class FileReftableStack implements AutoCloseable {
	private final static Logger LOG = LoggerFactory
			.getLogger(FileReftableStack.class);

	private static class StackEntry {
		String name;

		ReftableReader reftableReader;
	}

	private MergedReftable mergedReftable;

	private List<StackEntry> stack;

	private long lastNextUpdateIndex;

	private final File stackPath;

	private final File reftableDir;

	private final Runnable onChange;

	private final Supplier<Config> configSupplier;

	// Used for stats & testing.
	static class CompactionStats {
		long tables;

		long bytes;

		int count;

		CompactionStats() {
			tables = 0;
			bytes = 0;
			count = 0;
		}
	}

	private final CompactionStats stats;

	/**
	 * Creates a stack corresponding to the list of reftables in the argument
	 *
	 * @param stackPath
	 *            the filename for the stack.
	 * @param reftableDir
	 *            the dir holding the tables.
	 * @param onChange
	 *            hook to call if we notice a new write
	 * @param configSupplier
	 *            Config supplier
	 * @throws IOException
	 */
	FileReftableStack(File stackPath, File reftableDir,
			@Nullable Runnable onChange, Supplier<Config> configSupplier)
			throws IOException {
		this.stackPath = stackPath;
		this.reftableDir = reftableDir;
		this.stack = new ArrayList<>();
		this.configSupplier = configSupplier;
		this.onChange = onChange;

		// skip event notification
		lastNextUpdateIndex = 0;
		reload();

		stats = new CompactionStats();
	}

	public CompactionStats getStats() {
		return stats;
	}

	/**
	 * reloads the stack, potentially reusing opened reftableReaders.
	 *
	 * @throws RetryException
	 *             load must be retried.
	 * @throws IOException
	 *             on other IO errors.
	 */
	private void reloadOnce() throws IOException {
		List<String> names = readTableNames();

		Map<String, ReftableReader> current = stack.stream()
				.collect(Collectors.toMap(e -> e.name, e -> e.reftableReader));

		List<ReftableReader> newTables = new ArrayList<>();
		List<StackEntry> newStack = new ArrayList<>(stack.size()+1);
		try {
			ReftableReader last = null;
			for (String name : names) {
				StackEntry entry = new StackEntry();
				entry.name = name;

				if (current.containsKey(name)) {
					entry.reftableReader = current.remove(name);
				} else {
					File subtable = new File(reftableDir, name);
					FileInputStream is;
					try {
						is = new FileInputStream(subtable);
					} catch (FileNotFoundException e) {
						// External process compacted the stack.
						throw new RetryException();
					}

					entry.reftableReader = new ReftableReader(
							BlockSource.from(is));
					newTables.add(entry.reftableReader);
				}

				if (last != null) {
					// It would be better to do this check in MergedReftable,
					// but min/maxUpdateIndex() throws IOException.
					if (last.maxUpdateIndex() >= entry.reftableReader
							.minUpdateIndex()) {
						throw new IllegalStateException(MessageFormat.format(
								JGitText.get().indexNumbersNotIncreasing, name,
								Long.valueOf(entry.reftableReader.minUpdateIndex()),
								Long.valueOf(last.maxUpdateIndex())));
					}
				}
				last = entry.reftableReader;

				newStack.add(entry);
			}
		} catch (Exception e) {
			newTables.forEach(t -> {
				try {
					t.close();
				} catch (IOException ioe) {
					// ignore reader close errors.
					LOG.error(e.getMessage(), e);
				}
			});
			throw e;
		}

		// survived without exceptions: swap in new stack, and close dangling tables.
		stack = newStack;
		current.values().forEach(r -> {
				try {
					r.close();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			});
	}

	void reload() throws IOException {
		while (true) {
			try {
				reloadOnce();
				break;
			} catch (RetryException e) {
				// try again.
			}
		}

		mergedReftable = new MergedReftable(stack.stream()
				.map(x -> x.reftableReader).collect(Collectors.toList()));
		long curr = nextUpdateIndex();
		if (lastNextUpdateIndex > 0 && lastNextUpdateIndex != curr
				&& onChange != null) {
			onChange.run();
		}
		lastNextUpdateIndex = curr;
	}

	MergedReftable getMergedReftable() {
		return mergedReftable;
	}

	/** Writer is a callable that writes data to a reftable under construction.
	 * It should set the min/max update index, and then write refs and/or logs.
	 * It should not call finish() on the writer.
	 */
	interface Writer {
		void call(ReftableWriter w) throws IOException;
	}

	private static class RetryException extends IOException {
		private static final long serialVersionUID = 1L;
		// empty
	}

	private List<String> readTableNames() throws IOException {
		List<String> names = new ArrayList<>(stack.size() + 1);

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(stackPath), UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.isEmpty()) {
					names.add(line);
				}
			}
		} catch (FileNotFoundException e) {
			// file isn't there: empty repository.
		}
		return names;
	}

	/**
	 * @throws IOException
	 *             on IO problem
	 * @return true if the on-disk file corresponds to the in-memory data.
	 */
	public boolean isUpToDate() throws IOException {
		// TODO: could use FileSnapshot to avoid reading the file.
		try {
			List<String> names = readTableNames();
			if (names.size() != stack.size()) {
				return false;
			}
			for (int i = 0; i < names.size(); i++) {
				if (!names.get(i).equals(stack.get(i).name)) {
					return false;
				}
			}
		} catch (FileNotFoundException e) {
			return stack.isEmpty();
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		for (StackEntry entry : stack) {
			try {
				entry.reftableReader.close();
			} catch (Exception e) {
				// we are reading. We don't care about exceptions, but log anyway.
				LOG.error(e.getMessage(), e);
			}
		}
	}

	private long nextUpdateIndex() throws IOException {
		return stack.size() > 0
				? stack.get(stack.size() - 1).reftableReader.maxUpdateIndex()
						+ 1
				: 1;
	}

	@SuppressWarnings({ "nls" })
	private String filename(long low, long high) {
		return String.format("%012x-%012x", Long.valueOf(low), Long.valueOf(high));
	}

	/**
	 * Tries to add a new reftable to the stack. Returns true if it succeeded, or false if
	 * there was a lock failure, due to races with other processes. This is package private so
	 * FileReftableDatabase can call into here.
	 *
	 * @return true if the transaction.
	 */
	@SuppressWarnings("nls")
	boolean addReftable(Writer w) throws IOException {
		LockFile lock = new LockFile(stackPath);
		try {
			if (!lock.lockForAppend()) {
				return false;
			}
			if (!isUpToDate()) {
				return false;
			}

			String fn = filename(nextUpdateIndex(), nextUpdateIndex());

			File tmpTable = File.createTempFile(fn + "_", ".ref",
					stackPath.getParentFile());

			ReftableWriter.Stats s;
			try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
				ReftableWriter rw = new ReftableWriter(reftableConfig(), fos);
				w.call(rw);
				rw.finish();
				s = rw.getStats();
			}

			if (s.minUpdateIndex() != nextUpdateIndex()) {
				return false;
			}

			// The spec says to name log-only files with .log, which is somewhat
			// pointless given compaction, but we do so anyway.
			fn += s.refCount() > 0 ? ".ref" : ".log";
			File dest = new File(reftableDir, fn);

			FileUtils.rename(tmpTable, dest, StandardCopyOption.ATOMIC_MOVE);
			lock.write((fn + "\n").getBytes(UTF_8));
			if (!lock.commit()) {
				FileUtils.delete(dest);
				return false;
			}

			reload();

			autoCompact();
		} finally {
			lock.unlock();
		}
		return true;
	}

	private ReftableConfig reftableConfig() {
		return new ReftableConfig(configSupplier.get());
	}

	/**
	 * Write the reftable for the given range into a temp file.
	 *
	 * @return the file holding the replacement table.
	 */
	private File compactLocked(int first, int last) throws IOException {
		String fn = filename(first, last);

		File tmpTable = File.createTempFile(fn + "_", ".ref", //$NON-NLS-1$//$NON-NLS-2$
				stackPath.getParentFile());
		try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
			ReftableCompactor c = new ReftableCompactor(fos)
					.setConfig(reftableConfig())
					.setMinUpdateIndex(
							stack.get(first).reftableReader.minUpdateIndex())
					.setMaxUpdateIndex(
							stack.get(last).reftableReader.maxUpdateIndex())
					.setIncludeDeletes(first > 0);

			List<ReftableReader> compactMe = new ArrayList<>();
			for (int i = first; i <= last; i++) {
				compactMe.add(stack.get(i).reftableReader);
			}
			c.addAll(compactMe);

			c.compact();
		}

		return tmpTable;
	}

	/**
	 * Compacts a range of the stack, following the file locking protocol documented in the spec.
	 *
	 * @return true if a compaction was successfully applied.
	 */
	boolean compactRange(int first, int last) throws IOException {
		if (first >= last) {
			return true;
		}
		LockFile lock = new LockFile(stackPath);

		File tmpTable = null;
		List<LockFile> subtableLocks = new ArrayList<>();

		long bytes = 0;
		long tables = 0;
		try {
			if (!lock.lock()) {
				return false;
			}
			if (!isUpToDate()) {
				return false;
			}

			List<File> deleteOnSuccess = new ArrayList<>();
			for (int i = first; i <= last; i++) {
				File f = new File(reftableDir, stack.get(i).name);
				LockFile lf = new LockFile(f);
				if (!lf.lock()) {
					return false;
				}
				subtableLocks.add(lf);
				deleteOnSuccess.add(f);
				tables++;
				bytes += stack.get(i).reftableReader.size();
			}

			lock.unlock();
			lock = null;

			tmpTable = compactLocked(first, last);

			lock = new LockFile(stackPath);
			if (!lock.lock()) {
				return false;
			}
			if (!isUpToDate()) {
				return false;
			}

			String fn = filename(
					stack.get(first).reftableReader.minUpdateIndex(),
					stack.get(last).reftableReader.maxUpdateIndex());

			// The spec suggests to use .log for log-only tables, but there
			// seems to be no good reason for this.
			fn += ".ref"; //$NON-NLS-1$
			File dest = new File(reftableDir, fn);

			FileUtils.rename(tmpTable, dest, StandardCopyOption.ATOMIC_MOVE);
			tmpTable = null;

			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < first; i++) {
				sb.append(stack.get(i).name + "\n"); //$NON-NLS-1$
			}
			sb.append(fn + "\n"); //$NON-NLS-1$
			for (int i = last + 1; i < stack.size(); i++) {
				sb.append(stack.get(i).name + "\n"); //$NON-NLS-1$
			}

			lock.write(sb.toString().getBytes(UTF_8));
			if (!lock.commit()) {
				dest.delete();
				return false;
			}

			stats.bytes += bytes;
			stats.tables += tables;
			stats.count++;

			for (File f : deleteOnSuccess) {
				Files.delete(f.toPath());
			}

			reload();
			return true;
		} finally {
			if (tmpTable != null) {
				tmpTable.delete();
			}
			for (LockFile lf : subtableLocks) {
				lf.unlock();
			}
			if (lock != null) {
				lock.unlock();
			}
		}
	}

	// Calculate an approximate log_2().
	private int log2(long sz) {
		int l = 0;
		while (sz > 0) {
			l++;
			sz /= 2;
		}

		return l;
	}

	// A segment is a consecutive list of reftables of the same approximate size.
	private static class Segment {
		int log;

		int start;

		int end; // exclusive.

		int size() {
			return end - start;
		}
	}

	/**
	 * heuristically tries to compact the stack if the stack has a suitable shape.
 	 */
	private void autoCompact() throws IOException {
		// The cost of compaction is proportional to the size, and we want to
		// avoid frequent large compactions. We do this by playing the game 2048
		// here: first
		// compact together the smallest tables if there are more than one. Then
		// try to
		// see if the result is big enough to match up with
		// next up.
		int i = 0;

		int segmentStart = -1;
		int lastLog = -1;

		List<Segment> segments = new ArrayList<>();
		for (StackEntry e : stack) {
			int l = log2(e.reftableReader.size());
			if (l != lastLog) {
				if (segmentStart > 0) {
					Segment s = new Segment();
					s.log = l;
					s.start = segmentStart;
					s.end = i;
					segments.add(s);
				}
				segmentStart = i;
			}

			lastLog = l;
			i++;
		}

		if (stack.size() > 0) {
			Segment s = new Segment();
			s.log = lastLog;
			s.start = segmentStart;
			s.end = stack.size();
			segments.add(s);
		}

		segments = segments.stream().filter(s -> s.size() > 1)
				.collect(Collectors.toList());
		if (segments.isEmpty()) {
			return;
		}

		Optional<Segment> minSeg = segments.stream()
				.min(Comparator.comparing(s -> Integer.valueOf(s.log)));

		int start = minSeg.get().start;
		int end = minSeg.get().end;

		long total = 0;
		for (int j = start; j < end; j++) {
			total += stack.get(j).reftableReader.size();
		}

		while (start > 0) {
			int prev = start - 1;
			long prevSize = stack.get(prev).reftableReader.size();
			if (log2(total) < log2(prevSize)) {
				break;
			}
			start = prev;
			total += prevSize;
		}

		compactRange(start, end - 1);
	}
}
