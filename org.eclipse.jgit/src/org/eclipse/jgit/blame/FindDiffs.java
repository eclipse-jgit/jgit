/*
 * Copyright (C) 2022 Simeon Andreev and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.treewalk.filter.PathFilter;

class FindDiffs {

	/**
	 * Computes a subset of the specified diffs, that can be used to try to
	 * quickly find a rename diff:
	 * <ul>
	 * <li>diff that adds {@code path}</li>
	 * <li>diffs that remove a file with the same file name as {@code path}</li>
	 * <li>diffs that remove a file in the same folder as {@code path}</li>
	 * <ul>
	 * </p>
	 *
	 * @param path
	 *            the path of an added file
	 * @param entries
	 *            diffs in which to find similar renames
	 * @return a subset of diffs that can be used to quickly try to find a
	 *         rename diff
	 */
	static List<DiffEntry> sameFileNameOrFolderDiffs(PathFilter path,
			List<DiffEntry> entries) {
		List<DiffEntry> prunedEntries = new ArrayList<>();
		boolean sameFileAdded = false;
		boolean similarFilesRemoved = false;
		Path blamePath = Paths.get(path.getPath());
		String blameFileName = blamePath.getFileName().toString();
		for (DiffEntry entry : entries) {
			if (entry.getChangeType() == ChangeType.DELETE && sameFileNameOrFolder(
					blamePath, blameFileName, entry.getOldPath())) {
				similarFilesRemoved = true;
				prunedEntries.add(entry);
			} else if (entry.getChangeType() == ChangeType.ADD
					&& entry.getNewPath().equals(path.getPath())) {
				sameFileAdded = true;
				prunedEntries.add(entry);
			}
		}
		if (sameFileAdded && similarFilesRemoved) {
			return prunedEntries;
		}
		return Collections.emptyList();
	}

	private static boolean sameFileNameOrFolder(Path path1, String path1FileName,
			String path2) {
		Path p2 = Paths.get(path2);
		String path2FileName = p2.getFileName().toString();
		boolean sameFileOrFolder = path1FileName.equals(path2FileName)
				|| sameFolders(path1, p2);
		return sameFileOrFolder;
	}

	private static boolean sameFolders(Path path1, Path path2) {
		if (path1.getNameCount() >= 2 && path2.getNameCount() >= 2) {
			Path directory1 = path1.subpath(0, path1.getNameCount() - 2);
			Path directory2 = path2.subpath(0, path2.getNameCount() - 2);
			if (directory1.equals(directory2)) {
				return true;
			}
		}
		return false;
	}
}
