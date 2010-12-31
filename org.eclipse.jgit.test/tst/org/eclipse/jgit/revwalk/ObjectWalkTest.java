/*
 * Copyright (C) 2009-2010, Google Inc.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileTreeEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Tree;
import org.junit.Test;

public class ObjectWalkTest extends RevWalkTestCase {
	protected ObjectWalk objw;

	@Override
	protected RevWalk createRevWalk() {
		return objw = new ObjectWalk(db);
	}

	@Test
	public void testNoCommits() throws Exception {
		assertNull(objw.next());
		assertNull(objw.nextObject());
	}

	@Test
	public void testTwoCommitsEmptyTree() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		markStart(b);

		assertCommit(b, objw.next());
		assertCommit(a, objw.next());
		assertNull(objw.next());

		assertSame(tree(), objw.nextObject());
		assertNull(objw.nextObject());
	}

	@Test
	public void testOneCommitOneTreeTwoBlob() throws Exception {
		final RevBlob f0 = blob("0");
		final RevBlob f1 = blob("1");
		final RevTree t = tree(file("0", f0), file("1", f1), file("2", f1));
		final RevCommit a = commit(t);
		markStart(a);

		assertCommit(a, objw.next());
		assertNull(objw.next());

		assertSame(t, objw.nextObject());
		assertSame(f0, objw.nextObject());
		assertSame(f1, objw.nextObject());
		assertNull(objw.nextObject());
	}

	@Test
	public void testTwoCommitTwoTreeTwoBlob() throws Exception {
		final RevBlob f0 = blob("0");
		final RevBlob f1 = blob("1");
		final RevBlob f2 = blob("0v2");
		final RevTree ta = tree(file("0", f0), file("1", f1), file("2", f1));
		final RevTree tb = tree(file("0", f2), file("1", f1), file("2", f1));
		final RevCommit a = commit(ta);
		final RevCommit b = commit(tb, a);
		markStart(b);

		assertCommit(b, objw.next());
		assertCommit(a, objw.next());
		assertNull(objw.next());

		assertSame(tb, objw.nextObject());
		assertSame(f2, objw.nextObject());
		assertSame(f1, objw.nextObject());

		assertSame(ta, objw.nextObject());
		assertSame(f0, objw.nextObject());

		assertNull(objw.nextObject());
	}

	@Test
	public void testTwoCommitDeepTree1() throws Exception {
		final RevBlob f0 = blob("0");
		final RevBlob f1 = blob("0v2");
		final RevTree ta = tree(file("a/b/0", f0));
		final RevTree tb = tree(file("a/b/1", f1));
		final RevCommit a = commit(ta);
		final RevCommit b = commit(tb, a);
		markStart(b);

		assertCommit(b, objw.next());
		assertCommit(a, objw.next());
		assertNull(objw.next());

		assertSame(tb, objw.nextObject());
		assertSame(get(tb, "a"), objw.nextObject());
		assertSame(get(tb, "a/b"), objw.nextObject());
		assertSame(f1, objw.nextObject());

		assertSame(ta, objw.nextObject());
		assertSame(get(ta, "a"), objw.nextObject());
		assertSame(get(ta, "a/b"), objw.nextObject());
		assertSame(f0, objw.nextObject());

		assertNull(objw.nextObject());
	}

	@Test
	public void testTwoCommitDeepTree2() throws Exception {
		final RevBlob f1 = blob("1");
		final RevTree ta = tree(file("a/b/0", f1), file("a/c/q", f1));
		final RevTree tb = tree(file("a/b/1", f1), file("a/c/q", f1));
		final RevCommit a = commit(ta);
		final RevCommit b = commit(tb, a);
		markStart(b);

		assertCommit(b, objw.next());
		assertCommit(a, objw.next());
		assertNull(objw.next());

		assertSame(tb, objw.nextObject());
		assertSame(get(tb, "a"), objw.nextObject());
		assertSame(get(tb, "a/b"), objw.nextObject());
		assertSame(f1, objw.nextObject());
		assertSame(get(tb, "a/c"), objw.nextObject());

		assertSame(ta, objw.nextObject());
		assertSame(get(ta, "a"), objw.nextObject());
		assertSame(get(ta, "a/b"), objw.nextObject());

		assertNull(objw.nextObject());
	}

	@Test
	public void testCull() throws Exception {
		final RevBlob f1 = blob("1");
		final RevBlob f2 = blob("2");
		final RevBlob f3 = blob("3");
		final RevBlob f4 = blob("4");

		final RevTree ta = tree(file("a/1", f1), file("c/3", f3));
		final RevCommit a = commit(ta);

		final RevTree tb = tree(file("a/1", f2), file("c/3", f3));
		final RevCommit b1 = commit(tb, a);
		final RevCommit b2 = commit(tb, b1);

		final RevTree tc = tree(file("a/1", f4));
		final RevCommit c1 = commit(tc, a);
		final RevCommit c2 = commit(tc, c1);

		markStart(b2);
		markUninteresting(c2);

		assertCommit(b2, objw.next());
		assertCommit(b1, objw.next());
		assertNull(objw.next());

		assertTrue(a.has(RevFlag.UNINTERESTING));
		assertTrue(ta.has(RevFlag.UNINTERESTING));
		assertTrue(f1.has(RevFlag.UNINTERESTING));
		assertTrue(f3.has(RevFlag.UNINTERESTING));

		assertSame(tb, objw.nextObject());
		assertSame(get(tb, "a"), objw.nextObject());
		assertSame(f2, objw.nextObject());
		assertNull(objw.nextObject());
	}

	@Test
	public void testEmptyTreeCorruption() throws Exception {
		ObjectId bId = ObjectId
				.fromString("abbbfafe3129f85747aba7bfac992af77134c607");
		final RevTree tree_root, tree_A, tree_AB;
		final RevCommit b;
		{
			Tree root = new Tree(db);
			Tree A = root.addTree("A");
			FileTreeEntry B = root.addFile("B");
			B.setId(bId);

			Tree A_A = A.addTree("A");
			Tree A_B = A.addTree("B");

			final ObjectInserter inserter = db.newObjectInserter();
			try {
				A_A.setId(inserter.insert(Constants.OBJ_TREE, A_A.format()));
				A_B.setId(inserter.insert(Constants.OBJ_TREE, A_B.format()));
				A.setId(inserter.insert(Constants.OBJ_TREE, A.format()));
				root.setId(inserter.insert(Constants.OBJ_TREE, root.format()));
				inserter.flush();
			} finally {
				inserter.release();
			}

			tree_root = rw.parseTree(root.getId());
			tree_A = rw.parseTree(A.getId());
			tree_AB = rw.parseTree(A_A.getId());
			assertSame(tree_AB, rw.parseTree(A_B.getId()));
			b = commit(rw.parseTree(root.getId()));
		}

		markStart(b);

		assertCommit(b, objw.next());
		assertNull(objw.next());

		assertSame(tree_root, objw.nextObject());
		assertSame(tree_A, objw.nextObject());
		assertSame(tree_AB, objw.nextObject());
		assertSame(rw.lookupBlob(bId), objw.nextObject());
		assertNull(objw.nextObject());
	}
}
