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

import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Updates a {@link org.eclipse.jgit.dircache.DirCache} by adding individual
 * {@link org.eclipse.jgit.dircache.DirCacheEntry}s.
 * <p>
 * A builder always starts from a clean slate and appends in every single
 * <code>DirCacheEntry</code> which the final updated index must have to reflect
 * its new content.
 * <p>
 * For maximum performance applications should add entries in path name order.
 * Adding entries out of order is permitted, however a final sorting pass will
 * be implicitly performed during {@link #finish()} to correct any out-of-order
 * entries. Duplicate detection is also delayed until the sorting is complete.
 *
 * @see DirCacheEditor
 */
public class DirCacheBuilder extends BaseDirCacheEditor {
	private boolean sorted;

	/**
	 * Construct a new builder.
	 *
	 * @param dc
	 *            the cache this builder will eventually update.
	 * @param ecnt
	 *            estimated number of entries the builder will have upon
	 *            completion. This sizes the initial entry table.
	 */
	protected DirCacheBuilder(DirCache dc, int ecnt) {
		super(dc, ecnt);
	}

	/**
	 * Append one entry into the resulting entry list.
	 * <p>
	 * The entry is placed at the end of the entry list. If the entry causes the
	 * list to now be incorrectly sorted a final sorting phase will be
	 * automatically enabled within {@link #finish()}.
	 * <p>
	 * The internal entry table is automatically expanded if there is
	 * insufficient space for the new addition.
	 *
	 * @param newEntry
	 *            the new entry to add.
	 * @throws java.lang.IllegalArgumentException
	 *             If the FileMode of the entry was not set by the caller.
	 */
	public void add(DirCacheEntry newEntry) {
		if (newEntry.getRawMode() == 0)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().fileModeNotSetForPath,
					newEntry.getPathString()));
		beforeAdd(newEntry);
		fastAdd(newEntry);
	}

	/**
	 * Add a range of existing entries from the destination cache.
	 * <p>
	 * The entries are placed at the end of the entry list. If any of the
	 * entries causes the list to now be incorrectly sorted a final sorting
	 * phase will be automatically enabled within {@link #finish()}.
	 * <p>
	 * This method copies from the destination cache, which has not yet been
	 * updated with this editor's new table. So all offsets into the destination
	 * cache are not affected by any updates that may be currently taking place
	 * in this editor.
	 * <p>
	 * The internal entry table is automatically expanded if there is
	 * insufficient space for the new additions.
	 *
	 * @param pos
	 *            first entry to copy from the destination cache.
	 * @param cnt
	 *            number of entries to copy.
	 */
	public void keep(int pos, int cnt) {
		beforeAdd(cache.getEntry(pos));
		fastKeep(pos, cnt);
	}

	/**
	 * Recursively add an entire tree into this builder.
	 * <p>
	 * If pathPrefix is "a/b" and the tree contains file "c" then the resulting
	 * DirCacheEntry will have the path "a/b/c".
	 * <p>
	 * All entries are inserted at stage 0, therefore assuming that the
	 * application will not insert any other paths with the same pathPrefix.
	 *
	 * @param pathPrefix
	 *            UTF-8 encoded prefix to mount the tree's entries at. If the
	 *            path does not end with '/' one will be automatically inserted
	 *            as necessary.
	 * @param stage
	 *            stage of the entries when adding them.
	 * @param reader
	 *            reader the tree(s) will be read from during recursive
	 *            traversal. This must be the same repository that the resulting
	 *            DirCache would be written out to (or used in) otherwise the
	 *            caller is simply asking for deferred MissingObjectExceptions.
	 *            Caller is responsible for releasing this reader when done.
	 * @param tree
	 *            the tree to recursively add. This tree's contents will appear
	 *            under <code>pathPrefix</code>. The ObjectId must be that of a
	 *            tree; the caller is responsible for dereferencing a tag or
	 *            commit (if necessary).
	 * @throws java.io.IOException
	 *             a tree cannot be read to iterate through its entries.
	 */
	public void addTree(byte[] pathPrefix, int stage, ObjectReader reader,
			AnyObjectId tree) throws IOException {
		CanonicalTreeParser p = createTreeParser(pathPrefix, reader, tree);
		while (!p.eof()) {
			if (isTree(p)) {
				p = enterTree(p, reader);
				continue;
			}

			DirCacheEntry first = toEntry(stage, p);
			beforeAdd(first);
			fastAdd(first);
			p = p.next();
			break;
		}

		// Rest of tree entries are correctly sorted; use fastAdd().
		while (!p.eof()) {
			if (isTree(p)) {
				p = enterTree(p, reader);
			} else {
				fastAdd(toEntry(stage, p));
				p = p.next();
			}
		}
	}

	private static CanonicalTreeParser createTreeParser(byte[] pathPrefix,
			ObjectReader reader, AnyObjectId tree) throws IOException {
		return new CanonicalTreeParser(pathPrefix, reader, tree);
	}

	private static boolean isTree(CanonicalTreeParser p) {
		return (p.getEntryRawMode() & TYPE_MASK) == TYPE_TREE;
	}

	private static CanonicalTreeParser enterTree(CanonicalTreeParser p,
			ObjectReader reader) throws IOException {
		p = p.createSubtreeIterator(reader);
		return p.eof() ? p.next() : p;
	}

	private static DirCacheEntry toEntry(int stage, CanonicalTreeParser i) {
		byte[] buf = i.getEntryPathBuffer();
		int len = i.getEntryPathLength();
		byte[] path = new byte[len];
		System.arraycopy(buf, 0, path, 0, len);

		DirCacheEntry e = new DirCacheEntry(path, stage);
		e.setFileMode(i.getEntryRawMode());
		e.setObjectIdFromRaw(i.idBuffer(), i.idOffset());
		return e;
	}

	/** {@inheritDoc} */
	@Override
	public void finish() {
		if (!sorted)
			resort();
		replace();
	}

	private void beforeAdd(DirCacheEntry newEntry) {
		if (sorted && entryCnt > 0) {
			final DirCacheEntry lastEntry = entries[entryCnt - 1];
			final int cr = DirCache.cmp(lastEntry, newEntry);
			if (cr > 0) {
				// The new entry sorts before the old entry; we are
				// no longer sorted correctly. We'll need to redo
				// the sorting before we can close out the build.
				//
				sorted = false;
			} else if (cr == 0) {
				// Same file path; we can only insert this if the
				// stages won't be violated.
				//
				final int peStage = lastEntry.getStage();
				final int dceStage = newEntry.getStage();
				if (peStage == dceStage)
					throw bad(newEntry, JGitText.get().duplicateStagesNotAllowed);
				if (peStage == 0 || dceStage == 0)
					throw bad(newEntry, JGitText.get().mixedStagesNotAllowed);
				if (peStage > dceStage)
					sorted = false;
			}
		}
	}

	private void resort() {
		Arrays.sort(entries, 0, entryCnt, DirCache.ENT_CMP);

		for (int entryIdx = 1; entryIdx < entryCnt; entryIdx++) {
			final DirCacheEntry pe = entries[entryIdx - 1];
			final DirCacheEntry ce = entries[entryIdx];
			final int cr = DirCache.cmp(pe, ce);
			if (cr == 0) {
				// Same file path; we can only allow this if the stages
				// are 1-3 and no 0 exists.
				//
				final int peStage = pe.getStage();
				final int ceStage = ce.getStage();
				if (peStage == ceStage)
					throw bad(ce, JGitText.get().duplicateStagesNotAllowed);
				if (peStage == 0 || ceStage == 0)
					throw bad(ce, JGitText.get().mixedStagesNotAllowed);
			}
		}

		sorted = true;
	}

	private static IllegalStateException bad(DirCacheEntry a, String msg) {
		return new IllegalStateException(String.format(
				"%s: %d %s", //$NON-NLS-1$
				msg, Integer.valueOf(a.getStage()), a.getPathString()));
	}
}
