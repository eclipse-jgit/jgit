/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame.cache;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.sha1.SHA1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blame cache using the file system as storage.
 */
public class FileBlameCache implements BlameCache {
	private final File cacheDir;

	/**
	 * Open the blame cache in $HOME/.config/jgit-blame
	 *
	 * @return FileBlameCache instance
	 */
	public static FileBlameCache getDefault() {
		Path xdgCacheDirectory = SystemReader.getInstance()
				.getXdgCacheDirectory(FS.detect());
		if (xdgCacheDirectory == null) {
			throw new IllegalStateException("Unable to open XDG_CACHE_DIR");
		}

		File cacheDir = new File(xdgCacheDirectory.toFile(), "jgit-blame");
		cacheDir.mkdirs();
		return new FileBlameCache(cacheDir);
	}

	/**
	 * Cache in cacheDir.
	 *
	 * @param cacheDir
	 *            directory to read/write cache entries. It must exist. if null,
	 *            the reader/writers do nothing.
	 */
	FileBlameCache(File cacheDir) {
		if (cacheDir == null) {
			throw new IllegalStateException("cacheDir cannot be null");
		}
		if (!cacheDir.exists()) {
			throw new IllegalStateException("cacheDir must exist");
		}
		this.cacheDir = cacheDir;
	}

	@Override
	public List<CacheRegion> get(Repository repo, ObjectId commitId,
								 String path) throws IOException {
		File candidate = getCachePath(cacheDir, commitId, path);
		if (!candidate.exists()) {
			return null;
		}

		try (ObjectInputStream ois = new ObjectInputStream(
				Files.newInputStream(candidate.toPath()))) {
			@SuppressWarnings("BanSerializableRead")
			List<Entry> entries = (List<Entry>) ois.readObject();
			return entries.stream().map(Entry::asCacheRegion)
					.collect(Collectors.toUnmodifiableList());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private record Entry(String path, ObjectId oid, int start, int end)
			implements Serializable {
		CacheRegion asCacheRegion() {
			return new CacheRegion(path, oid, start, end);
		}
	}

	/**
	 * Get a writer to store the blame of the (revision, path) pair.
	 *
	 * @param revision
	 *            commit in the repo
	 * @param path
	 *            path of file in the repo
	 * @return a writer to store the blame in the cache
	 */
	public FileBlameCacheWriter getWriter(ObjectId revision, String path) {
		return new FileBlameCacheWriter(getCachePath(cacheDir, revision, path));
	}

	@Override
	public String toString() {
		return "FileBlameCache(" + cacheDir.getAbsolutePath() + ")";
	}

	private static File getCachePath(File cacheDir, ObjectId revision,
			String path) {
		SHA1 sha1 = SHA1.newInstance();
		sha1.update(path.getBytes(StandardCharsets.UTF_8));
		return new File(cacheDir,
				revision.name() + "_" + sha1.toObjectId().getName());
	}

	/**
	 * Writes to the blame cache the results of the generator
	 * <p>
	 * If the entry exists in cache, it won't overwrite it.
	 */
	public static final class FileBlameCacheWriter {
		private static final Logger LOGGER = LoggerFactory
				.getLogger(FileBlameCacheWriter.class);

		private final File cacheFile;

		private final boolean skip;

		private ArrayList<Entry> pending = new ArrayList<>();

		private FileBlameCacheWriter(File cacheFile) {
			this.cacheFile = cacheFile;
			skip = cacheFile.exists();
		}

		public void add(String path, ObjectId commit, int start, int end) {
			if (skip) {
				return;
			}
			pending.add(new Entry(path, commit, start, end));
		}

		public void flush() {
			if (skip || pending.isEmpty()) {
				return;
			}

			try {
				cacheFile.createNewFile();
			} catch (IOException e) {
				LOGGER.error("Cannot create " + cacheFile.getAbsolutePath()
						+ " " + e.getMessage());
				return;
			}
			LOGGER.info("Flushing " + pending.size() + " entries");
			try (ObjectOutputStream oos = new ObjectOutputStream(
					Files.newOutputStream(cacheFile.toPath(),
							StandardOpenOption.APPEND))) {
				oos.writeObject(pending);
				oos.flush();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// For testing
		File getOutputFile() {
			return cacheFile;
		}
	}
}
