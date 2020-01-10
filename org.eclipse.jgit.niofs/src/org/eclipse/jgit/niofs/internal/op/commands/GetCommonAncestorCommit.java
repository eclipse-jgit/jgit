/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

public class GetCommonAncestorCommit {

	private final Git git;
	private final RevCommit commitA;
	private final RevCommit commitB;

	public GetCommonAncestorCommit(final Git git, final RevCommit commitA, final RevCommit commitB) {
		this.git = checkNotNull("git", git);
		this.commitA = checkNotNull("commitA", commitA);
		this.commitB = checkNotNull("commitB", commitB);
	}

	public RevCommit execute() {
		try (final RevWalk revWalk = new RevWalk(git.getRepository())) {
			final RevCommit validatedCommitA = revWalk.lookupCommit(this.commitA);
			final RevCommit validatedCommitB = revWalk.lookupCommit(this.commitB);

			revWalk.setRevFilter(RevFilter.MERGE_BASE);
			revWalk.markStart(validatedCommitA);
			revWalk.markStart(validatedCommitB);
			return revWalk.next();
		} catch (Exception e) {
			throw new GitException("Error when trying to get common ancestor", e);
		}
	}
}
