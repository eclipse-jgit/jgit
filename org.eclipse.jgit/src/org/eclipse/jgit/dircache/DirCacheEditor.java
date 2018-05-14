/*
 * Copyright (C) 2008-2009, Google Inc.
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

import static org.eclipse.jgit.dircache.DirCache.cmp;
import static org.eclipse.jgit.dircache.DirCacheTree.peq;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.Paths;

/**
 * Updates a {@link org.eclipse.jgit.dircache.DirCache} by supplying discrete
 * edit commands.
 * <p>
 * An editor updates a DirCache by taking a list of
 * {@link org.eclipse.jgit.dircache.DirCacheEditor.PathEdit} commands and
 * executing them against the entries of the destination cache to produce a new
 * cache. This edit style allows applications to insert a few commands and then
 * have the editor compute the proper entry indexes necessary to perform an
 * efficient in-order update of the index records. This can be easier to use
 * than {@link org.eclipse.jgit.dircache.DirCacheBuilder}.
 * <p>
 *
 * @see DirCacheBuilder
 */
public class DirCacheEditor extends BaseDirCacheEditor {
	private static final Comparator<PathEdit> EDIT_CMP = new Comparator<PathEdit>() {
		@Override
		public int compare(PathEdit o1, PathEdit o2) {
			final byte[] a = o1.path;
			final byte[] b = o2.path;
			return cmp(a, a.length, b, b.length);
		}
	};

	private final List<PathEdit> edits;
	private int editIdx;

	/**
	 * Construct a new editor.
	 *
	 * @param dc
	 *            the cache this editor will eventually update.
	 * @param ecnt
	 *            estimated number of entries the editor will have upon
	 *            completion. This sizes the initial entry table.
	 */
	protected DirCacheEditor(DirCache dc, int ecnt) {
		super(dc, ecnt);
		edits = new ArrayList<>();
	}

	/**
	 * Append one edit command to the list of commands to be applied.
	 * <p>
	 * Edit commands may be added in any order chosen by the application. They
	 * are automatically rearranged by the builder to provide the most efficient
	 * update possible.
	 *
	 * @param edit
	 *            another edit command.
	 */
	public void add(PathEdit edit) {
		edits.add(edit);
	}

	/** {@inheritDoc} */
	@Override
	public boolean commit() throws IOException {
		if (edits.isEmpty()) {
			// No changes? Don't rewrite the index.
			//
			cache.unlock();
			return true;
		}
		return super.commit();
	}

	/** {@inheritDoc} */
	@Override
	public void finish() {
		if (!edits.isEmpty()) {
			applyEdits();
			replace();
		}
	}

	private void applyEdits() {
		Collections.sort(edits, EDIT_CMP);
		editIdx = 0;

		final int maxIdx = cache.getEntryCount();
		int lastIdx = 0;
		while (editIdx < edits.size()) {
			PathEdit e = edits.get(editIdx++);
			int eIdx = cache.findEntry(lastIdx, e.path, e.path.length);
			final boolean missing = eIdx < 0;
			if (eIdx < 0)
				eIdx = -(eIdx + 1);
			final int cnt = Math.min(eIdx, maxIdx) - lastIdx;
			if (cnt > 0)
				fastKeep(lastIdx, cnt);

			if (e instanceof DeletePath) {
				lastIdx = missing ? eIdx : cache.nextEntry(eIdx);
				continue;
			}
			if (e instanceof DeleteTree) {
				lastIdx = cache.nextEntry(e.path, e.path.length, eIdx);
				continue;
			}

			if (missing) {
				DirCacheEntry ent = new DirCacheEntry(e.path);
				e.apply(ent);
				if (ent.getRawMode() == 0) {
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().fileModeNotSetForPath,
							ent.getPathString()));
				}
				lastIdx = e.replace
					? deleteOverlappingSubtree(ent, eIdx)
					: eIdx;
				fastAdd(ent);
			} else {
				// Apply to all entries of the current path (different stages)
				lastIdx = cache.nextEntry(eIdx);
				for (int i = eIdx; i < lastIdx; i++) {
					final DirCacheEntry ent = cache.getEntry(i);
					e.apply(ent);
					fastAdd(ent);
				}
			}
		}

		final int cnt = maxIdx - lastIdx;
		if (cnt > 0)
			fastKeep(lastIdx, cnt);
	}

	private int deleteOverlappingSubtree(DirCacheEntry ent, int eIdx) {
		byte[] entPath = ent.path;
		int entLen = entPath.length;

		// Delete any file that was previously processed and overlaps
		// the parent directory for the new entry. Since the editor
		// always processes entries in path order, binary search back
		// for the overlap for each parent directory.
		for (int p = pdir(entPath, entLen); p > 0; p = pdir(entPath, p)) {
			int i = findEntry(entPath, p);
			if (i >= 0) {
				// A file does overlap, delete the file from the array.
				// No other parents can have overlaps as the file should
				// have taken care of that itself.
				int n = --entryCnt - i;
				System.arraycopy(entries, i + 1, entries, i, n);
				break;
			}

			// If at least one other entry already exists in this parent
			// directory there is no need to continue searching up the tree.
			i = -(i + 1);
			if (i < entryCnt && inDir(entries[i], entPath, p)) {
				break;
			}
		}

		int maxEnt = cache.getEntryCount();
		if (eIdx >= maxEnt) {
			return maxEnt;
		}

		DirCacheEntry next = cache.getEntry(eIdx);
		if (Paths.compare(next.path, 0, next.path.length, 0,
				entPath, 0, entLen, TYPE_TREE) < 0) {
			// Next DirCacheEntry sorts before new entry as tree. Defer a
			// DeleteTree command to delete any entries if they exist. This
			// case only happens for A, A.c, A/c type of conflicts (rare).
			insertEdit(new DeleteTree(entPath));
			return eIdx;
		}

		// Next entry may be contained by the entry-as-tree, skip if so.
		while (eIdx < maxEnt && inDir(cache.getEntry(eIdx), entPath, entLen)) {
			eIdx++;
		}
		return eIdx;
	}

	private int findEntry(byte[] p, int pLen) {
		int low = 0;
		int high = entryCnt;
		while (low < high) {
			int mid = (low + high) >>> 1;
			int cmp = cmp(p, pLen, entries[mid]);
			if (cmp < 0) {
				high = mid;
			} else if (cmp == 0) {
				while (mid > 0 && cmp(p, pLen, entries[mid - 1]) == 0) {
					mid--;
				}
				return mid;
			} else {
				low = mid + 1;
			}
		}
		return -(low + 1);
	}

	private void insertEdit(DeleteTree d) {
		for (int i = editIdx; i < edits.size(); i++) {
			int cmp = EDIT_CMP.compare(d, edits.get(i));
			if (cmp < 0) {
				edits.add(i, d);
				return;
			} else if (cmp == 0) {
				return;
			}
		}
		edits.add(d);
	}

	private static boolean inDir(DirCacheEntry e, byte[] path, int pLen) {
		return e.path.length > pLen && e.path[pLen] == '/'
				&& peq(path, e.path, pLen);
	}

	private static int pdir(byte[] path, int e) {
		for (e--; e > 0; e--) {
			if (path[e] == '/') {
				return e;
			}
		}
		return 0;
	}

	/**
	 * Any index record update.
	 * <p>
	 * Applications should subclass and provide their own implementation for the
	 * {@link #apply(DirCacheEntry)} method. The editor will invoke apply once
	 * for each record in the index which matches the path name. If there are
	 * multiple records (for example in stages 1, 2 and 3), the edit instance
	 * will be called multiple times, once for each stage.
	 */
	public abstract static class PathEdit {
		final byte[] path;
		boolean replace = true;

		/**
		 * Create a new update command by path name.
		 *
		 * @param entryPath
		 *            path of the file within the repository.
		 */
		public PathEdit(String entryPath) {
			path = Constants.encode(entryPath);
		}

		PathEdit(byte[] path) {
			this.path = path;
		}

		/**
		 * Create a new update command for an existing entry instance.
		 *
		 * @param ent
		 *            entry instance to match path of. Only the path of this
		 *            entry is actually considered during command evaluation.
		 */
		public PathEdit(DirCacheEntry ent) {
			path = ent.path;
		}

		/**
		 * Configure if a file can replace a directory (or vice versa).
		 * <p>
		 * Default is {@code true} as this is usually the desired behavior.
		 *
		 * @param ok
		 *            if true a file can replace a directory, or a directory can
		 *            replace a file.
		 * @return {@code this}
		 * @since 4.2
		 */
		public PathEdit setReplace(boolean ok) {
			replace = ok;
			return this;
		}

		/**
		 * Apply the update to a single cache entry matching the path.
		 * <p>
		 * After apply is invoked the entry is added to the output table, and
		 * will be included in the new index.
		 *
		 * @param ent
		 *            the entry being processed. All fields are zeroed out if
		 *            the path is a new path in the index.
		 */
		public abstract void apply(DirCacheEntry ent);

		@Override
		public String toString() {
			String p = DirCacheEntry.toString(path);
			return getClass().getSimpleName() + '[' + p + ']';
		}
	}

	/**
	 * Deletes a single file entry from the index.
	 * <p>
	 * This deletion command removes only a single file at the given location,
	 * but removes multiple stages (if present) for that path. To remove a
	 * complete subtree use {@link DeleteTree} instead.
	 *
	 * @see DeleteTree
	 */
	public static final class DeletePath extends PathEdit {
		/**
		 * Create a new deletion command by path name.
		 *
		 * @param entryPath
		 *            path of the file within the repository.
		 */
		public DeletePath(String entryPath) {
			super(entryPath);
		}

		/**
		 * Create a new deletion command for an existing entry instance.
		 *
		 * @param ent
		 *            entry instance to remove. Only the path of this entry is
		 *            actually considered during command evaluation.
		 */
		public DeletePath(DirCacheEntry ent) {
			super(ent);
		}

		@Override
		public void apply(DirCacheEntry ent) {
			throw new UnsupportedOperationException(JGitText.get().noApplyInDelete);
		}
	}

	/**
	 * Recursively deletes all paths under a subtree.
	 * <p>
	 * This deletion command is more generic than {@link DeletePath} as it can
	 * remove all records which appear recursively under the same subtree.
	 * Multiple stages are removed (if present) for any deleted entry.
	 * <p>
	 * This command will not remove a single file entry. To remove a single file
	 * use {@link DeletePath}.
	 *
	 * @see DeletePath
	 */
	public static final class DeleteTree extends PathEdit {
		/**
		 * Create a new tree deletion command by path name.
		 *
		 * @param entryPath
		 *            path of the subtree within the repository. If the path
		 *            does not end with "/" a "/" is implicitly added to ensure
		 *            only the subtree's contents are matched by the command.
		 *            The special case "" (not "/"!) deletes all entries.
		 */
		public DeleteTree(String entryPath) {
			super(entryPath.isEmpty()
					|| entryPath.charAt(entryPath.length() - 1) == '/'
					? entryPath
					: entryPath + '/');
		}

		DeleteTree(byte[] path) {
			super(appendSlash(path));
		}

		private static byte[] appendSlash(byte[] path) {
			int n = path.length;
			if (n > 0 && path[n - 1] != '/') {
				byte[] r = new byte[n + 1];
				System.arraycopy(path, 0, r, 0, n);
				r[n] = '/';
				return r;
			}
			return path;
		}

		@Override
		public void apply(DirCacheEntry ent) {
			throw new UnsupportedOperationException(JGitText.get().noApplyInDelete);
		}
	}
}
