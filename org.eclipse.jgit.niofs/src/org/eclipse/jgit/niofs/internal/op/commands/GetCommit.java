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

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class GetCommit {

	private final Git git;
	private final String commitId;

	public GetCommit(final Git git, final String commitId) {
		this.git = checkNotNull("git", git);
		this.commitId = checkNotEmpty("commitId", commitId);
	}

	public RevCommit execute() {
		final Repository repository = git.getRepository();

		try (final RevWalk revWalk = new RevWalk(repository)) {
			final ObjectId id = repository.resolve(this.commitId);
			return id != null ? revWalk.parseCommit(id) : null;
		} catch (Exception e) {
			throw new GitException("Error when trying to get commit", e);
		}
	}
}
