package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.Reftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableCompactor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableStack;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
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
import java.util.List;
import java.util.Map;
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

	private long lastNextUpdateIndex;

	private final File stackPath;

	private final File reftableDir;

	private final Runnable onChange;

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
			@Nullable Runnable onChange) throws IOException {
		this.stackPath = stackPath;
		this.reftableDir = reftableDir;
		stack = new ArrayList<>();
		this.onChange = onChange;

		// skip event notification
		lastNextUpdateIndex = 0;
		reload();
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

	private void reload() throws IOException {
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
		// XXX - would be nice if we could have an API amenable to retry.
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
				// XXX get the config from somewhere.
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

			// XXX this should depend on a config setting.
			autoCompact();
		} finally {
			lock.unlock();
		}
		return true;
	}

	ReftableConfig reftableConfig() {
		return new ReftableConfig();
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

	void autoCompact() throws IOException {
		long size = 0;
		for (StackEntry e : stack) {
			size += e.reftableReader.size();
		}

		// XXX make this configurable
		long threshold = size / 2;
		int first = -1;
		int i = 0;
		for (StackEntry e : stack) {
			if (e.reftableReader.size() < threshold) {
				threshold -= e.reftableReader.size();
				first = i;
				break;
			}
			i++;
			threshold /= 2;
		}

		if (first < 0) {
			return;
		}

		int last = first;
		while (first >= 0 && threshold >= 0 && last < stack.size() - 1) {
			threshold -= stack.get(last).reftableReader.size();
			last++;
		}

		// We try just once. If it failed here, it will succeed some other time.
		compactRange(first, last);
	}
}
