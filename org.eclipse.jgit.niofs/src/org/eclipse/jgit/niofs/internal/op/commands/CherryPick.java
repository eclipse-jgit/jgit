/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class CherryPick {

	private final Git git;
	private final String targetBranch;
	private final String[] commits;

	public CherryPick(final Git git, final String targetBranch, final String... commits) {
		this.git = git;
		this.targetBranch = targetBranch;
		this.commits = commits;
	}

	public void execute() throws IOException {
		final List<ObjectId> commits = git.resolveObjectIds(this.commits);
		if (commits.size() != this.commits.length) {
			throw new IOException("Couldn't resolve some commits.");
		}

		final Ref headRef = git.getRef(targetBranch);
		if (headRef == null) {
			throw new IOException("Branch not found.");
		}

		try {
			// loop through all refs to be cherry-picked
			for (final ObjectId src : commits) {
				final RevCommit srcCommit = git.resolveRevCommit(src);

				// get the parent of the commit to cherry-pick
				if (srcCommit.getParentCount() != 1) {
					throw new IOException(new MultipleParentsNotAllowedException(
							MessageFormat.format(JGitText.get().canOnlyCherryPickCommitsWithOneParent, srcCommit.name(),
									srcCommit.getParentCount())));
				}

				git.refUpdate(targetBranch, srcCommit);
			}
		} catch (final java.io.IOException e) {
			throw new IOException(new JGitInternalException(
					MessageFormat.format(JGitText.get().exceptionCaughtDuringExecutionOfCherryPickCommand, e), e));
		} catch (final Exception e) {
			throw new IOException(e);
		}
	}
}
