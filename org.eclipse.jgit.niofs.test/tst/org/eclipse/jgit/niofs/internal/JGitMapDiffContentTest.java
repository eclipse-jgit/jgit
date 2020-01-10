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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class JGitMapDiffContentTest extends BaseTest {

	private Git git;

	private static final String MASTER_BRANCH = "master";

	private static final List<String> TXT_FILES = Stream.of("file0", "file1", "file2", "file3", "file4")
			.collect(Collectors.toList());

	private static final String[] COMMON_TXT_LINES = { "Line1", "Line2", "Line3", "Line4" };

	@Before
	public void setup() throws IOException {
		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, "source/source.git");

		git = new CreateRepository(gitSource).execute().get();

		commit(git, MASTER_BRANCH, "Adding files into master",
				content(TXT_FILES.get(0), multiline(TXT_FILES.get(0), COMMON_TXT_LINES)),
				content(TXT_FILES.get(1), multiline(TXT_FILES.get(1), COMMON_TXT_LINES)),
				content(TXT_FILES.get(2), multiline(TXT_FILES.get(2), COMMON_TXT_LINES)));
	}

	@Test
	public void testNoDiffOnlyOneCommit() throws IOException {
		Map<String, File> content = git.mapDiffContent(MASTER_BRANCH,
				git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());

		assertThat(content).isEmpty();
	}

	@Test
	public void testHasContent() throws IOException {
		commit(git, MASTER_BRANCH, "Adding file into master",
				content(TXT_FILES.get(4), multiline(TXT_FILES.get(4), COMMON_TXT_LINES)));

		Map<String, File> contents = git.mapDiffContent(MASTER_BRANCH,
				git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());

		assertThat(contents).isNotEmpty();
		assertThat(contents).hasSize(1);
	}

	@Test
	public void testHasContents() throws IOException {
		commit(git, MASTER_BRANCH, "Adding files into master",
				content(TXT_FILES.get(3), multiline(TXT_FILES.get(3), COMMON_TXT_LINES)),
				content(TXT_FILES.get(4), multiline(TXT_FILES.get(4), COMMON_TXT_LINES)));

		Map<String, File> contents = git.mapDiffContent(MASTER_BRANCH,
				git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());

		assertThat(contents).isNotEmpty();
		assertThat(contents).hasSize(2);
	}

	@Test
	public void testHasDeleteContents() throws IOException {
		new Commit(git, MASTER_BRANCH, "name", "name@example.com", "Removing file", null, null, false,
				new HashMap<String, File>() {
					{
						put(TXT_FILES.get(0), null);
					}
				}).execute();

		new Commit(git, MASTER_BRANCH, "name", "name@example.com", "Removing file", null, null, false,
				new HashMap<String, File>() {
					{
						put(TXT_FILES.get(1), null);
					}
				}).execute();

		Map<String, File> contents = git.mapDiffContent(MASTER_BRANCH,
				git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());

		assertThat(contents).isNotEmpty();
		assertThat(contents).hasSize(2);
		contents.values().forEach(v -> assertThat(v).isNull());
	}

	@Test
	public void testWithManyCommitsOneFile() throws IOException {
		commit(git, MASTER_BRANCH, "Updating a file", content(TXT_FILES.get(0), "update 1"));

		commit(git, MASTER_BRANCH, "Updating a file", content(TXT_FILES.get(0), "update 2"));

		commit(git, MASTER_BRANCH, "Updating a file", content(TXT_FILES.get(0), "update 3"));

		Map<String, File> contents = git.mapDiffContent(MASTER_BRANCH,
				git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());

		assertThat(contents).isNotEmpty();
		assertThat(contents).hasSize(1);
	}

	@Test
	public void testWithMiddleCommits() throws IOException {
		commit(git, MASTER_BRANCH, "Updating a file", content(TXT_FILES.get(0), "update 1"));

		RevCommit startCommit = git.getLastCommit(MASTER_BRANCH);

		commit(git, MASTER_BRANCH, "Adding files into master",
				content(TXT_FILES.get(3), multiline(TXT_FILES.get(3), COMMON_TXT_LINES)),
				content(TXT_FILES.get(4), multiline(TXT_FILES.get(4), COMMON_TXT_LINES)));

		new Commit(git, MASTER_BRANCH, "name", "name@example.com", "Removing file", null, null, false,
				new HashMap<String, File>() {
					{
						put(TXT_FILES.get(1), null);
					}
				}).execute();

		RevCommit endCommit = git.getLastCommit(MASTER_BRANCH);

		commit(git, MASTER_BRANCH, "Updating a file", content(TXT_FILES.get(0), "update 3"));

		Map<String, File> contents = git.mapDiffContent(MASTER_BRANCH, startCommit.getName(), endCommit.getName());

		assertThat(contents).isNotEmpty();
		assertThat(contents).hasSize(3);
	}

	@Test(expected = GitException.class)
	public void testWithWrongBranchName() throws IOException {
		git.mapDiffContent("wrong-branch-name", git.getFirstCommit(git.getRef(MASTER_BRANCH)).getName(),
				git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());
	}

	@Test(expected = GitException.class)
	public void testWithInvalidCommit() throws IOException {
		git.mapDiffContent(MASTER_BRANCH, "invalid-commit-id", git.getLastCommit(git.getRef(MASTER_BRANCH)).getName());
	}
}