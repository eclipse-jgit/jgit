/*
 * Copyright (C) 2019 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.MergedReftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * A mutable stack of reftables on local filesystem storage. Not thread-safe.
 * This is an AutoCloseable because this object owns the file handles to the
 * open reftables.
 */
public class FileReftableStack implements AutoCloseable {
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

	private final SecureRandom random = new SecureRandom();

	private final Supplier<Config> configSupplier;

	// Used for stats & testing.
	static class CompactionStats {

		long tables;

		long bytes;

		int attempted;

		int failed;

		long refCount;

		long logCount;

		CompactionStats() {
			tables = 0;
			bytes = 0;
			attempted = 0;
			failed = 0;
			logCount = 0;
			refCount = 0;
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
	 *             on I/O problems
	 */
	public FileReftableStack(File stackPath, File reftableDir,
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

	CompactionStats getStats() {
		return stats;
	}

	/**
	 * Reloads the stack, potentially reusing opened reftableReaders.
	 *
	 * @param names
	 *            holds the names of the tables to load.
	 * @throws FileNotFoundException
	 *             load must be retried.
	 * @throws IOException
	 *             on other IO errors.
	 */
	private void reloadOnce(List<String> names)
			throws IOException, FileNotFoundException {
		Map<String, ReftableReader> current = stack.stream()
				.collect(Collectors.toMap(e -> e.name, e -> e.reftableReader));

		List<ReftableReader> newTables = new ArrayList<>();
		List<StackEntry> newStack = new ArrayList<>(stack.size() + 1);
		try {
			for (String name : names) {
				StackEntry entry = new StackEntry();
				entry.name = name;

				ReftableReader t = null;
				if (current.containsKey(name)) {
					t = current.remove(name);
				} else {
					File subtable = new File(reftableDir, name);
					FileInputStream is;

					is = new FileInputStream(subtable);

					t = new ReftableReader(BlockSource.from(is));
					newTables.add(t);
				}

				entry.reftableReader = t;
				newStack.add(entry);
			}
			// survived without exceptions: swap in new stack, and close
			// dangling tables.
			stack = newStack;
			newTables.clear();

			current.values().forEach(r -> {
				try {
					r.close();
				} catch (IOException e) {
					throw new AssertionError(e);
				}
			});
		} finally {
			newTables.forEach(t -> {
				try {
					t.close();
				} catch (IOException ioe) {
					// reader close should not generate errors.
					throw new AssertionError(ioe);
				}
			});
		}
	}

	void reload() throws IOException {
		// Try for 2.5 seconds.
		long deadline = System.currentTimeMillis() + 2500;
		// A successful reftable transaction is 2 atomic file writes
		// (open, write, close, rename), which a fast Linux system should be
		// able to do in about ~200us. So 1 ms should be ample time.
		long min = 1;
		long max = 1000;
		long delay = 0;
		boolean success = false;

		// Don't check deadline for the first 3 retries, so we can step with a
		// debugger without worrying about deadlines.
		int tries = 0;
		while (tries < 3 || System.currentTimeMillis() < deadline) {
			List<String> names = readTableNames();
			tries++;
			try {
				reloadOnce(names);
				success = true;
				break;
			} catch (FileNotFoundException e) {
				List<String> changed = readTableNames();
				if (changed.equals(names)) {
					throw e;
				}
			}

			delay = FileUtils.delay(delay, min, max);
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		}

		if (!success) {
			throw new LockFailedException(stackPath);
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

	/**
	 * @return the merged reftable
	 */
	public MergedReftable getMergedReftable() {
		return mergedReftable;
	}

	/**
	 * Writer is a callable that writes data to a reftable under construction.
	 * It should set the min/max update index, and then write refs and/or logs.
	 * It should not call finish() on the writer.
	 */
	public interface Writer {
		/**
		 * Write data to reftable
		 *
		 * @param w
		 *            writer to use
		 * @throws IOException
		 */
		void call(ReftableWriter w) throws IOException;
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
	 * @return true if the on-disk file corresponds to the in-memory data.
	 * @throws IOException
	 *             on IO problem
	 */
	boolean isUpToDate() throws IOException {
		// We could use FileSnapshot to avoid reading the file, but the file is
		// small so it's probably a minor optimization.
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		for (StackEntry entry : stack) {
			try {
				entry.reftableReader.close();
			} catch (Exception e) {
				// we are reading; this should never fail.
				throw new AssertionError(e);
			}
		}
	}

	private long nextUpdateIndex() throws IOException {
		return stack.size() > 0
				? stack.get(stack.size() - 1).reftableReader.maxUpdateIndex()
						+ 1
				: 1;
	}

	private String filename(long low, long high) {
		return String.format("%012x-%012x-%08x", //$NON-NLS-1$
				Long.valueOf(low), Long.valueOf(high),
				Integer.valueOf(random.nextInt()));
	}

	/**
	 * Tries to add a new reftable to the stack. Returns true if it succeeded,
	 * or false if there was a lock failure, due to races with other processes.
	 * This is package private so FileReftableDatabase can call into here.
	 *
	 * @param w
	 *            writer to write data to a reftable under construction
	 * @return true if the transaction was successful.
	 * @throws IOException
	 *             on I/O problems
	 */
	@SuppressWarnings("nls")
	public boolean addReftable(Writer w) throws IOException {
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

			if (s.minUpdateIndex() < nextUpdateIndex()) {
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
	 * @param first
	 *            index of first stack entry to be written
	 * @param last
	 *            index of last stack entry to be written
	 * @return the file holding the replacement table.
	 * @throws IOException
	 *             on I/O problem
	 */
	private File compactLocked(int first, int last) throws IOException {
		String fn = filename(first, last);

		File tmpTable = File.createTempFile(fn + "_", ".ref", //$NON-NLS-1$//$NON-NLS-2$
				stackPath.getParentFile());
		try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
			ReftableCompactor c = new ReftableCompactor(fos)
					.setConfig(reftableConfig())
					.setIncludeDeletes(first > 0);

			List<ReftableReader> compactMe = new ArrayList<>();
			long totalBytes = 0;
			for (int i = first; i <= last; i++) {
				compactMe.add(stack.get(i).reftableReader);
				totalBytes += stack.get(i).reftableReader.size();
			}
			c.addAll(compactMe);

			c.compact();

			// Even though the compaction did not definitely succeed, we keep
			// tally here as we've expended the effort.
			stats.bytes += totalBytes;
			stats.tables += first - last + 1;
			stats.attempted++;
			stats.refCount += c.getStats().refCount();
			stats.logCount += c.getStats().logCount();
		}

		return tmpTable;
	}

	/**
	 * Compacts a range of the stack, following the file locking protocol
	 * documented in the spec.
	 *
	 * @param first
	 *            index of first stack entry to be considered in compaction
	 * @param last
	 *            index of last stack entry to be considered in compaction
	 * @return true if a compaction was successfully applied.
	 * @throws IOException
	 *             on I/O problem
	 */
	boolean compactRange(int first, int last) throws IOException {
		if (first >= last) {
			return true;
		}
		LockFile lock = new LockFile(stackPath);

		File tmpTable = null;
		List<LockFile> subtableLocks = new ArrayList<>();

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

			// The spec suggests to use .log for log-only tables, and collect
			// all log entries in a single file at the bottom of the stack. That would
			// require supporting overlapping ranges for the different tables. For the
			// sake of simplicity, we simply ignore this and always produce a log +
			// ref combined table.
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

			reload();
			for (File f : deleteOnSuccess) {
				try {
					Files.delete(f.toPath());
				} catch (IOException e) {
					// Ignore: this can happen on Windows in case of concurrent processes.
					// leave the garbage and continue.
					if (!SystemReader.getInstance().isWindows()) {
						throw e;
					}
				}
			}

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

	/**
	 * Calculate an approximate log2.
	 *
	 * @param sz
	 * @return log2
	 */
	static int log(long sz) {
		long base = 2;
		if (sz <= 0) {
			throw new IllegalArgumentException("log2 negative"); //$NON-NLS-1$
		}
		int l = 0;
		while (sz > 0) {
			l++;
			sz /= base;
		}

		return l - 1;
	}

	/**
	 * A segment is a consecutive list of reftables of the same approximate
	 * size.
	 */
	static class Segment {
		// the approximate log_2 of the size.
		int log;

		// The total bytes in this segment
		long bytes;

		int start;

		int end; // exclusive.

		int size() {
			return end - start;
		}

		Segment(int start, int end, int log, long bytes) {
			this.log = log;
			this.start = start;
			this.end = end;
			this.bytes = bytes;
		}

		Segment() {
			this(0, 0, 0, 0);
		}

		@Override
		public int hashCode() {
			return 0; // appease error-prone
		}

		@Override
		public boolean equals(Object other) {
			if (other == null) {
				return false;
			}
			Segment o = (Segment) other;
			return o.bytes == bytes && o.log == log && o.start == start
					&& o.end == end;
		}

		@SuppressWarnings("boxing")
		@Override
		public String toString() {
			return String.format("{ [%d,%d) l=%d sz=%d }", start, end, log, //$NON-NLS-1$
					bytes);
		}
	}

	static List<Segment> segmentSizes(long[] sizes) {
		List<Segment> segments = new ArrayList<>();
		Segment cur = new Segment();
		for (int i = 0; i < sizes.length; i++) {
			int l = log(sizes[i]);
			if (l != cur.log && cur.bytes > 0) {
				segments.add(cur);
				cur = new Segment();
				cur.start = i;
				cur.log = l;
			}

			cur.log = l;
			cur.end = i + 1;
			cur.bytes += sizes[i];
		}
		segments.add(cur);
		return segments;
	}

	private static Optional<Segment> autoCompactCandidate(long[] sizes) {
		if (sizes.length == 0) {
			return Optional.empty();
		}

		// The cost of compaction is proportional to the size, and we want to
		// avoid frequent large compactions. We do this by playing the game 2048
		// here: first compact together the smallest tables if there are more
		// than one. Then try to see if the result will be big enough to match
		// up with next up.

		List<Segment> segments = segmentSizes(sizes);
		segments = segments.stream().filter(s -> s.size() > 1)
				.collect(Collectors.toList());
		if (segments.isEmpty()) {
			return Optional.empty();
		}

		Optional<Segment> optMinSeg = segments.stream()
				.min(Comparator.comparing(s -> Integer.valueOf(s.log)));
		// Input is non-empty, so always present.
		Segment smallCollected = optMinSeg.get();
		while (smallCollected.start > 0) {
			int prev = smallCollected.start - 1;
			long prevSize = sizes[prev];
			if (log(smallCollected.bytes) < log(prevSize)) {
				break;
			}
			smallCollected.start = prev;
			smallCollected.bytes += prevSize;
		}

		return Optional.of(smallCollected);
	}

	/**
	 * Heuristically tries to compact the stack if the stack has a suitable
	 * shape.
	 *
	 * @throws IOException
	 */
	private void autoCompact() throws IOException {
		Optional<Segment> cand = autoCompactCandidate(tableSizes());
		if (cand.isPresent()) {
			if (!compactRange(cand.get().start, cand.get().end - 1)) {
				stats.failed++;
			}
		}
	}

	// 68b footer, 24b header = 92.
	private static long OVERHEAD = 91;

	private long[] tableSizes() throws IOException {
		long[] sizes = new long[stack.size()];
		for (int i = 0; i < stack.size(); i++) {
			// If we don't subtract the overhead, the file size isn't
			// proportional to the number of entries. This will cause us to
			// compact too often, which is expensive.
			sizes[i] = stack.get(i).reftableReader.size() - OVERHEAD;
		}
		return sizes;
	}

	void compactFully() throws IOException {
		if (!compactRange(0, stack.size() - 1)) {
			stats.failed++;
		}
	}
}
