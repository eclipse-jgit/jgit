/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeMessageFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;

/**
 * A class used to execute a {@code cherry-pick} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-cherry-pick.html"
 *      >Git documentation about cherry-pick</a>
 */
public class CherryPickCommand extends GitCommand<CherryPickResult> {
	private String reflogPrefix = "cherry-pick:"; //$NON-NLS-1$

	private List<Ref> commits = new LinkedList<Ref>();

	private String ourCommitName = null;

	private MergeStrategy strategy = MergeStrategy.RECURSIVE;

	/**
	 * @param repo
	 */
	protected CherryPickCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Cherry-Pick} command with all the options and
	 * parameters collected by the setter methods (e.g. {@link #include(Ref)} of
	 * this class. Each instance of this class should only be used for one
	 * invocation of the command. Don't call this method twice on an instance.
	 *
	 * @return the result of the cherry-pick
	 * @throws GitAPIException
	 * @throws WrongRepositoryStateException
	 * @throws ConcurrentRefUpdateException
	 * @throws UnmergedPathsException
	 * @throws NoMessageException
	 * @throws NoHeadException
	 */
	public CherryPickResult call() throws GitAPIException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, NoHeadException {
		RevCommit newHead = null;
		List<Ref> cherryPickedRefs = new LinkedList<Ref>();
		checkCallable();

		RevWalk revWalk = new RevWalk(repo);
		try {

			// get the head commit
			Ref headRef = repo.getRef(Constants.HEAD);
			if (headRef == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);

			newHead = revWalk.parseCommit(headRef.getObjectId());

			// loop through all refs to be cherry-picked
			for (Ref src : commits) {
				// get the commit to be cherry-picked
				// handle annotated tags
				ObjectId srcObjectId = src.getPeeledObjectId();
				if (srcObjectId == null)
					srcObjectId = src.getObjectId();
				RevCommit srcCommit = revWalk.parseCommit(srcObjectId);

				// get the parent of the commit to cherry-pick
				if (srcCommit.getParentCount() != 1)
					throw new MultipleParentsNotAllowedException(
							MessageFormat.format(
									JGitText.get().canOnlyCherryPickCommitsWithOneParent,
									srcCommit.name(),
									Integer.valueOf(srcCommit.getParentCount())));

				RevCommit srcParent = srcCommit.getParent(0);
				revWalk.parseHeaders(srcParent);

				String ourName = calculateOurName(headRef);
				String cherryPickName = srcCommit.getId().abbreviate(7).name()
						+ " " + srcCommit.getShortMessage(); //$NON-NLS-1$

				ResolveMerger merger = (ResolveMerger) strategy.newMerger(repo);
				merger.setWorkingTreeIterator(new FileTreeIterator(repo));
				merger.setBase(srcParent.getTree());
				merger.setCommitNames(new String[] { "BASE", ourName,
						cherryPickName });
				if (merger.merge(newHead, srcCommit)) {
					if (AnyObjectId.equals(newHead.getTree().getId(), merger
							.getResultTreeId()))
						continue;
					DirCacheCheckout dco = new DirCacheCheckout(repo,
							newHead.getTree(), repo.lockDirCache(),
							merger.getResultTreeId());
					dco.setFailOnConflict(true);
					dco.checkout();
					newHead = new Git(getRepository()).commit()
							.setMessage(srcCommit.getFullMessage())
							.setReflogComment(reflogPrefix + " " //$NON-NLS-1$
									+ srcCommit.getShortMessage())
							.setAuthor(srcCommit.getAuthorIdent()).call();
					cherryPickedRefs.add(src);
				} else {
					if (merger.failed())
						return new CherryPickResult(merger.getFailingPaths());

					// there are merge conflicts

					String message = new MergeMessageFormatter()
							.formatWithConflicts(srcCommit.getFullMessage(),
									merger.getUnmergedPaths());

					repo.writeCherryPickHead(srcCommit.getId());
					repo.writeMergeCommitMsg(message);

					return CherryPickResult.CONFLICT;
				}
			}
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(
							JGitText.get().exceptionCaughtDuringExecutionOfCherryPickCommand,
							e), e);
		} finally {
			revWalk.release();
		}
		return new CherryPickResult(newHead, cherryPickedRefs);
	}

	/**
	 * @param commit
	 *            a reference to a commit which is cherry-picked to the current
	 *            head
	 * @return {@code this}
	 */
	public CherryPickCommand include(Ref commit) {
		checkCallable();
		commits.add(commit);
		return this;
	}

	/**
	 * @param commit
	 *            the Id of a commit which is cherry-picked to the current head
	 * @return {@code this}
	 */
	public CherryPickCommand include(AnyObjectId commit) {
		return include(commit.getName(), commit);
	}

	/**
	 * @param name
	 *            a name given to the commit
	 * @param commit
	 *            the Id of a commit which is cherry-picked to the current head
	 * @return {@code this}
	 */
	public CherryPickCommand include(String name, AnyObjectId commit) {
		return include(new ObjectIdRef.Unpeeled(Storage.LOOSE, name,
				commit.copy()));
	}

	/**
	 * @param ourCommitName
	 *            the name that should be used in the "OURS" place for conflict
	 *            markers
	 * @return {@code this}
	 */
	public CherryPickCommand setOurCommitName(String ourCommitName) {
		this.ourCommitName = ourCommitName;
		return this;
	}

	/**
	 * Set the prefix to use in the reflog.
	 * <p>
	 * This is primarily needed for implementing rebase in terms of
	 * cherry-picking
	 *
	 * @param prefix
	 *            including ":"
	 * @return {@code this}
	 * @since 3.1
	 */
	public CherryPickCommand setReflogPrefix(final String prefix) {
		this.reflogPrefix = prefix;
		return this;
	}

	/**
	 * @param strategy
	 *            The merge strategy to use in order to merge during the
	 *            execution of this Cherry-pick.
	 * @return {@code this}
	 * @since 3.4
	 */
	public CherryPickCommand setStrategy(MergeStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	private String calculateOurName(Ref headRef) {
		if (ourCommitName != null)
			return ourCommitName;

		String targetRefName = headRef.getTarget().getName();
		String headName = Repository.shortenRefName(targetRefName);
		return headName;
	}
}
