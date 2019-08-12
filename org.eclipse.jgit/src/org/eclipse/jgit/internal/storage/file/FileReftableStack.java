package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.Reftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableStack;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A stack of reftables on local filesystem storage. Not thread-safe.
 */
class FileReftableStack implements ReftableStack {
	private static class StackEntry {
		String name;

		ReftableReader reftableReader;
	}

	// Used for stats & testing.
	public static class CompactionStats {
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

	private long lastNextUpdateIndex;

	private final File stackPath;

	private final File reftableDir;

	private final Runnable onChange;

	private final Supplier<Config> configSupplier;

	private List<StackEntry> stack;

	/**
	 * Creates a stack corresponding to the list of reftables in the argument
	 *
	 * @param stackPath
	 *            the filename for the stack.
	 * @param reftableDir
	 *            the dir holding the tables.
	 * @param onChange
	 *            hook to call if we notice a new write
	 */
	FileReftableStack(File stackPath, File reftableDir,
			@Nullable Runnable onChange,
			  Supplier<Config> configSupplier) throws IOException {
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
	 *             load must be retried. In this case, the stack will be empty.
	 * @throws IOException
	 *             on other IO errors.
	 */
	private void reloadOnce() throws IOException {
		Map<String, ReftableReader> current = stack.stream()
				.collect(Collectors.toMap(e -> e.name, e -> e.reftableReader));
		stack.clear();

		ReftableReader last = null;
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(stackPath), UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				StackEntry entry = new StackEntry();
				entry.name = line;

				if (current.containsKey(line)) {
					entry.reftableReader = current.remove(line);
				} else {
					File subtable = new File(reftableDir, line);
					FileInputStream is;
					try {
						is = new FileInputStream(subtable);
					} catch (FileNotFoundException e) {
						// External process compacted the stack.
						throw new RetryException();
					}

					entry.reftableReader = new ReftableReader(
							BlockSource.from(is));
				}

				if (last != null) {
					// XXX this check should be in MergedReftable
					if (last.maxUpdateIndex() >= entry.reftableReader
							.minUpdateIndex()) {
						throw new IllegalStateException(String.format(
								"index numbers not increasing %s: min %d, last max %d",
								line, entry.reftableReader.minUpdateIndex(),
								last.maxUpdateIndex()));
					}
				}
				last = entry.reftableReader;

				stack.add(entry);
			}

		} catch (FileNotFoundException e) {
			// stack is not there yet: empty repo, continue without error.
		} catch (Exception e) {
			close();
			throw e;
		} finally {
			current.values().forEach(r -> {
				try {
					r.close();
				} catch (IOException e) {

				}
			});
		}
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

		long curr = nextUpdateIndex();
		if (lastNextUpdateIndex > 0 && lastNextUpdateIndex != curr
				&& onChange != null) {
			onChange.run();
		}
		lastNextUpdateIndex = curr;
	}

	/** {@inheritDoc} */
	@Override
	public List<Reftable> readers() {
		return stack.stream().map(x -> x.reftableReader)
				.collect(Collectors.toList());
	}

	interface Writer {
		void call(ReftableWriter w) throws IOException;
	}

	private static class RetryException extends IOException {

	}

	/**
	 * @returns true if the on-disk file corresponds to the in-memory data.
	 */
	public boolean uptodate() throws IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(stackPath), UTF_8))) {
			for (StackEntry e : stack) {
				if (!e.name.equals(br.readLine())) {
					return false;
				}
			}
			if (br.readLine() != null) {
				return false;
			}
		} catch (FileNotFoundException e) {
			return stack.isEmpty();
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// Nop.  This should be closed from FileRepo explicitly.
		// XXX this is ugly - refactor DFS interface instead.
	}

	// XXX
	public void explicitClose() {
		for (StackEntry entry : stack) {
			try {
				entry.reftableReader.close();
			} catch (Exception e) {
				// we are reading. We don't care about exceptions.
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public long nextUpdateIndex() throws IOException {
		// This could be moved into common place if Reftable had a maxUpdate
		// field.
		long updateIndex = 0;
		for (StackEntry e : stack) {
			updateIndex = Math.max(updateIndex,
					e.reftableReader.maxUpdateIndex());

		}
		return updateIndex + 1;
	}

	private String filename(long low, long high) {
		// 64 bit should use %020 ?
		return String.format("%06d-%06d", low, high);
	}

	boolean addReftable(Writer w) throws IOException {
		LockFile lock = new LockFile(stackPath);
		try {
			if (!lock.lockForAppend()) {
				return false;
			}
			if (!uptodate()) {
				return false;
			}

			String fn = filename(nextUpdateIndex(), nextUpdateIndex());

			File tmpTable = File.createTempFile(fn + "_", ".ref",
					stackPath.getParentFile());

			ReftableWriter.Stats stats;
			try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
				ReftableWriter rw = new ReftableWriter(reftableConfig(), fos);
				w.call(rw);
				rw.finish();
				stats = rw.getStats();
			}

			fn += stats.refCount() > 0 ? ".ref" : ".log";
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

	ReftableConfig reftableConfig() {
		return new ReftableConfig(configSupplier.get());
	}

	File compactLocked(int first, int last) throws IOException {
		ReftableCompactor c = new ReftableCompactor()
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

		String fn = filename(first, last);
		File tmpTable = File.createTempFile(fn + "_", ".ref",
				stackPath.getParentFile());

		try (FileOutputStream fos = new FileOutputStream(tmpTable)) {
			c.compact(fos);
		}

		return tmpTable;
	}

	boolean compactRange(int first, int last) throws IOException {
		if (first >= last) {
			return true;
		}
		LockFile lock = new LockFile(stackPath);
		File dir = new File(stackPath.getParentFile(), Constants.REFTABLE);

		File tmpTable = null;
		List<LockFile> subtableLocks = new ArrayList<>();

		long bytes = 0;
		long tables = 0;
		try {
			if (!lock.lock()) {
				return false;
			}
			if (!uptodate()) {
				return false;
			}

			List<File> deleteOnSuccess = new ArrayList<>();
			for (int i = first; i <= last; i++) {
				File f = new File(dir, stack.get(i).name);
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
			if (!uptodate()) {
				return false;
			}

			String fn = filename(
					stack.get(first).reftableReader.minUpdateIndex(),
					stack.get(last).reftableReader.maxUpdateIndex());

			// XXX .log ?
			fn += ".ref";
			File dest = new File(reftableDir, fn);

			FileUtils.rename(tmpTable, dest, StandardCopyOption.ATOMIC_MOVE);
			tmpTable = null;

			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < first; i++) {
				sb.append(stack.get(i).name + "\n");
			}
			sb.append(fn + "\n");
			for (int i = last + 1; i < stack.size(); i++) {
				sb.append(stack.get(i).name + "\n");
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

	int log2(long sz) {
		int l = 0;
		while (sz > 0) {
			l++;
			sz /= 2;
		}

		return l;
	}

	private static class Segment {
		int log;
		int start;
		int end; // exclusive.

		int size() { return end -start; }
	};

	void autoCompact() throws IOException {
		// The cost of compaction is proportional to the size, and we want to avoid frequent
		// large compactions. We do this by playing the game 2048 here: first compact
		// together the smallest tables if there are more than one. Then try to see if the result is big enough to match up with
		// next up.
		int i = 0;

		int segmentStart = -1;
		int lastLog = -1;

		List<Segment > segments = new ArrayList<>();
		for (StackEntry e : stack) {
			int l = log2(e.reftableReader.size());
			if (l != lastLog) {
				if (segmentStart > 0) {
					Segment s  = new Segment();
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
			Segment s  = new Segment();
			s.log = lastLog;
			s.start = segmentStart;
			s.end = stack.size();
			segments.add(s);
		}

		segments = segments.stream().filter(s -> s.size() > 1).collect(Collectors.toList());
		if (segments.isEmpty()) {
			return;
		}

		Optional<Segment> minSeg =  segments.stream().min(Comparator.comparing(s -> s.log));

		int start = minSeg.get().start;
		int end = minSeg.get().end;

		long total = 0;
		for (int j = start; j < end; j++) {
			total += stack.get(j).reftableReader.size();
		}

		while (start > 0) {
			int prev = start -1;
			long prevSize = stack.get(prev).reftableReader.size();
			if (log2(total) < log2(prevSize)) {
				break;
			}
			start = prev;
			total += prevSize;
		}

		compactRange(start, end-1);
	}
}
