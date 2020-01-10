/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.commands.CreateBranch;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class JGitGetCommonAncestorCommitTest extends BaseTest {

	private Git git;

	private static final String MASTER_BRANCH = "master";
	private static final String DEVELOP_BRANCH = "develop";

	@Before
	public void setup() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, "source/source.git");

		git = new CreateRepository(gitSource).execute().get();
	}

	@Test
	public void successTest() throws IOException {
		commit(git, MASTER_BRANCH, "Adding file", content("file.txt", "file content"));

		RevCommit expectedCommonAncestorCommit = git.getLastCommit(MASTER_BRANCH);

		new CreateBranch((GitImpl) git, MASTER_BRANCH, DEVELOP_BRANCH).execute();

		commit(git, MASTER_BRANCH, "Updating file", content("file.txt", "file content 1"));
		commit(git, MASTER_BRANCH, "Updating file", content("file.txt", "file content 2"));

		commit(git, DEVELOP_BRANCH, "Updating file", content("file.txt", "file content 3"));
		commit(git, DEVELOP_BRANCH, "Updating file", content("file.txt", "file content 4"));

		RevCommit actualCommonAncestorCommit = git.getCommonAncestorCommit(MASTER_BRANCH, DEVELOP_BRANCH);

		assertThat(actualCommonAncestorCommit.getName()).isEqualTo(expectedCommonAncestorCommit.getName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidBranchTest() {
		git.getCommonAncestorCommit(MASTER_BRANCH, "invalid-branch");
	}
}
