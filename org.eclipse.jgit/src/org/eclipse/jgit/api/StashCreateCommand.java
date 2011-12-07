/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * Command class to stash changes in the working directory and index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 */
public class StashCreateCommand extends GitCommand<RevCommit> {

	/**
	 * Create a command to stash changes
	 *
	 * @param repo
	 */
	public StashCreateCommand(Repository repo) {
		super(repo);
	}

	private String getHeadCommitMessage(ObjectId headId) throws IOException {
		RevWalk walk = new RevWalk(repo);
		try {
			return headId.abbreviate(7).name()
					+ walk.parseCommit(headId).getShortMessage();
		} finally {
			walk.release();
		}
	}

	private CommitBuilder createBuilder(ObjectId headId) {
		CommitBuilder builder = new CommitBuilder();
		PersonIdent author = new PersonIdent(repo);
		builder.setAuthor(author);
		builder.setCommitter(author);
		builder.setParentId(headId);
		return builder;
	}

	private void updateStashRef(ObjectId commitId) throws IOException {
		Ref currentStash = repo.getRef(Constants.R_STASH);
		RefUpdate stashUpdate = repo.updateRef(Constants.R_STASH);
		stashUpdate.setNewObjectId(commitId);
		if (currentStash != null)
			stashUpdate.setExpectedOldObjectId(currentStash.getObjectId());
		else
			stashUpdate.setExpectedOldObjectId(ObjectId.zeroId());
		stashUpdate.forceUpdate();
	}

	private void addToIndex(DirCache index, Set<String> modified,
			Set<String> missing, WorkingTreeIterator iter,
			ObjectInserter inserter) throws IOException {
		DirCacheEditor editor = index.editor();
		if (!modified.isEmpty()) {
			final TreeWalk walk = new TreeWalk(repo);
			iter.reset();
			walk.addTree(iter);
			walk.setRecursive(true);
			walk.setFilter(PathFilterGroup.createFromStrings(modified));

			MutableObjectId id = new MutableObjectId();
			while (walk.next()) {
				walk.getObjectId(id, 0);
				final DirCacheEntry entry = new DirCacheEntry(walk.getRawPath());
				entry.setLength(iter.getEntryLength());
				entry.setLastModified(iter.getEntryLastModified());
				entry.setFileMode(iter.getEntryFileMode());
				InputStream in = iter.openEntryStream();
				try {
					entry.setObjectId(inserter.insert(Constants.OBJ_BLOB,
							iter.getEntryLength(), in));
				} finally {
					in.close();
				}
				editor.add(new PathEdit(walk.getPathString()) {

					public void apply(DirCacheEntry ent) {
						ent.copyMetaData(entry);
					}
				});
			}
		}

		for (String path : missing)
			editor.add(new DeletePath(path));

		editor.finish();
	}

	public RevCommit call() throws Exception {
		checkCallable();

		Ref head = repo.getRef(Constants.HEAD);
		if (head == null || head.getObjectId() == null)
			throw new NoHeadException("HEAD required"); //$NON-NLS-1$

		ObjectId headId = head.getObjectId();
		String branch = Repository.shortenRefName(head.getTarget().getName());
		String headMessage = getHeadCommitMessage(head.getObjectId());

		DirCache cache = repo.lockDirCache();
		ObjectId commitId = null;
		final ObjectInserter inserter = repo.newObjectInserter();
		try {
			final FileTreeIterator fileTreeIter = new FileTreeIterator(repo);
			IndexDiff diff = new IndexDiff(repo, headId, fileTreeIter);
			diff.diff();

			// Commit index changes
			CommitBuilder builder = createBuilder(headId);
			builder.setTreeId(cache.writeTree(inserter));
			builder.setMessage("index on " + branch + ": " + headMessage);
			ObjectId indexCommit = inserter.insert(builder);

			// Commit working tree changes
			builder.addParentId(indexCommit);
			builder.setMessage("WIP on " + branch + ": " + headMessage);
			addToIndex(cache, diff.getModified(), diff.getMissing(),
					fileTreeIter, inserter);
			builder.setTreeId(cache.writeTree(inserter));
			commitId = inserter.insert(builder);
			inserter.flush();

			updateStashRef(commitId);
		} finally {
			inserter.release();
			cache.unlock();
		}

		// Hard reset to HEAD
		new ResetCommand(repo).setMode(ResetType.HARD).call();

		// Return stashed commit
		RevWalk rWalk = new RevWalk(repo);
		try {
			return rWalk.parseCommit(commitId);
		} finally {
			rWalk.release();
		}
	}
}
