/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

package org.eclipse.jgit.subtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalkTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;

public abstract class SubtreeTestCase extends RevWalkTestCase {

	RevTree editTree(ObjectId srcTree, DirCacheEntry... entries)
			throws Exception {

		DirCache dirCache = DirCache.newInCore();
		DirCacheBuilder builder = dirCache.builder();
		builder.addTree(new byte[0], 0, rw.getObjectReader(), srcTree);
		builder.finish();

		DirCacheEditor editor = dirCache.editor();
		for (final DirCacheEntry entry : entries) {
			editor.add(new PathEdit(entry.getPathString()) {
				@Override
				public void apply(DirCacheEntry entry2) {
					try {
						entry2.setObjectId(entry.getObjectId());
						entry2.setFileMode(FileMode.REGULAR_FILE);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}

			});
		}
		editor.finish();

		ObjectInserter oi = db.newObjectInserter();
		try {
			return rw.parseTree(editor.getDirCache().writeTree(oi));
		} finally {
			oi.release();
		}
	}

	RevCommit edit(RevCommit c, boolean inPlace,
			DirCacheEntry... entries) throws Exception {
		rw.parseCommit(c);

		ObjectInserter oi = db.newObjectInserter();
		try {
			RevTree tree = editTree(c.getTree(), entries);
			if (inPlace) {
				return commit(rw.parseTree(tree), c.getParents());
			} else {
				return commit(rw.parseTree(tree), c);
			}
		} finally {
			oi.release();
		}
	}

	RevCommit subtreeAdd(String path, RevCommit superCommit,
			final RevCommit subCommit) throws IOException {

		rw.parseCommit(superCommit);
		rw.parseCommit(subCommit);

		ObjectInserter oi = db.newObjectInserter();
		try {

			// Load the super commit's tree
			DirCache dirCache = DirCache.newInCore();
			DirCacheBuilder builder = dirCache.builder();
			builder.addTree(new byte[0], 0, rw.getObjectReader(),
					superCommit.getTree());
			builder.finish();

			// Add the subcommit's tree at the specified path
			DirCacheEditor editor = dirCache.editor();
			final TreeWalk tw = new TreeWalk(db);
			tw.setRecursive(true);
			tw.addTree(subCommit.getTree());
			while (tw.next()) {
				final ObjectId subId = tw.getObjectId(0);
				final FileMode mode = tw.getFileMode(0);
				editor.add(new PathEdit(path + "/" + tw.getPathString()) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.setObjectId(subId);
						ent.setFileMode(mode);
					}
				});
			}
			editor.finish();

			// Update the .gitsubtree config file
			ObjectId tree = editor.getDirCache().writeTree(oi);
			List<SubtreeContext> contexts = new ArrayList<SubtreeContext>();
			contexts.add(new PathBasedContext(path, path));
			tree = SubtreeSplitter.updateSubtreeConfig(db, rw, contexts, oi,
					tree);

			// Build the commit
			CommitBuilder cb = new CommitBuilder();
			cb.addParentId(superCommit);
			cb.addParentId(subCommit);
			cb.setAuthor(superCommit.getAuthorIdent());
			cb.setCommitter(superCommit.getCommitterIdent());
			cb.setTreeId(tree);
			return rw.parseCommit(oi.insert(cb));

		} finally {
			oi.release();
		}
	}

}
