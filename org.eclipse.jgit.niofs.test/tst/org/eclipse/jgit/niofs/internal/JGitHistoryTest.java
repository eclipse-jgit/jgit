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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.niofs.internal.op.commands.ListCommits;
import org.eclipse.jgit.niofs.internal.op.model.CommitHistory;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.op.model.MoveCommitContent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class JGitHistoryTest extends BaseTest {

	private Git git;

	@Before
	public void setup() throws IOException {
		final File tmpDir = util.createTempDirectory();
		final File repoDir = new File(tmpDir, "test-repo.git");
		git = new CreateRepository(repoDir).execute()
				.orElseThrow(() -> new IllegalStateException("Unable to create git repo for tests."));

		commit(git, "master", "create files", content("non-moving.txt", multiline("a", "b", "c")),
				content("moving.txt", multiline("1", "2", "3")));
		moveCommit(singleMove("moving.txt", "moving1.txt"), "rename moving file");
		commit(git, "master", "change content, no moves", content("non-moving.txt", multiline("a", "b", "d")),
				content("moving1.txt", multiline("1", "2", "4")));
		moveCommit(singleMove("moving1.txt", "dir/moving2.txt"), "move moving file to new dir");
		commit(git, "master", "simulate checkout old version", content("moving1.txt", multiline("1", "2", "4")));
	}

	private Map<String, String> singleMove(String from, String to) {
		Map<String, String> moves = new HashMap<>();
		moves.put(from, to);
		return moves;
	}

	private void moveCommit(Map<String, String> moves, String message) {
		git.commit("master", new CommitInfo(null, "name", "name@example.com", message, null, null), false, null,
				new MoveCommitContent(moves));
	}

	@Test
	public void listCommitsForUnmovedFile() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "non-moving.txt").execute();
		assertEquals("non-moving.txt", history.getTrackedFilePath());
		assertEquals(2, history.getCommits().size());
		assertEquals("/non-moving.txt", history.trackedFileNameChangeFor(history.getCommits().get(0).getId()));
		assertEquals("/non-moving.txt", history.trackedFileNameChangeFor(history.getCommits().get(1).getId()));
	}

	@Test
	public void listCommitsForMovedFile() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "dir/moving2.txt").execute();
		assertEquals("dir/moving2.txt", history.getTrackedFilePath());
		assertEquals(4, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("move moving file to new dir", commit0.getFullMessage());
		assertEquals("/dir/moving2.txt", oPath0);

		final RevCommit commit1 = history.getCommits().get(1);
		String oPath1 = history.trackedFileNameChangeFor(commit1.getId());
		assertEquals("change content, no moves", commit1.getFullMessage());
		assertEquals("/moving1.txt", oPath1);

		final RevCommit commit2 = history.getCommits().get(2);
		String oPath2 = history.trackedFileNameChangeFor(commit2.getId());
		assertEquals("rename moving file", commit2.getFullMessage());
		assertEquals("/moving1.txt", oPath2);

		final RevCommit commit3 = history.getCommits().get(3);
		String oPath3 = history.trackedFileNameChangeFor(commit3.getId());
		assertEquals("create files", commit3.getFullMessage());
		assertEquals("/moving.txt", oPath3);
	}

	@Test
	public void listCommitsForRestoredFile() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "moving1.txt").execute();
		assertEquals("moving1.txt", history.getTrackedFilePath());
		assertEquals(4, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("simulate checkout old version", commit0.getFullMessage());
		assertEquals("/moving1.txt", oPath0);

		final RevCommit commit1 = history.getCommits().get(1);
		String oPath1 = history.trackedFileNameChangeFor(commit1.getId());
		assertEquals("change content, no moves", commit1.getFullMessage());
		assertEquals("/moving1.txt", oPath1);

		final RevCommit commit2 = history.getCommits().get(2);
		String oPath2 = history.trackedFileNameChangeFor(commit2.getId());
		assertEquals("rename moving file", commit2.getFullMessage());
		assertEquals("/moving1.txt", oPath2);

		final RevCommit commit3 = history.getCommits().get(3);
		String oPath3 = history.trackedFileNameChangeFor(commit3.getId());
		assertEquals("create files", commit3.getFullMessage());
		assertEquals("/moving.txt", oPath3);
	}

	@Test
	public void listCommitsOnDirectory() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "dir").execute();
		assertEquals("dir", history.getTrackedFilePath());
		assertEquals(1, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("move moving file to new dir", commit0.getFullMessage());
		assertEquals("/dir", oPath0);
	}

	@Test
	public void listCommitsOnRootDirectoryViaAbsolute() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "/").execute();
		assertEquals("/", history.getTrackedFilePath());
		assertEquals(5, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("simulate checkout old version", commit0.getFullMessage());
		assertEquals("/", oPath0);
	}

	@Test
	public void listCommitsOnRootDirectoryViaNull() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), null).execute();
		assertEquals("/", history.getTrackedFilePath());
		assertEquals(5, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("simulate checkout old version", commit0.getFullMessage());
		assertEquals("/", oPath0);
	}

	@Test
	public void listCommitsOnRootDirectoryViaEmpty() throws Exception {
		final CommitHistory history = new ListCommits(git, git.getRef("master"), "").execute();
		assertEquals("/", history.getTrackedFilePath());
		assertEquals(5, history.getCommits().size());

		final RevCommit commit0 = history.getCommits().get(0);
		String oPath0 = history.trackedFileNameChangeFor(commit0.getId());
		assertEquals("simulate checkout old version", commit0.getFullMessage());
		assertEquals("/", oPath0);
	}
}
