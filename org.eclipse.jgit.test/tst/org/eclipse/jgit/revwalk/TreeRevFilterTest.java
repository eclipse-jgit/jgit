/*
 * Copyright (C) 2014, Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import org.eclipse.jgit.revwalk.filter.OrRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.revwalk.filter.SkipRevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class TreeRevFilterTest extends RevWalkTestCase {
	private RevFilter treeRevFilter(String path) {
		return new TreeRevFilter(rw, treeFilter(path));
	}

	private static TreeFilter treeFilter(String path) {
		return AndTreeFilter.create(
				PathFilterGroup.createFromStrings(Collections.singleton(path)),
				TreeFilter.ANY_DIFF);
	}

	@Test
	public void testStringOfPearls_FilePath1()
			throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		rw.setRevFilter(treeRevFilter("d/f"));
		markStart(c);

		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		assertCommit(a, rw.next()); // b was skipped
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath2() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter("d/f"));
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_DirPath2() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		rw.setRevFilter(treeRevFilter("d"));
		markStart(d);

		// d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testStringOfPearls_FilePath3() throws Exception {
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);
		RevCommit d = commit(tree(file("d/f", blob("b"))), c);
		RevCommit e = commit(tree(file("d/f", blob("b"))), d);
		RevCommit f = commit(tree(file("d/f", blob("b"))), e);
		RevCommit g = commit(tree(file("d/f", blob("b"))), f);
		RevCommit h = commit(tree(file("d/f", blob("b"))), g);
		RevCommit i = commit(tree(file("d/f", blob("c"))), h);
		rw.setRevFilter(treeRevFilter("d/f"));
		markStart(i);

		assertCommit(i, rw.next());
		assertEquals(1, i.getParentCount());
		assertCommit(h, i.getParent(0));

		// h..d was skipped
		assertCommit(c, rw.next());
		assertEquals(1, c.getParentCount());
		assertCommit(b, c.getParent(0));

		// b was skipped
		assertCommit(a, rw.next());
		assertEquals(0, a.getParentCount());
		assertNull(rw.next());
	}

	@Test
	public void testPathFilterOrOtherFilter() throws Exception {
		RevFilter pathFilter = treeRevFilter("d/f");
		RevFilter skipFilter = SkipRevFilter.create(1);
		RevFilter orFilter = OrRevFilter.create(skipFilter, pathFilter);

		RevCommit a = parseBody(commit(5, tree(file("d/f", blob("a")))));
		RevCommit b = parseBody(commit(5, tree(file("d/f", blob("a"))), a));
		RevCommit c = parseBody(commit(5, tree(file("d/f", blob("b"))), b));

		// Path filter matches c, a.
		rw.setRevFilter(pathFilter);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(a, rw.next());

		// Skip filter matches b, a.
		rw.reset();
		rw.setRevFilter(skipFilter);
		markStart(c);
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());

		// (Path OR Skip) matches c, b, a.
		rw.reset();
		rw.setRevFilter(orFilter);
		markStart(c);
		assertCommit(c, rw.next());
		assertCommit(b, rw.next());
		assertCommit(a, rw.next());
	}

	@Test
	public void testDirFilter_MergeNonOverlappingCommits() throws Exception {
		/**
		 * v0 introduces some files and v1 other files (no overlap)
		 *
		 * <pre>
		 *   merge
		 *    |  \
		 *    x0  v1
		 *    |
		 *    v0
		 * </pre>
		 *
		 * Comparing trees between "merge" and "v1" shows only additions (the
		 * files that "merge" got from "x0")
		 *
		 * Comparing trees between "merge" and "x0" show only additions (the
		 * files that "merge" got from "v1")
		 */
		RevCommit v0 = commit(tree(file("a/b/README", blob("readme"))));
		RevCommit x0 = commit(tree(file("a/b/README", blob("readme"))), v0);

		RevCommit v1 = commit(tree(
				file("a/b/LICENSE", blob("license")),
				file("a/b/HACKING", blob("hacking"))));

		RevCommit merge = commit(tree(file("a/b/README", blob("readme")),
				file("a/b/LICENSE", blob("license")),
				file("a/b/HACKING", blob("hacking"))), x0, v1);

		rw.setTreeFilter(AndTreeFilter.create(PathFilter.create("a/b"),
				TreeFilter.ANY_DIFF));

		markStart(merge);
		assertCommit(merge, rw.next());
		assertCommit(v1, rw.next());
		assertCommit(v0, rw.next());
		assertCommit(null, rw.next());
	}

	@Test
	public void testDirFilter_MergeOneParentDoesntContribute() throws Exception {
		RevCommit v0 = commit(tree(
				file("a/b/README", blob("readme")),
				file("a/b/LICENSE", blob("license")),
				file("a/b/HACKING", blob("hacking"))));

		RevCommit v1 = commit(tree(
				file("a/b/README", blob("readme")),
				file("a/b/LICENSE", blob("license"))));

		RevCommit merge = commit(tree(file("a/b/README", blob("readme")),
				file("a/b/LICENSE", blob("license")),
				file("a/b/HACKING", blob("hacking"))), v0, v1);

		rw.setTreeFilter(AndTreeFilter.create(PathFilter.create("a/b"),
				TreeFilter.ANY_DIFF));

		markStart(merge);

		// Skip "merge". It doesn't contribute anything new in "a/b"
		// Skip "v1". "merge" has everything in "v1" plus additions (coming from
		// the other parent), so "v1" is not contributing in the "a/b" history
		assertCommit(v0, rw.next());
		assertCommit(null, rw.next());
	}
}
