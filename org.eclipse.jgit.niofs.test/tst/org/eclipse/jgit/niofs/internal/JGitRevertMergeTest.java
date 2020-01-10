/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.commands.CreateBranch;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.junit.Before;
import org.junit.Test;

public class JGitRevertMergeTest extends BaseTest {

	private Git git;

	private static final String MASTER_BRANCH = "master";
	private static final String DEVELOP_BRANCH = "develop";

	private static final List<String> TXT_FILES = Stream.of("file0", "file1", "file2", "file3", "file4")
			.collect(Collectors.toList());

	private static final String[] COMMON_TXT_LINES = { "Line1", "Line2", "Line3", "Line4" };

	private String commonAncestorCommitId;

	@Before
	public void setup() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, "source/source.git");

		git = new CreateRepository(gitSource).execute().get();

		commit(git, MASTER_BRANCH, "Adding files into master",
				content(TXT_FILES.get(0), multiline(TXT_FILES.get(0), COMMON_TXT_LINES)),
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), COMMON_TXT_LINES)),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), COMMON_TXT_LINES)));

		new CreateBranch((GitImpl) git, MASTER_BRANCH, DEVELOP_BRANCH).execute();

		commit(git, DEVELOP_BRANCH, "Adding files",
				content(TXT_FILES.get(3), multiline(TXT_FILES.get(3), COMMON_TXT_LINES)),
				content(TXT_FILES.get(4), multiline(TXT_FILES.get(4), COMMON_TXT_LINES)));

		commonAncestorCommitId = git.getCommonAncestorCommit(DEVELOP_BRANCH, MASTER_BRANCH).getName();
	}

	@Test(expected = GitException.class)
	public void testInvalidSourceBranch() throws IOException {
		String mergeCommitId = doMerge();

		git.revertMerge("invalid-branch", MASTER_BRANCH, commonAncestorCommitId, mergeCommitId);
	}

	@Test(expected = GitException.class)
	public void testInvalidTargetBranch() throws IOException {
		String mergeCommitId = doMerge();

		git.revertMerge(DEVELOP_BRANCH, "invalid-branch", commonAncestorCommitId, mergeCommitId);
	}

	@Test
	public void testRevertFailedMergeIsNotLastTargetCommit() throws IOException {
		String mergeCommitId = doMerge();

		commit(git, MASTER_BRANCH, "Updating file", content(TXT_FILES.get(0), "new content"));

		boolean result = git.revertMerge(DEVELOP_BRANCH, MASTER_BRANCH, commonAncestorCommitId, mergeCommitId);

		assertThat(result).isFalse();
	}

	@Test
	public void testRevertFailedMergeParentTargetIsNotCommonAncestor() throws IOException {
		commit(git, MASTER_BRANCH, "Updating file", content(TXT_FILES.get(0), "new content"));

		String mergeCommitId = doMerge();

		boolean result = git.revertMerge(DEVELOP_BRANCH, MASTER_BRANCH, commonAncestorCommitId, mergeCommitId);

		assertThat(result).isFalse();
	}

	@Test
	public void testRevertFailedMergeSourceParentIsNotLastSourceCommit() throws IOException {
		String mergeCommitId = doMerge();

		commit(git, DEVELOP_BRANCH, "Updating file", content(TXT_FILES.get(0), "new content"));

		boolean result = git.revertMerge(DEVELOP_BRANCH, MASTER_BRANCH, commonAncestorCommitId, mergeCommitId);

		assertThat(result).isFalse();
	}

	@Test
	public void testRevertSucceeded() throws IOException {
		String mergeCommitId = doMerge();

		boolean result = git.revertMerge(DEVELOP_BRANCH, MASTER_BRANCH, commonAncestorCommitId, mergeCommitId);

		assertThat(result).isTrue();
	}

	private String doMerge() throws IOException {
		git.merge(DEVELOP_BRANCH, MASTER_BRANCH, true);

		return git.getLastCommit(MASTER_BRANCH).getName();
	}
}
