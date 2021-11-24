/*
 * Copyright (C) 2021, kylezhao <kylezhao@tencent.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class RevListTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testWithParentsFlag() throws Exception {
		List<RevCommit> commits = createCommitsForParentsFlag(git);
		String result = toString(
				execute("git rev-list HEAD --parents -- Test.txt"));

		String expect = toString(
				commits.get(3).name() + ' ' + commits.get(1).name(),
				commits.get(1).name());

		assertEquals(expect, result);
	}

	@Test
	public void testWithoutParentsFlag() throws Exception {
		List<RevCommit> commits = createCommitsForParentsFlag(git);
		String result = toString(execute("git rev-list HEAD -- Test.txt"));

		String expect = toString(commits.get(3).name(), commits.get(1).name());

		assertEquals(expect, result);
	}

	private List<RevCommit> createCommitsForParentsFlag(Git repo)
			throws Exception {
		List<RevCommit> commits = new ArrayList<>();
		writeTrashFile("Test1.txt", "Hello world");
		repo.add().addFilepattern("Test1.txt").call();
		commits.add(repo.commit().setMessage("commit#0").call());
		writeTrashFile("Test.txt", "Hello world!");
		repo.add().addFilepattern("Test.txt").call();
		commits.add(repo.commit().setMessage("commit#1").call());
		writeTrashFile("Test1.txt", "Hello world!!");
		repo.add().addFilepattern("Test1.txt").call();
		commits.add(repo.commit().setMessage("commit#2").call());
		writeTrashFile("Test.txt", "Hello world!!!");
		repo.add().addFilepattern("Test.txt").call();
		commits.add(repo.commit().setMessage("commit#3").call());
		return commits;
	}
}
