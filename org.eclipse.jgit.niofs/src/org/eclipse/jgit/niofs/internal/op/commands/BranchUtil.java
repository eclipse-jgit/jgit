/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;

public class BranchUtil {

	private BranchUtil() {

	}

	public static void deleteUnfilteredBranches(final Repository repository, final List<String> branchesToKeep)
			throws GitAPIException {
		if (branchesToKeep == null || branchesToKeep.isEmpty()) {
			return;
		}

		final org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.wrap(repository);
		final String[] toDelete = git.branchList().call().stream().map(Ref::getName)
				.map(fullname -> fullname.substring(fullname.lastIndexOf('/') + 1))
				.filter(name -> !branchesToKeep.contains(name)).toArray(String[]::new);
		git.branchDelete().setBranchNames(toDelete).setForce(true).call();
	}

	public static void existsBranch(final Git git, final String branch) {
		if (git.getRef(branch) == null) {
			throw new GitException(String.format("Branch <<%s>> does not exist", branch));
		}
	}
}
