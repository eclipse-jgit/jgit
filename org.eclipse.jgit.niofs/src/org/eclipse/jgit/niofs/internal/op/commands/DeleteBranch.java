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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.GitImpl;

public class DeleteBranch {

	private final GitImpl git;
	private final Ref branch;

	public DeleteBranch(final GitImpl git, final Ref branch) {
		this.git = git;
		this.branch = branch;
	}

	public void execute() throws IOException {
		try {
			git._branchDelete().setBranchNames(branch.getName()).setForce(true).call();
		} catch (final GitAPIException e) {
			throw new IOException(e);
		}
	}
}
