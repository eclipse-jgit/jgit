/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.dircache;

import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;
import static org.eclipse.jgit.util.Paths.compareSameName;

import java.io.IOException;

import org.eclipse.jgit.errors.DirCacheNameConflictException;

/**
 * Generic update/editing support for {@link DirCache}.
 * <p>
 * The different update strategies extend this class to provide their own unique
 * services to applications.
 */
abstract class BaseDirCacheEditor {
	/** The cache instance this editor updates during {@link #finish()}. */
	protected DirCache cache;

	/**
	 * Entry table this builder will eventually replace into {@link #cache}.
	 * <p>
	 * Use {@link #fastAdd(DirCacheEntry)} or {@link #fastKeep(int, int)} to
	 * make additions to this table. The table is automatically expanded if it
	 * is too small for a new addition.
	 * <p>
	 * Typically the entries in here are sorted by their path names, just like
	 * they are in the DirCache instance.
	 */
	protected DirCacheEntry[] entries;

	/** Total number of valid entries in {@link #entries}. */
	protected int entryCnt;

	/**
	 * Construct a new editor.
	 *
	 * @param dc
	 *            the cache this editor will eventually update.
	 * @param ecnt
	 *            estimated number of entries the editor will have upon
	 *            completion. This sizes the initial entry table.
	 */
	protected BaseDirCacheEditor(final DirCache dc, final int ecnt) {
		cache = dc;
		entries = new DirCacheEntry[ecnt];
	}

	/**
	 * Get the {@code DirCache}
	 *
	 * @return the cache we will update on {@link #finish()}.
	 */
	public DirCache getDirCache() {
		return cache;
	}

	/**
	 * Append one entry into the resulting entry list.
	 * <p>
	 * The entry is placed at the end of the entry list. The caller is
	 * responsible for making sure the final table is correctly sorted.
	 * <p>
	 * The {@link #entries} table is automatically expanded if there is
	 * insufficient space for the new addition.
	 *
	 * @param newEntry
	 *            the new entry to add.
	 */
	protected void fastAdd(final DirCacheEntry newEntry) {
		if (entries.length == entryCnt) {
			final DirCacheEntry[] n = new DirCacheEntry[(entryCnt + 16) * 3 / 2];
			System.arraycopy(entries, 0, n, 0, entryCnt);
			entries = n;
		}
		entries[entryCnt++] = newEntry;
	}

	/**
	 * Add a range of existing entries from the destination cache.
	 * <p>
	 * The entries are placed at the end of the entry list, preserving their
	 * current order. The caller is responsible for making sure the final table
	 * is correctly sorted.
	 * <p>
	 * This method copies from the destination cache, which has not yet been
	 * updated with this editor's new table. So all offsets into the destination
	 * cache are not affected by any updates that may be currently taking place
	 * in this editor.
	 * <p>
	 * The {@link #entries} table is automatically expanded if there is
	 * insufficient space for the new additions.
	 *
	 * @param pos
	 *            first entry to copy from the destination cache.
	 * @param cnt
	 *            number of entries to copy.
	 */
	protected void fastKeep(final int pos, int cnt) {
		if (entryCnt + cnt > entries.length) {
			final int m1 = (entryCnt + 16) * 3 / 2;
			final int m2 = entryCnt + cnt;
			final DirCacheEntry[] n = new DirCacheEntry[Math.max(m1, m2)];
			System.arraycopy(entries, 0, n, 0, entryCnt);
			entries = n;
		}

		cache.toArray(pos, entries, entryCnt, cnt);
		entryCnt += cnt;
	}

	/**
	 * Finish this builder and update the destination
	 * {@link org.eclipse.jgit.dircache.DirCache}.
	 * <p>
	 * When this method completes this builder instance is no longer usable by
	 * the calling application. A new builder must be created to make additional
	 * changes to the index entries.
	 * <p>
	 * After completion the DirCache returned by {@link #getDirCache()} will
	 * contain all modifications.
	 * <p>
	 * <i>Note to implementors:</i> Make sure {@link #entries} is fully sorted
	 * then invoke {@link #replace()} to update the DirCache with the new table.
	 */
	public abstract void finish();

	/**
	 * Update the DirCache with the contents of {@link #entries}.
	 * <p>
	 * This method should be invoked only during an implementation of
	 * {@link #finish()}, and only after {@link #entries} is sorted.
	 */
	protected void replace() {
		checkNameConflicts();
		if (entryCnt < entries.length / 2) {
			final DirCacheEntry[] n = new DirCacheEntry[entryCnt];
			System.arraycopy(entries, 0, n, 0, entryCnt);
			entries = n;
		}
		cache.replace(entries, entryCnt);
	}

	private void checkNameConflicts() {
		int end = entryCnt - 1;
		for (int eIdx = 0; eIdx < end; eIdx++) {
			DirCacheEntry e = entries[eIdx];
			if (e.getStage() != 0) {
				continue;
			}

			byte[] ePath = e.path;
			int prefixLen = lastSlash(ePath) + 1;

			for (int nIdx = eIdx + 1; nIdx < entryCnt; nIdx++) {
				DirCacheEntry n = entries[nIdx];
				if (n.getStage() != 0) {
					continue;
				}

				byte[] nPath = n.path;
				if (!startsWith(ePath, nPath, prefixLen)) {
					// Different prefix; this entry is in another directory.
					break;
				}

				int s = nextSlash(nPath, prefixLen);
				int m = s < nPath.length ? TYPE_TREE : n.getRawMode();
				int cmp = compareSameName(
						ePath, prefixLen, ePath.length,
						nPath, prefixLen, s, m);
				if (cmp < 0) {
					break;
				} else if (cmp == 0) {
					throw new DirCacheNameConflictException(
							e.getPathString(),
							n.getPathString());
				}
			}
		}
	}

	private static int lastSlash(byte[] path) {
		for (int i = path.length - 1; i >= 0; i--) {
			if (path[i] == '/') {
				return i;
			}
		}
		return -1;
	}

	private static int nextSlash(byte[] b, int p) {
		final int n = b.length;
		for (; p < n; p++) {
			if (b[p] == '/') {
				return p;
			}
		}
		return n;
	}

	private static boolean startsWith(byte[] a, byte[] b, int n) {
		if (b.length < n) {
			return false;
		}
		for (n--; n >= 0; n--) {
			if (a[n] != b[n]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Finish, write, commit this change, and release the index lock.
	 * <p>
	 * If this method fails (returns false) the lock is still released.
	 * <p>
	 * This is a utility method for applications as the finish-write-commit
	 * pattern is very common after using a builder to update entries.
	 *
	 * @return true if the commit was successful and the file contains the new
	 *         data; false if the commit failed and the file remains with the
	 *         old data.
	 * @throws java.lang.IllegalStateException
	 *             the lock is not held.
	 * @throws java.io.IOException
	 *             the output file could not be created. The caller no longer
	 *             holds the lock.
	 */
	public boolean commit() throws IOException {
		finish();
		cache.write();
		return cache.commit();
	}
}
