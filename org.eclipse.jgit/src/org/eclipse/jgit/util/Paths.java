/*
 * Copyright (C) 2016, Google Inc.
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
