/*
 * Copyright (C) 2014, Robin Stocker <robin@nibor.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
