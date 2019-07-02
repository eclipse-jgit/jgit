/*
 * Copyright (C) 2012, GitHub Inc.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.IndexDiffFilter;
import org.eclipse.jgit.treewalk.filter.SkipWorkTreeFilter;
import org.eclipse.jgit.util.FileUtils;

/**
 * Command class to stash changes in the working directory and index in a
 * commit.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 * @since 2.0
 */
public class StashCreateCommand extends GitCommand<RevCommit> {

	private static final String MSG_INDEX = "index on {0}: {1} {2}"; //$NON-NLS-1$

	private static final String MSG_UNTRACKED = "untracked files on {0}: {1} {2}"; //$NON-NLS-1$

	private static final String MSG_WORKING_DIR = "WIP on {0}: {1} {2}"; //$NON-NLS-1$

	private String indexMessage = MSG_INDEX;

	private String workingDirectoryMessage = MSG_WORKING_DIR;

	private String ref = Constants.R_STASH;

	private PersonIdent person;

	private boolean includeUntracked;

	/**
	 * Create a command to stash changes in the working directory and index
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public StashCreateCommand(Repository repo) {
		super(repo);
		person = new PersonIdent(repo);
	}

	/**
	 * Set the message used when committing index changes
	 * <p>
	 * The message will be formatted with the current branch, abbreviated commit
	 * id, and short commit message when used.
	 *
	 * @param message
	 *            the stash message
	 * @return {@code this}
	 */
	public StashCreateCommand setIndexMessage(String message) {
		indexMessage = message;
		return this;
	}

	/**
	 * Set the message used when committing working directory changes
	 * <p>
	 * The message will be formatted with the current branch, abbreviated commit
	 * id, and short commit message when used.
	 *
	 * @param message
	 *            the working directory message
	 * @return {@code this}
	 */
	public StashCreateCommand setWorkingDirectoryMessage(String message) {
		workingDirectoryMessage = message;
		return this;
	}

	/**
	 * Set the person to use as the author and committer in the commits made
	 *
	 * @param person
	 *            the {@link org.eclipse.jgit.lib.PersonIdent} of the person who
	 *            creates the stash.
	 * @return {@code this}
	 */
	public StashCreateCommand setPerson(PersonIdent person) {
		this.person = person;
		return this;
	}

	/**
	 * Set the reference to update with the stashed commit id If null, no
	 * reference is updated
	 * <p>
	 * This value defaults to {@link org.eclipse.jgit.lib.Constants#R_STASH}
	 *
	 * @param ref
	 *            the name of the {@code Ref} to update
	 * @return {@code this}
	 */
	public StashCreateCommand setRef(String ref) {
		this.ref = ref;
		return this;
	}

	/**
	 * Whether to include untracked files in the stash.
	 *
	 * @param includeUntracked
	 *            whether to include untracked files in the stash
	 * @return {@code this}
	 * @since 3.4
	 */
	public StashCreateCommand setIncludeUntracked(boolean includeUntracked) {
		this.includeUntracked = includeUntracked;
		return this;
	}

	private RevCommit parseCommit(final ObjectReader reader,
			final ObjectId headId) throws IOException {
		try (RevWalk walk = new RevWalk(reader)) {
			return walk.parseCommit(headId);
		}
	}

	private CommitBuilder createBuilder() {
		CommitBuilder builder = new CommitBuilder();
		PersonIdent author = person;
		if (author == null)
			author = new PersonIdent(repo);
		builder.setAuthor(author);
		builder.setCommitter(author);
		return builder;
	}

	private void updateStashRef(ObjectId commitId, PersonIdent refLogIdent,
			String refLogMessage) throws IOException {
		if (ref == null)
			return;
		Ref currentRef = repo.findRef(ref);
		RefUpdate refUpdate = repo.updateRef(ref);
		refUpdate.setNewObjectId(commitId);
		refUpdate.setRefLogIdent(refLogIdent);
		refUpdate.setRefLogMessage(refLogMessage, false);
		refUpdate.setForceRefLog(true);
		if (currentRef != null)
			refUpdate.setExpectedOldObjectId(currentRef.getObjectId());
		else
			refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
		refUpdate.forceUpdate();
	}

	private Ref getHead() throws GitAPIException {
		try {
			Ref head = repo.exactRef(Constants.HEAD);
			if (head == null || head.getObjectId() == null)
				throw new NoHeadException(JGitText.get().headRequiredToStash);
			return head;
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashFailed, e);
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Stash the contents on the working directory and index in separate commits
	 * and reset to the current HEAD commit.
	 */
	@Override
	public RevCommit call() throws GitAPIException {
		checkCallable();

		List<String> deletedFiles = new ArrayList<>();
		Ref head = getHead();
		try (ObjectReader reader = repo.newObjectReader()) {
			RevCommit headCommit = parseCommit(reader, head.getObjectId());
			DirCache cache = repo.lockDirCache();
			ObjectId commitId;
			try (ObjectInserter inserter = repo.newObjectInserter();
					TreeWalk treeWalk = new TreeWalk(repo, reader)) {

				treeWalk.setRecursive(true);
				treeWalk.addTree(headCommit.getTree());
				treeWalk.addTree(new DirCacheIterator(cache));
				treeWalk.addTree(new FileTreeIterator(repo));
				treeWalk.getTree(2, FileTreeIterator.class)
						.setDirCacheIterator(treeWalk, 1);
				treeWalk.setFilter(AndTreeFilter.create(new SkipWorkTreeFilter(
						1), new IndexDiffFilter(1, 2)));

				// Return null if no local changes to stash
				if (!treeWalk.next())
					return null;

				MutableObjectId id = new MutableObjectId();
				List<PathEdit> wtEdits = new ArrayList<>();
				List<String> wtDeletes = new ArrayList<>();
				List<DirCacheEntry> untracked = new ArrayList<>();
				boolean hasChanges = false;
				do {
					AbstractTreeIterator headIter = treeWalk.getTree(0,
							AbstractTreeIterator.class);
					DirCacheIterator indexIter = treeWalk.getTree(1,
							DirCacheIterator.class);
					WorkingTreeIterator wtIter = treeWalk.getTree(2,
							WorkingTreeIterator.class);
					if (indexIter != null
							&& !indexIter.getDirCacheEntry().isMerged())
						throw new UnmergedPathsException(
								new UnmergedPathException(
										indexIter.getDirCacheEntry()));
					if (wtIter != null) {
						if (indexIter == null && headIter == null
								&& !includeUntracked)
							continue;
						hasChanges = true;
						if (indexIter != null && wtIter.idEqual(indexIter))
							continue;
						if (headIter != null && wtIter.idEqual(headIter))
							continue;
						treeWalk.getObjectId(id, 0);
						final DirCacheEntry entry = new DirCacheEntry(
								treeWalk.getRawPath());
						entry.setLength(wtIter.getEntryLength());
						entry.setLastModified(
								wtIter.getEntryLastModifiedInstant());
						entry.setFileMode(wtIter.getEntryFileMode());
						long contentLength = wtIter.getEntryContentLength();
						try (InputStream in = wtIter.openEntryStream()) {
							entry.setObjectId(inserter.insert(
									Constants.OBJ_BLOB, contentLength, in));
						}

						if (indexIter == null && headIter == null)
							untracked.add(entry);
						else
							wtEdits.add(new PathEdit(entry) {
								@Override
								public void apply(DirCacheEntry ent) {
									ent.copyMetaData(entry);
								}
							});
					}
					hasChanges = true;
					if (wtIter == null && headIter != null)
						wtDeletes.add(treeWalk.getPathString());
				} while (treeWalk.next());

				if (!hasChanges)
					return null;

				String branch = Repository.shortenRefName(head.getTarget()
						.getName());

				// Commit index changes
				CommitBuilder builder = createBuilder();
				builder.setParentId(headCommit);
				builder.setTreeId(cache.writeTree(inserter));
				builder.setMessage(MessageFormat.format(indexMessage, branch,
						headCommit.abbreviate(7).name(),
						headCommit.getShortMessage()));
				ObjectId indexCommit = inserter.insert(builder);

				// Commit untracked changes
				ObjectId untrackedCommit = null;
				if (!untracked.isEmpty()) {
					DirCache untrackedDirCache = DirCache.newInCore();
					DirCacheBuilder untrackedBuilder = untrackedDirCache
							.builder();
					for (DirCacheEntry entry : untracked)
						untrackedBuilder.add(entry);
					untrackedBuilder.finish();

					builder.setParentIds(new ObjectId[0]);
					builder.setTreeId(untrackedDirCache.writeTree(inserter));
					builder.setMessage(MessageFormat.format(MSG_UNTRACKED,
							branch, headCommit.abbreviate(7).name(),
							headCommit.getShortMessage()));
					untrackedCommit = inserter.insert(builder);
				}

				// Commit working tree changes
				if (!wtEdits.isEmpty() || !wtDeletes.isEmpty()) {
					DirCacheEditor editor = cache.editor();
					for (PathEdit edit : wtEdits)
						editor.add(edit);
					for (String path : wtDeletes)
						editor.add(new DeletePath(path));
					editor.finish();
				}
				builder.setParentId(headCommit);
				builder.addParentId(indexCommit);
				if (untrackedCommit != null)
					builder.addParentId(untrackedCommit);
				builder.setMessage(MessageFormat.format(
						workingDirectoryMessage, branch,
						headCommit.abbreviate(7).name(),
						headCommit.getShortMessage()));
				builder.setTreeId(cache.writeTree(inserter));
				commitId = inserter.insert(builder);
				inserter.flush();

				updateStashRef(commitId, builder.getAuthor(),
						builder.getMessage());

				// Remove untracked files
				if (includeUntracked) {
					for (DirCacheEntry entry : untracked) {
						String repoRelativePath = entry.getPathString();
						File file = new File(repo.getWorkTree(),
								repoRelativePath);
						FileUtils.delete(file);
						deletedFiles.add(repoRelativePath);
					}
				}

			} finally {
				cache.unlock();
			}

			// Hard reset to HEAD
			new ResetCommand(repo).setMode(ResetType.HARD).call();

			// Return stashed commit
			return parseCommit(reader, commitId);
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashFailed, e);
		} finally {
			if (!deletedFiles.isEmpty()) {
				repo.fireEvent(
						new WorkingTreeModifiedEvent(null, deletedFiles));
			}
		}
	}
}
