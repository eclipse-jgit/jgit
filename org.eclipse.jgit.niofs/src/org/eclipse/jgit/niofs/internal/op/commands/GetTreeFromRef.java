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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class GetTreeFromRef {

	private final Git git;
	private final String treeRefName;

	public GetTreeFromRef(final Git git, final String treeRefName) {
		this.git = git;
		this.treeRefName = treeRefName;
	}

	public ObjectId execute() {
		final RevCommit commit = git.getLastCommit(treeRefName);
		if (commit == null) {
			return null;
		}
		return commit.getTree().getId();
	}
}
