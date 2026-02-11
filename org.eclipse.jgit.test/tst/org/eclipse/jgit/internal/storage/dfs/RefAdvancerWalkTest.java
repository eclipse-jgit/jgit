/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class RefAdvancerWalkTest {
	private static final String MAIN = "refs/heads/main";

	private InMemoryRepository db;

	private TestRepository<InMemoryRepository> git;

	private Set<RevCommit> commitsInMidx;

	private Map<String, RevCommit> commitByLetter;

	@Before
	public void setUp() throws Exception {
		db = new InMemoryRepository(
				new DfsRepositoryDescription("ref advance"));
		git = new TestRepository<>(db);
		setupRepo();
	}

	/**
	 * <pre>
	 *  tipMergeBeforeMidx -> H
	 *                        |
	 *                        |
	 *                        F  G <- tipStraight
	 *                        |\ |
	 *                        | \|
	 * tipMergeMidxCommits -> D  E
	 *                        |\ |
	 *                      +--------+
	 *                      | B  C <-- tipIn
	 *                      | | /    |
	 *                      | |/     |
	 *                      | A      |
	 *                      |    midx|
	 *                      +--------+
	 * </pre>
	 */
	private void setupRepo() throws Exception {
		RevCommit a = commitToMain();
		RevCommit b = commitToMain();
		RevCommit c = commit(a);
		RevCommit d = commitToMain(c);
		RevCommit e = commit(c);
		/* unused */ commitToMain();
		RevCommit g = commit(e);
		RevCommit h = commitToMain();

		commitsInMidx = new HashSet<>();
		commitsInMidx.add(a);
		commitsInMidx.add(b);
		commitsInMidx.add(c);

		commitByLetter = Map.of("a", a, "b", b, "c", c, "d", d, "e", e, "g", g,
				"h", h);

	}

	@Test
	public void singleWant_linearHistory() throws Exception {
		runTest("g", Set.of("c"));
	}

	@Test
	public void singleWant_alreadyInMidx() throws Exception {
		runTest("c", Set.of("c"));
	}

	@Test
	public void singleWant_mergeCommitsInMidx() throws Exception {
		runTest("d", Set.of("b", "c"));
	}

	@Test
	public void singleWant_mergeBeforeMidx() throws Exception {
		runTest("h", Set.of("b", "c"));
	}

	@Test
	public void manyWant_mergeBeforeMidx() throws Exception {
		runTest(Set.of("h", "c", "d", "g"), Set.of("b", "c"));
	}

	private void runTest(String want, Set<String> expectedTips)
			throws IOException {
		runTest(Set.of(want), expectedTips);
	}

	private void runTest(Set<String> want, Set<String> expectedTips)
			throws IOException {
		RefAdvancerWalk advancer = new RefAdvancerWalk(db,
				commitsInMidx::contains);
		List<ObjectId> wants = want.stream().map(commitByLetter::get)
				.collect(Collectors.toUnmodifiableList());
		Set<RevCommit> tipsInMidx = advancer.advance(wants);

		Set<RevCommit> expected = expectedTips.stream().map(commitByLetter::get)
				.collect(Collectors.toUnmodifiableSet());
		assertEquals(expected.size(), tipsInMidx.size());
		assertTrue(tipsInMidx.containsAll(expected));
	}

	private static int commitCounter = 0;

	private RevCommit commitToMain() throws Exception {
		int i = commitCounter++;
		return git.branch(MAIN).commit()
				.add("xx" + i, git.blob("content #" + i)).create();
	}

	private RevCommit commitToMain(RevCommit... extraParent) throws Exception {
		int i = commitCounter++;
		TestRepository<InMemoryRepository>.CommitBuilder commit = git
				.branch(MAIN).commit();
		for (RevCommit p : extraParent) {
			commit.parent(p);
		}

		return commit.add("xx" + i, git.blob("content #" + i)).create();
	}

	private RevCommit commit(RevCommit parent) throws Exception {
		int i = commitCounter++;
		return git.commit().parent(parent)
				.add("cc" + i, git.blob("out of main content #" + i)).create();
	}

}
