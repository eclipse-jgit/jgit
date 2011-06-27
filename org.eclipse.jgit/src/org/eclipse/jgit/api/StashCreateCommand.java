/*
 * Copyright (C) 2011, Abhishek Bhatnagar <abhatnag@redhat.com>
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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeMessageFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;

/**
 * Stash the changes in a dirty working directory away
 * 
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 */
public class StashCreateCommand extends GitCommand<RevCommit> {
	/**
	 * parents this commit should have. The current HEAD will be in this list
	 * and also all commits mentioned in .git/MERGE_HEAD
	 */
	private List<ObjectId> parents = new LinkedList<ObjectId>();

	private PersonIdent author = new PersonIdent("Abhishek Bhatnagar",
			"abhatnag@redhat.com");

	private PersonIdent committer = new PersonIdent("Test User",
			"test@redhat.com");

	private MergeStrategy mergeStrategy = MergeStrategy.RESOLVE;

	/**
	 * @param repo
	 */
	protected StashCreateCommand(Repository repo) {
		super(repo);
	}

	public RevCommit call() throws Exception {
		// create commit of current index

		// determine the current HEAD and the commit it is referring to
		ObjectId headCommitId = repo.resolve(Constants.HEAD + "^{commit}");
		System.out.println("Head commit id: " + headCommitId);
		if (headCommitId == null) {
			RevCommit previousCommit = new RevWalk(repo)
					.parseCommit(headCommitId);
			RevCommit[] p = previousCommit.getParents();
			for (int i = 0; i < p.length; i++)
				parents.add(0, p[i].getId());
		} else {
			parents.add(0, headCommitId);
		}

		// lock the index
		DirCache index = repo.lockDirCache();

		try {
			ObjectInserter odi = repo.newObjectInserter();
			try {
				ObjectId indexTreeId = index.writeTree(odi);
				System.out.println("indexTreeId: " + indexTreeId);

				// experiment
				DirCacheBuilder b = index.builder();
				DirCacheEntry ent = new DirCacheEntry("a");
				ent.setFileMode(FileMode.REGULAR_FILE);
				ent.setObjectId(new ObjectInserter.Formatter().idFor(OBJ_BLOB,
						Constants.encode("a")));
				b.add(ent);
				b.finish();

				// create a commit object, populate it and write it
				CommitBuilder newCommit = new CommitBuilder();
				newCommit.setCommitter(committer);
				newCommit.setAuthor(author);
				newCommit.setMessage("index on master: 1e41dc first commit");
				newCommit.setParentIds(parents);
				newCommit.setTreeId(indexTreeId);

				// insert commit
				ObjectId newCommitId = odi.insert(newCommit);
				odi.flush();
				System.out.println("first commit made");

				// temp head and src commits
				System.out.println("Commit 1: " + headCommitId);
				System.out.println("Commit 2: " + newCommitId);

				RevWalk revWalk = new RevWalk(repo);
				try {
					RevCommit revCommit = revWalk.parseCommit(headCommitId);

					// ////////////////////////////////////////////////////////////////////////////////////
					// artificial merge
					boolean noProblems = false;
					// RevCommit newHead = null;
					Map<String, MergeFailureReason> failingPaths = null;
					List<String> unmergedPaths = null;

					// begin
					repo.writeMergeHeads(Arrays.asList(headCommitId,
							newCommitId));
					ThreeWayMerger merger = (ThreeWayMerger) mergeStrategy
							.newMerger(repo);

					// do the actual merging
					if (merger instanceof ResolveMerger) {
						ResolveMerger resolveMerger = (ResolveMerger) merger;
						resolveMerger.setCommitNames(new String[] { "BASE",
								"HEAD", "NEW" }); // come back here
						resolveMerger
								.setWorkingTreeIterator(new FileTreeIterator(
										repo));
						// Find two commits, that I'm merging
						// noProblems = merger.merge(headCommitId, newCommitId);
						// ///////////////////////////////////////////////////
						RevObject[] sourceObjects = null;
						ObjectReader reader = repo.newObjectReader();
						RevWalk walk = new RevWalk(reader);
						RevCommit[] sourceCommits = null;
						RevTree[] sourceTrees = null;
						AnyObjectId[] tips = new AnyObjectId[2];
						tips[0] = headCommitId;
						tips[1] = newCommitId;

						sourceObjects = new RevObject[tips.length];
						for (int i = 0; i < tips.length; i++)
							sourceObjects[i] = walk.parseAny(tips[i]);

						sourceCommits = new RevCommit[sourceObjects.length];
						for (int i = 0; i < sourceObjects.length; i++) {
							try {
								sourceCommits[i] = walk
										.parseCommit(sourceObjects[i]);
							} catch (IncorrectObjectTypeException err) {
								sourceCommits[i] = null;
							}
						}

						sourceTrees = new RevTree[sourceObjects.length];
						for (int i = 0; i < sourceObjects.length; i++)
							sourceTrees[i] = walk.parseTree(sourceObjects[i]);
						// ///////////////////////////////////////////////////
						failingPaths = resolveMerger.getFailingPaths();
						unmergedPaths = resolveMerger.getUnmergedPaths();
						noProblems = true;
					} else {
						noProblems = merger.merge(headCommitId, newCommitId);
					}

					// check for problems
					if (noProblems) {
						System.out.println("NO PROBLEMS");
						/*
						 * DirCacheCheckout dco = new DirCacheCheckout(repo,
						 * ((RevCommit) headCommitId).getTree(), // fishy
						 * repo.lockDirCache(), merger.getResultTreeId());
						 * dco.setFailOnConflict(true); dco.checkout();
						 */
						// convert to manual commit
						/*
						 * newHead = new Git(getRepository()).commit()
						 * .setAuthor(author).setCommitter(committer)
						 * .setMessage("Sup Dawg").call();
						 */
						// create a commit object, populate it and write it
						CommitBuilder latestCommit = new CommitBuilder();
						latestCommit.setCommitter(committer);
						latestCommit.setAuthor(author);
						latestCommit
								.setMessage("index on master: e4783e2 first commit");
						latestCommit.setParentIds(parents);
						latestCommit.setTreeId(indexTreeId);

						ObjectId newHead = odi.insert(newCommit);
						odi.flush();

						RefUpdate ru = repo.updateRef(Constants.R_STASH);
						ru.setNewObjectId(newHead);

						// this is going to be ObjectId.zeroId() if it's the
						// first
						// stash
						// if not, grab the id from what the ref currently
						// points to
						ru.setExpectedOldObjectId(ObjectId.zeroId());
						Result rc = ru.forceUpdate();

						switch (rc) {
						case FAST_FORWARD: {
							setCallable(false);
							return revCommit;
						}
						case NEW: {
							setCallable(false);
							return revCommit;
						}
						case LOCK_FAILURE:
							throw new ConcurrentRefUpdateException(
									JGitText.get().couldNotLockHEAD,
									ru.getRef(), rc);
						default:
							System.out.println("ERROR: " + rc);
							throw new JGitInternalException(
									"Something went wrong");
						}
					} else {
						System.out.println("PROBLEMS: " + !noProblems);
						if (failingPaths != null) {
							System.out.println("Not good, something happened");
							repo.writeMergeCommitMsg(null);
							repo.writeMergeHeads(null);
						} else {
							String mergeMessageWithConflicts = new MergeMessageFormatter()
									.formatWithConflicts(
											"index on master: e4783e2 first commit",
											unmergedPaths);
							repo.writeMergeCommitMsg(mergeMessageWithConflicts);
						}
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} finally {
			index.unlock();
		}
		return null;
	}
}
