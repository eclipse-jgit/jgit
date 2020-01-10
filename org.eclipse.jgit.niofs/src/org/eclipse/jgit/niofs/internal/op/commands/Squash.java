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
import java.util.Spliterator;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.revwalk.RevCommit;

import static java.util.stream.StreamSupport.stream;

/**
 * Implements the Git Squash command. It needs the repository were he is going
 * to make the squash, the squash commit message, and the start commit, to know
 * from where he has to squash. It return an Empty Optional because is not
 * necessary to return anything. It throws a {@link GitException} if something
 * bad happens.
 */
public class Squash {

	private final String branch;
	private final GitImpl git;
	private String squashedCommitMessage;
	private String startCommitString;

	public Squash(final GitImpl git, final String branch, final String startCommitString,
			final String squashedCommitMessage) {
		this.git = git;
		this.squashedCommitMessage = squashedCommitMessage;
		this.branch = branch;
		this.startCommitString = startCommitString;
	}

	public void execute() {
		final Repository repo = this.git.getRepository();

		final RevCommit latestCommit = git.getLastCommit(branch);
		final RevCommit startCommit = checkIfCommitIsPresentAtBranch(this.git, this.branch, this.startCommitString);

		RevCommit parent = startCommit;
		if (startCommit.getParentCount() > 0) {
			parent = startCommit.getParent(0);
		}

		final CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setParentId(parent);
		commitBuilder.setTreeId(latestCommit.getTree().getId());
		commitBuilder.setMessage(squashedCommitMessage);
		commitBuilder.setAuthor(startCommit.getAuthorIdent());
		commitBuilder.setCommitter(startCommit.getAuthorIdent());

		try (final ObjectInserter odi = repo.newObjectInserter()) {
			final RevCommit squashedCommit = git.resolveRevCommit(odi.insert(commitBuilder));
			git.refUpdate(branch, squashedCommit);
		} catch (ConcurrentRefUpdateException | IOException e) {
			throw new GitException("Error on executing squash.", e);
		}
	}

	/**
	 * It checks if the commit is present on branch logs. If not it throws a
	 * {@link GitException}
	 * 
	 * @param git               The git repository
	 * @param branch            The branch where it is going to do the search
	 * @param startCommitString The commit it needs to find
	 * @throws {@link GitException} when it cannot find the commit in that branch
	 */
	private RevCommit checkIfCommitIsPresentAtBranch(final GitImpl git, final String branch,
			final String startCommitString) {

		try {
			final ObjectId id = git.getRef(branch).getObjectId();
			final Spliterator<RevCommit> log = git._log().add(id).call().spliterator();
			return stream(log, false).filter((elem) -> elem.getName().equals(startCommitString)).findFirst()
					.orElseThrow(() -> new GitException("Commit is not present at branch " + branch));
		} catch (GitAPIException | MissingObjectException | IncorrectObjectTypeException e) {
			throw new GitException("A problem occurred when trying to get commit list", e);
		}
	}
}
