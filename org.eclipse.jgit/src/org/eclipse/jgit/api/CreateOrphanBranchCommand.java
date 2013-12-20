/*
 * Copyright (C) 2013 SATO taichi <ryushi@gmail.com>
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
import java.util.EnumSet;

import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.StringUtils;

/**
 * Create a new orphan branch.
 *
 * @author taichi
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-checkout.html"
 *      >Git documentation about Checkout</a>
 */
public class CreateOrphanBranchCommand extends GitCommand<Ref> {

	String name;

	String startPoint;

	RevCommit startCommit;

	CheckoutResult status;

	/**
	 * @param repo
	 */
	protected CreateOrphanBranchCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @param name
	 *            the name of the new branch
	 * @return this instance
	 */
	public CreateOrphanBranchCommand setName(String name) {
		this.checkCallable();
		this.name = name;
		return this;
	}

	/**
	 * Set the name of the commit that should be checked out.
	 * <p>
	 * When checking out files and this is not specified or <code>null</code>,
	 * the index is used.
	 * <p>
	 * When creating a new branch, this will be used as the start point. If not
	 * specified or <code>null</code>, the current HEAD is used.
	 *
	 * @param startPoint
	 *            commit name to check out
	 * @return this instance
	 */
	public CreateOrphanBranchCommand setStartPoint(String startPoint) {
		this.checkCallable();
		this.startPoint = startPoint;
		this.startCommit = null;
		return this;
	}

	/**
	 * Set the commit that should be checked out.
	 * <p>
	 * When creating a new branch, this will be used as the start point. If not
	 * specified or <code>null</code>, the current HEAD is used.
	 * <p>
	 * When checking out files and this is not specified or <code>null</code>,
	 * the index is used.
	 *
	 * @param startCommit
	 *            commit to check out
	 * @return this instance
	 */
	public CreateOrphanBranchCommand setStartPoint(RevCommit startCommit) {
		this.checkCallable();
		this.startPoint = null;
		this.startCommit = startCommit;
		return this;
	}

	@Override
	public Ref call() throws GitAPIException, RefNotFoundException,
			CheckoutConflictException, InvalidRefNameException,
			RefAlreadyExistsException {
		this.checkCallable();
		try {
			this.processOptions();
			this.checkoutStartPoint();
			RefUpdate update = this.getRepository().updateRef(Constants.HEAD);
			Result r = update.link(this.getBranchName());
			if (EnumSet.of(Result.NEW, Result.FORCED).contains(r) == false)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().checkoutUnexpectedResult, r.name()));

			this.setCallable(false);
			return this.getRepository().getRef(Constants.HEAD);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private void processOptions() throws InvalidRefNameException,
			RefAlreadyExistsException, IOException {
		if (name == null || !Repository.isValidRefName(getBranchName()))
			throw new InvalidRefNameException(MessageFormat.format(JGitText
					.get().branchNameInvalid, name == null ? "<null>" : name)); //$NON-NLS-1$

		Ref refToCheck = this.getRepository().getRef(getBranchName());
		if (refToCheck != null)
			throw new RefAlreadyExistsException(MessageFormat.format(
					JGitText.get().refAlreadyExists, name));
	}

	private String getBranchName() {
		if (name.startsWith(Constants.R_REFS))
			return name;

		return Constants.R_HEADS + name;
	}

	private void checkoutStartPoint() throws GitAPIException,
			RefNotFoundException, CheckoutConflictException, IOException {
		ObjectId sp = this.getStartPoint();
		if (sp != null)
			this.checkout(sp);
	}

	private ObjectId getStartPoint() throws RefNotFoundException, IOException {
		if (this.startCommit != null)
			return this.startCommit.getId();

		if (!StringUtils.isEmptyOrNull(this.startPoint)) {
			ObjectId oid = this.getRepository().resolve(this.startPoint);
			if (oid == null)
				throw new RefNotFoundException(MessageFormat.format(
						JGitText.get().refNotResolved, this.startPoint));

			return oid;
		}
		return null;
	}

	private void checkout(ObjectId fromId) throws GitAPIException,
			CheckoutConflictException, IOException {
		RevWalk rw = new RevWalk(this.getRepository());
		try {
			Ref headRef = this.repo.getRef(Constants.HEAD);
			AnyObjectId headId = headRef.getObjectId();
			RevCommit headCommit = headId == null ? null : rw
					.parseCommit(headId);
			RevTree headTree = headCommit == null ? null : headCommit.getTree();
			RevCommit from = rw.parseCommit(fromId);
			this.checkout(headTree, from.getTree());
		} finally {
			rw.release();
		}
	}

	private void checkout(RevTree headTree, RevTree fromTree)
			throws GitAPIException, CheckoutConflictException, IOException {
		// DirCacheCheckout free lock of DirCache
		DirCacheCheckout dco = new DirCacheCheckout(this.getRepository(),
				headTree, this.repo.lockDirCache(), fromTree);
		dco.setFailOnConflict(true);
		try {
			dco.checkout();
			if (!dco.getToBeDeleted().isEmpty())
				status = new CheckoutResult(Status.NONDELETED,
						dco.getToBeDeleted());

		} catch (org.eclipse.jgit.errors.CheckoutConflictException e) {
			status = new CheckoutResult(Status.CONFLICTS, dco.getConflicts());
			throw new CheckoutConflictException(dco.getConflicts(), e);
		}
	}

	/**
	 * @return the result, never <code>null</code>
	 */
	public CheckoutResult getResult() {
		if (status == null)
			return CheckoutResult.NOT_TRIED_RESULT;
		return status;
	}
}