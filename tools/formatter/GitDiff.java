/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.format;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Extracts new-side changed line ranges with JGit. */
final class GitDiff {
	record LineRange(int firstLine, int lineCount) {
		LineRange {
			if (firstLine < 1 || lineCount < 1) {
				throw new IllegalArgumentException(
						"A line range must contain at least one source line");
			}
		}
	}

	private GitDiff() {
	}

	static Repository openRepository(Path startingDirectory)
			throws IOException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(
				startingDirectory.toAbsolutePath().normalize().toFile());
		if (builder.getGitDir() == null) {
			throw new IOException(
					"Cannot locate Git repository: " + startingDirectory);
		}
		Repository repository = builder.setMustExist(true).build();
		if (repository.isBare()) {
			repository.close();
			throw new IOException(
					"Cannot locate Git working tree: " + startingDirectory);
		}
		return repository;
	}

	static Path repositoryRoot(Path startingDirectory) throws IOException {
		try (Repository repository = openRepository(startingDirectory)) {
			return repositoryRoot(repository);
		}
	}

	static Path repositoryRoot(Repository repository) throws IOException {
		if (repository.isBare()) {
			throw new IOException("Cannot locate Git working tree: "
					+ repository.getDirectory());
		}
		return repository.getWorkTree().toPath().toAbsolutePath().normalize();
	}

	static List<LineRange> changedRanges(Path repositoryRoot, String base,
			Path file) throws IOException {
		try (Repository repository = openRepository(repositoryRoot)) {
			return changedRanges(repository, base, file);
		}
	}

	static List<LineRange> changedRanges(Repository repository, String base,
			Path file) throws IOException {
		Path root = repositoryRoot(repository);
		Path absoluteFile = file.toAbsolutePath().normalize();
		if (!absoluteFile.startsWith(root)) {
			throw new IOException("File is outside Git repository: " + file);
		}
		String relativePath = root.relativize(absoluteFile).toString()
				.replace(File.separatorChar, '/');
		ObjectId baseTree = repository.resolve(base + "^{tree}");
		if (baseTree == null) {
			throw new IOException("Cannot resolve Git base: " + base);
		}

		try (ObjectReader reader = repository.newObjectReader();
				DiffFormatter diffFormatter = new DiffFormatter(
						DisabledOutputStream.INSTANCE)) {
			diffFormatter.setRepository(repository);
			if (repository.getConfig().getString(
					ConfigConstants.CONFIG_DIFF_SECTION, null,
					ConfigConstants.CONFIG_KEY_ALGORITHM) == null) {
				// Match native git's default; DiffFormatter defaults to
				// histogram.
				diffFormatter.setDiffAlgorithm(MyersDiff.INSTANCE);
			}
			diffFormatter.setDetectRenames(false);
			diffFormatter.setPathFilter(PathFilter.create(relativePath));
			CanonicalTreeParser baseIterator = new CanonicalTreeParser();
			baseIterator.reset(reader, baseTree);
			List<DiffEntry> entries = diffFormatter.scan(baseIterator,
					new ExplicitFileTreeIterator(repository));

			List<LineRange> ranges = new ArrayList<>();
			for (DiffEntry entry : entries) {
				EditList edits = diffFormatter.toFileHeader(entry).toEditList();
				for (Edit edit : edits) {
					int lineCount = edit.getEndB() - edit.getBeginB();
					if (lineCount > 0) {
						ranges.add(
								new LineRange(edit.getBeginB() + 1, lineCount));
					}
				}
			}
			return mergeAdjacent(ranges);
		}
	}

	private static List<LineRange> mergeAdjacent(List<LineRange> ranges) {
		if (ranges.size() < 2) {
			return List.copyOf(ranges);
		}
		List<LineRange> merged = new ArrayList<>();
		for (LineRange current : ranges) {
			if (merged.isEmpty()) {
				merged.add(current);
				continue;
			}
			int lastIndex = merged.size() - 1;
			LineRange previous = merged.get(lastIndex);
			int previousEnd = previous.firstLine() + previous.lineCount();
			if (current.firstLine() <= previousEnd) {
				int currentEnd = current.firstLine() + current.lineCount();
				merged.set(lastIndex,
						new LineRange(previous.firstLine(),
								Math.max(previousEnd, currentEnd)
										- previous.firstLine()));
			} else {
				merged.add(current);
			}
		}
		return List.copyOf(merged);
	}

	/**
	 * Includes the explicitly requested path even when an ignore rule matches.
	 */
	private static final class ExplicitFileTreeIterator
			extends FileTreeIterator {
		ExplicitFileTreeIterator(Repository repository) {
			super(repository);
		}

		private ExplicitFileTreeIterator(WorkingTreeIterator parent,
				File directory, FS fs, FileModeStrategy fileModeStrategy) {
			super(parent, directory, fs, fileModeStrategy);
		}

		@Override
		public boolean isEntryIgnored() {
			return false;
		}

		@Override
		protected AbstractTreeIterator enterSubtree() {
			return new ExplicitFileTreeIterator(this, getEntryFile(), fs,
					fileModeStrategy);
		}
	}
}
