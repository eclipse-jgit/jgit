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

import static java.util.function.Predicate.not;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Blame cache using the file system as storage
 * <p>
 * This is mostly to test the caching Blame command, not intended for serious
 * use.
 */
public class FileBlameCache {
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
			return FileBlameCache.NULL;
		}

		File cacheDir = new File(xdgCacheDirectory.toFile(), "jgit-blame");
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
		return new FileBlameCache(cacheDir);
	}

	/**
	 * Do nothing instance
	 */
	private static FileBlameCache NULL = new FileBlameCache(null) {
		@Override
		public BlameCache getReader() {
			return null;
		}

		@Override
		public FileBlameCacheWriter getWriter(ObjectId r, String p) {
			return null;
		}
	};

	/**
	 * Cache in cacheDir.
	 *
	 * @param cacheDir
	 *            directory to read/write cache entries. It must exist.
	 */
	FileBlameCache(File cacheDir) {
		this.cacheDir = cacheDir;
	}

	/**
	 * Get a writer to store the blame of the (revision, path) pair.
	 *
	 * @param revision commit in the repo
	 * @param path path of file in the repo
	 * @return a writer to store the blame in the cache
	 */
	@Nullable
	public FileBlameCacheWriter getWriter(ObjectId revision, String path) {
		return new FileBlameCacheWriter(getCachePath(cacheDir, revision, path));
	}

	/**
	 * Get a blame cache reader
	 *
	 * @return a blame cache reader for the BlameGenerator.
	 */
	@Nullable
	public BlameCache getReader() {
		return new FileBlameCacheReader(cacheDir);
	}

	@Override
	public String toString() {
		return "FileBlameCache(" + cacheDir != null ? cacheDir.getAbsolutePath()
				: "null" + ")";
	}

	private static File getCachePath(File cacheDir, ObjectId revision,
			String path) {
		return new File(cacheDir,
				revision.name() + ":" + path.replaceAll("/", "@SLASH@"));
	}

	/**
	 * Writes to the blame cache the results of the generator
	 * <p>
	 * If the entry exists in cache, it won't overwrite it.
	 */
	public static class FileBlameCacheWriter {
		private final File cacheFile;

		private final boolean skip;

		private ArrayList<Entry> pending = new ArrayList<>(100);

		private FileBlameCacheWriter(File cacheFile) {
			this.cacheFile = cacheFile;
			skip = cacheFile.exists();
		}

		public void add(String path, ObjectId commit, int start, int end) {
			if (skip) {
				return;
			}
			if (pending.size() > 99) {
				flush();
			}
			pending.add(new Entry(path, commit, start, end));
		}

		public void flush() {
			if (skip) {
				return;
			}
			try (FileWriter writer = new FileWriter(cacheFile, true)) {
				pending.forEach(e -> {
					try {
						writer.write(e.path() + " " + e.oid.name() + " "
								+ e.start + " " + e.end + "\n");
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				});
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			pending = new ArrayList<>(100);
		}

		private record Entry(String path, ObjectId oid, int start, int end) {
		}
	}

	private static final class FileBlameCacheReader implements BlameCache {
		private final File cacheDir;

		FileBlameCacheReader(File cacheDir) {
			this.cacheDir = cacheDir;
		}

		@Override
		public List<CacheRegion> get(Repository repo, ObjectId commitId,
				String path) throws IOException {
			File candidate = getCachePath(cacheDir, commitId, path);
			if (!candidate.exists()) {
				return null;
			}

			try (BufferedReader reader = Files.newBufferedReader(
					candidate.toPath(), StandardCharsets.UTF_8)) {
				return reader.lines().filter(not(String::isEmpty))
						.map(FileBlameCacheReader::lineToCacheRegion)
						.collect(Collectors.toUnmodifiableList());
			}
		}

		private static CacheRegion lineToCacheRegion(String line) {
			String[] parts = line.split(" ", -1);
			if (parts.length != 4) {
				throw new IllegalStateException("Invalid cache line: " + line);
			}
			String path = parts[0];
			ObjectId commit = ObjectId.fromString(parts[1]);
			int start = Integer.parseInt(parts[2]);
			int end = Integer.parseInt(parts[3]);
			return new CacheRegion(path, commit, start, end);
		}
	}
}
