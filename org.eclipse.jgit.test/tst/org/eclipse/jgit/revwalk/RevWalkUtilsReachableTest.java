/*
 * Copyright (C) 2014, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.junit.Test;

public class RevWalkUtilsReachableTest extends RevWalkTestCase {

	@Test
	public void oneCommit() throws Exception {
		RevCommit a = commit();
		Ref branchA = branch("a", a);

		assertContains(a, asList(branchA));
	}

	@Test
	public void twoCommits() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		branch("a", a);
		Ref branchB = branch("b", b);

		assertContains(b, asList(branchB));
	}

	@Test
	public void multipleBranches() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);
		branch("a", a);
		Ref branchB = branch("b", b);
		Ref branchB2 = branch("b2", b);

		assertContains(b, asList(branchB, branchB2));
	}

	@Test
	public void withMerge() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit();
		RevCommit c = commit(a, b);
		Ref branchA = branch("a", a);
		Ref branchB = branch("b", b);
		Ref branchC = branch("c", c);

		assertContains(a, asList(branchA, branchC));
		assertContains(b, asList(branchB, branchC));
	}

	@Test
	public void withCommitLoadedByDifferentRevWalk() throws Exception {
		RevCommit a = commit();
		Ref branchA = branch("a", a);

		try (RevWalk walk = new RevWalk(db)) {
			RevCommit parsedCommit = walk.parseCommit(a.getId());
			assertContains(parsedCommit, asList(branchA));
		}
	}

	@Test
	public void findBranchesReachableManyTimes() throws Exception {
		/*
		 *  a   b
		 *  |   |
		 *  c   d
		 *      | \
		 *      f  e
		 *      | /
		 *      g
		 */
		RevCommit a = commit();
		RevCommit b = commit();
		RevCommit c = commit(a);
		RevCommit d = commit(b);
		RevCommit f = commit(d);
		RevCommit e = commit(d);
		RevCommit g = commit(f, e);
		Ref branchA = branch("a", a);
		Ref branchB = branch("b", b);
		Ref branchC = branch("c", c);
		Ref branchD = branch("d", d);
		Ref branchE = branch("e", e);
		Ref branchF = branch("f", f);
		Ref branchG = branch("g", g);

		assertContains(a, asList(branchA, branchC));
		assertContains(b, asList(branchB, branchD, branchE, branchF, branchG));
		assertContains(c, asList(branchC));
		assertContains(d, asList(branchD, branchE, branchF, branchG));
		assertContains(e, asList(branchE, branchG));
		assertContains(f, asList(branchF, branchG));
		assertContains(g, asList(branchG));
	}

	private Ref branch(String name, RevCommit dst) throws Exception {
		return Git.wrap(db).branchCreate().setName(name)
				.setStartPoint(dst.name()).call();
	}

	private void assertContains(RevCommit commit, Collection<Ref> refsThatShouldContainCommit) throws Exception {
		Collection<Ref> allRefs = db.getRefDatabase().getRefs();
		Collection<Ref> sortedRefs = RefComparator.sort(allRefs);
		List<Ref> actual = RevWalkUtils.findBranchesReachableFrom(commit,
				rw, sortedRefs);
		assertEquals(refsThatShouldContainCommit, actual);
	}

}
