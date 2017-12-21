/*
 * Copyright (C) 2011, GEBIT Solutions
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

import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RevWalkFollowFilterTest extends RevWalkTestCase {

	private static class DiffCollector extends RenameCallback {
		List<DiffEntry> diffs = new ArrayList<>();

		@Override
		public void renamed(DiffEntry diff) {
			diffs.add(diff);
		}
	}

	private DiffCollector diffCollector;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		diffCollector = new DiffCollector();
	}

	protected FollowFilter follow(final String followPath) {
		FollowFilter followFilter =
			FollowFilter.create(followPath, new Config().get(DiffConfig.KEY));
		followFilter.setRenameCallback(diffCollector);
		rw.setTreeFilter(followFilter);
		return followFilter;
	}

	@Test
	public void testNoRename() throws Exception {
		final RevCommit a = commit(tree(file("0", blob("0"))));
		follow("0");
		markStart(a);
		assertCommit(a, rw.next());
		assertNull(rw.next());

		assertNoRenames();
	}

	@Test
	public void testSingleRename() throws Exception {
		final RevCommit a = commit(tree(file("a", blob("A"))));

		// rename a to b
		CommitBuilder commitBuilder = commitBuilder().parent(a)
				.add("b", blob("A")).rm("a");
		RevCommit renameCommit = commitBuilder.create();

		follow("b");
		markStart(renameCommit);
		assertCommit(renameCommit, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());

		assertRenames("a->b");
	}

	@Test
	public void testMultiRename() throws Exception {
		final String contents = "A";
		final RevCommit a = commit(tree(file("a", blob(contents))));

		// rename a to b
		CommitBuilder commitBuilder = commitBuilder().parent(a)
				.add("b", blob(contents)).rm("a");
		RevCommit renameCommit1 = commitBuilder.create();

		// rename b to c
		commitBuilder = commitBuilder().parent(renameCommit1)
				.add("c", blob(contents)).rm("b");
		RevCommit renameCommit2 = commitBuilder.create();

		// rename c to a
		commitBuilder = commitBuilder().parent(renameCommit2)
				.add("a", blob(contents)).rm("c");
		RevCommit renameCommit3 = commitBuilder.create();

		follow("a");
		markStart(renameCommit3);
		assertCommit(renameCommit3, rw.next());
		assertCommit(renameCommit2, rw.next());
		assertCommit(renameCommit1, rw.next());
		assertCommit(a, rw.next());
		assertNull(rw.next());

		assertRenames("c->a", "b->c", "a->b");
	}

	/**
	 * Assert which renames should have happened, in traversal order.
	 *
	 * @param expectedRenames
	 *            the rename specs, each one in the form "srcPath-&gt;destPath"
	 */
	protected void assertRenames(String... expectedRenames) {
		Assert.assertEquals("Unexpected number of renames. Expected: " +
				expectedRenames.length + ", actual: " + diffCollector.diffs.size(),
				expectedRenames.length, diffCollector.diffs.size());

		for (int i = 0; i < expectedRenames.length; i++) {
			DiffEntry diff = diffCollector.diffs.get(i);
			Assert.assertNotNull(diff);
			String[] split = expectedRenames[i].split("->");

			Assert.assertNotNull(split);
			Assert.assertEquals(2, split.length);
			String src = split[0];
			String target = split[1];

			Assert.assertEquals(src, diff.getOldPath());
			Assert.assertEquals(target, diff.getNewPath());
		}
	}

	protected void assertNoRenames() {
		Assert.assertEquals("Found unexpected rename/copy diff", 0,
				diffCollector.diffs.size());
	}

}
