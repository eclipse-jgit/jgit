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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;

/**
 * Updates a {@link DirCache} by supplying discrete edit commands.
 * <p>
 * An editor updates a DirCache by taking a list of {@link PathEdit} commands
 * and executing them against the entries of the destination cache to produce a
 * new cache. This edit style allows applications to insert a few commands and
 * then have the editor compute the proper entry indexes necessary to perform an
 * efficient in-order update of the index records. This can be easier to use
 * than {@link DirCacheBuilder}.
 * <p>
 *
 * @see DirCacheBuilder
 */
public class DirCacheEditor extends BaseDirCacheEditor {
	private static final Comparator<PathEdit> EDIT_CMP = new Comparator<PathEdit>() {
		public int compare(final PathEdit o1, final PathEdit o2) {
			final byte[] a = o1.path;
			final byte[] b = o2.path;
			return DirCache.cmp(a, a.length, b, b.length);
		}
	};

	private final List<PathEdit> edits;

	/**
	 * Construct a new editor.
	 *
	 * @param dc
	 *            the cache this editor will eventually update.
	 * @param ecnt
	 *            estimated number of entries the editor will have upon
	 *            completion. This sizes the initial entry table.
	 */
	protected DirCacheEditor(final DirCache dc, final int ecnt) {
		super(dc, ecnt);
		edits = new ArrayList<PathEdit>();
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
	public void add(final PathEdit edit) {
		edits.add(edit);
	}

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

	public void finish() {
		if (!edits.isEmpty()) {
			applyEdits();
			replace();
		}
	}

	private void applyEdits() {
		Collections.sort(edits, EDIT_CMP);

		final int maxIdx = cache.getEntryCount();
		int lastIdx = 0;
		for (final PathEdit e : edits) {
			int eIdx = cache.findEntry(e.path, e.path.length);
			final boolean missing = eIdx < 0;
			if (eIdx < 0)
				eIdx = -(eIdx + 1);
			final int cnt = Math.min(eIdx, maxIdx) - lastIdx;
			if (cnt > 0)
				fastKeep(lastIdx, cnt);
			lastIdx = missing ? eIdx : cache.nextEntry(eIdx);

			if (e instanceof DeletePath)
				continue;
			if (e instanceof DeleteTree) {
				lastIdx = cache.nextEntry(e.path, e.path.length, eIdx);
				continue;
			}

			if (missing) {
				final DirCacheEntry ent = new DirCacheEntry(e.path);
				e.apply(ent);
				if (ent.getRawMode() == 0)
					throw new IllegalArgumentException(MessageFormat.format(JGitText.get().fileModeNotSetForPath
							, ent.getPathString()));
				fastAdd(ent);
			} else {
				// Apply to all entries of the current path (different stages)
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

		/**
		 * Create a new update command by path name.
		 *
		 * @param entryPath
		 *            path of the file within the repository.
		 */
		public PathEdit(final String entryPath) {
			path = Constants.encode(entryPath);
		}

		/**
		 * Create a new update command for an existing entry instance.
		 *
		 * @param ent
		 *            entry instance to match path of. Only the path of this
		 *            entry is actually considered during command evaluation.
		 */
		public PathEdit(final DirCacheEntry ent) {
			path = ent.path;
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
		public DeletePath(final String entryPath) {
			super(entryPath);
		}

		/**
		 * Create a new deletion command for an existing entry instance.
		 *
		 * @param ent
		 *            entry instance to remove. Only the path of this entry is
		 *            actually considered during command evaluation.
		 */
		public DeletePath(final DirCacheEntry ent) {
			super(ent);
		}

		public void apply(final DirCacheEntry ent) {
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
		public DeleteTree(final String entryPath) {
			super(
					(entryPath.endsWith("/") || entryPath.length() == 0) ? entryPath //$NON-NLS-1$
							: entryPath + "/"); //$NON-NLS-1$
		}

		public void apply(final DirCacheEntry ent) {
			throw new UnsupportedOperationException(JGitText.get().noApplyInDelete);
		}
	}
}
