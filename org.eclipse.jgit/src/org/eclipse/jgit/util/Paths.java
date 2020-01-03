/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

/**
 * Utility functions for paths inside of a Git repository.
 *
 * @since 4.2
 */
public class Paths {
	/**
	 * Remove trailing {@code '/'} if present.
	 *
	 * @param path
	 *            input path to potentially remove trailing {@code '/'} from.
	 * @return null if {@code path == null}; {@code path} after removing a
	 *         trailing {@code '/'}.
	 */
	public static String stripTrailingSeparator(String path) {
		if (path == null || path.isEmpty()) {
			return path;
		}

		int i = path.length();
		if (path.charAt(path.length() - 1) != '/') {
			return path;
		}
		do {
			i--;
		} while (path.charAt(i - 1) == '/');
		return path.substring(0, i);
	}

	/**
	 * Compare two paths according to Git path sort ordering rules.
	 *
	 * @param aPath
	 *            first path buffer. The range {@code [aPos, aEnd)} is used.
	 * @param aPos
	 *            index into {@code aPath} where the first path starts.
	 * @param aEnd
	 *            1 past last index of {@code aPath}.
	 * @param aMode
	 *            mode of the first file. Trees are sorted as though
	 *            {@code aPath[aEnd] == '/'}, even if aEnd does not exist.
	 * @param bPath
	 *            second path buffer. The range {@code [bPos, bEnd)} is used.
	 * @param bPos
	 *            index into {@code bPath} where the second path starts.
	 * @param bEnd
	 *            1 past last index of {@code bPath}.
	 * @param bMode
	 *            mode of the second file. Trees are sorted as though
	 *            {@code bPath[bEnd] == '/'}, even if bEnd does not exist.
	 * @return &lt;0 if {@code aPath} sorts before {@code bPath};
	 *         0 if the paths are the same;
	 *         &gt;0 if {@code aPath} sorts after {@code bPath}.
	 */
	public static int compare(byte[] aPath, int aPos, int aEnd, int aMode,
			byte[] bPath, int bPos, int bEnd, int bMode) {
		int cmp = coreCompare(
				aPath, aPos, aEnd, aMode,
				bPath, bPos, bEnd, bMode);
		if (cmp == 0) {
			cmp = lastPathChar(aMode) - lastPathChar(bMode);
		}
		return cmp;
	}

	/**
	 * Compare two paths, checking for identical name.
	 * <p>
	 * Unlike {@code compare} this method returns {@code 0} when the paths have
	 * the same characters in their names, even if the mode differs. It is
	 * intended for use in validation routines detecting duplicate entries.
	 * <p>
	 * Returns {@code 0} if the names are identical and a conflict exists
	 * between {@code aPath} and {@code bPath}, as they share the same name.
	 * <p>
	 * Returns {@code <0} if all possibles occurrences of {@code aPath} sort
	 * before {@code bPath} and no conflict can happen. In a properly sorted
	 * tree there are no other occurrences of {@code aPath} and therefore there
	 * are no duplicate names.
	 * <p>
	 * Returns {@code >0} when it is possible for a duplicate occurrence of
	 * {@code aPath} to appear later, after {@code bPath}. Callers should
	 * continue to examine candidates for {@code bPath} until the method returns
	 * one of the other return values.
	 *
	 * @param aPath
	 *            first path buffer. The range {@code [aPos, aEnd)} is used.
	 * @param aPos
	 *            index into {@code aPath} where the first path starts.
	 * @param aEnd
	 *            1 past last index of {@code aPath}.
	 * @param bPath
	 *            second path buffer. The range {@code [bPos, bEnd)} is used.
	 * @param bPos
	 *            index into {@code bPath} where the second path starts.
	 * @param bEnd
	 *            1 past last index of {@code bPath}.
	 * @param bMode
	 *            mode of the second file. Trees are sorted as though
	 *            {@code bPath[bEnd] == '/'}, even if bEnd does not exist.
	 * @return &lt;0 if no duplicate name could exist;
	 *         0 if the paths have the same name;
	 *         &gt;0 other {@code bPath} should still be checked by caller.
	 */
	public static int compareSameName(
			byte[] aPath, int aPos, int aEnd,
			byte[] bPath, int bPos, int bEnd, int bMode) {
		return coreCompare(
				aPath, aPos, aEnd, TYPE_TREE,
				bPath, bPos, bEnd, bMode);
	}

	private static int coreCompare(
			byte[] aPath, int aPos, int aEnd, int aMode,
			byte[] bPath, int bPos, int bEnd, int bMode) {
		while (aPos < aEnd && bPos < bEnd) {
			int cmp = (aPath[aPos++] & 0xff) - (bPath[bPos++] & 0xff);
			if (cmp != 0) {
				return cmp;
			}
		}
		if (aPos < aEnd) {
			return (aPath[aPos] & 0xff) - lastPathChar(bMode);
		}
		if (bPos < bEnd) {
			return lastPathChar(aMode) - (bPath[bPos] & 0xff);
		}
		return 0;
	}

	private static int lastPathChar(int mode) {
		if ((mode & TYPE_MASK) == TYPE_TREE) {
			return '/';
		}
		return 0;
	}

	private Paths() {
	}
}
