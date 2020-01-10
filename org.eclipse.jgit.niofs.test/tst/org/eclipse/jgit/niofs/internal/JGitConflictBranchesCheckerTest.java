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

import static org.assertj.core.api.Assertions.assertThat;

public class JGitConflictBranchesCheckerTest extends AbstractTestInfra {

	private Git git;

	private static final String MASTER_BRANCH = "master";
	private static final String DEVELOP_BRANCH = "develop";

	private static final List<String> TXT_FILES = Stream.of("file0", "file1", "file2", "file3", "file4")
			.collect(Collectors.toList());

	private static final String[] COMMON_TXT_LINES = { "Line1", "Line2", "Line3", "Line4" };

	@Before
	public void setup() throws IOException {
		final File parentFolder = createTempDirectory();

		final File gitSource = new File(parentFolder, "source/source.git");

		git = new CreateRepository(gitSource).execute().get();

		commit(git, MASTER_BRANCH, "Adding files into master",
				content(TXT_FILES.get(0), multiline(TXT_FILES.get(0), COMMON_TXT_LINES)),
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), COMMON_TXT_LINES)),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), COMMON_TXT_LINES)));

		new CreateBranch((GitImpl) git, MASTER_BRANCH, DEVELOP_BRANCH).execute();
	}

	@Test
	public void testReportConflictsAllFiles() throws IOException {
		commit(git, DEVELOP_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2ChangedDev", "Line3", "Line4")),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), "Line1", "Line2ChangedDev", "Line3", "Line4")));

		commit(git, MASTER_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2ChangedMaster", "Line3", "Line4")),
				content(TXT_FILES.get(2),
						multiline(TXT_FILES.get(2), "Line1", "Line2ChangedMaster", "Line3", "Line4")));

		List<String> conflicts = git.conflictBranchesChecker(MASTER_BRANCH, DEVELOP_BRANCH);

		assertThat(conflicts).isNotEmpty();
		assertThat(conflicts).hasSize(2);
		assertThat(conflicts.get(0)).isEqualTo(TXT_FILES.get(1));
		assertThat(conflicts.get(1)).isEqualTo(TXT_FILES.get(2));
	}

	@Test
	public void testReportConflictsSomeFiles() throws IOException {
		commit(git, DEVELOP_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2ChangedDev", "Line3", "Line4")),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), "Line1", "Line2ChangedDev", "Line3", "Line4")));

		commit(git, MASTER_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2ChangedMaster", "Line3", "Line4")),
				content(TXT_FILES.get(2),
						multiline(TXT_FILES.get(2), "Line1", "Line2", "Line3", "Line4ChangedMaster")));

		List<String> conflicts = git.conflictBranchesChecker(MASTER_BRANCH, DEVELOP_BRANCH);

		assertThat(conflicts).isNotEmpty();
		assertThat(conflicts).hasSize(1);
		assertThat(conflicts.get(0)).isEqualTo(TXT_FILES.get(1));
	}

	@Test
	public void testReportConflictsNoFiles() throws IOException {
		commit(git, DEVELOP_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2ChangedDev", "Line3", "Line4")),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), "Line1", "Line2ChangedDev", "Line3", "Line4")));

		commit(git, MASTER_BRANCH, "Updating files",
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), "Line1", "Line2", "Line3", "Line4ChangedMaster")),
				content(TXT_FILES.get(2),
						multiline(TXT_FILES.get(2), "Line1", "Line2", "Line3", "Line4ChangedMaster")));

		List<String> conflicts = git.conflictBranchesChecker(MASTER_BRANCH, DEVELOP_BRANCH);

		assertThat(conflicts).isEmpty();
	}

	@Test(expected = GitException.class)
	public void testInvalidBranch() {
		git.conflictBranchesChecker(MASTER_BRANCH, "invalid-branch");
	}
}
