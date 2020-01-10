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

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

public class GetFirstCommit {

	private final Git git;
	private final Ref ref;

	public GetFirstCommit(final Git git, final String branchName) {
		this(git, git.getRef(branchName));
	}

	public GetFirstCommit(final Git git, final Ref ref) {
		this.git = git;
		this.ref = ref;
	}

	public RevCommit execute() throws IOException {
		try (final RevWalk rw = new RevWalk(git.getRepository())) {
			final RevCommit root = rw.parseCommit(ref.getObjectId());
			rw.sort(RevSort.REVERSE);
			rw.markStart(root);
			return rw.next();
		} catch (final IOException ignored) {
		}
		return null;
	}
}