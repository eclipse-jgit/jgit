/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

package org.eclipse.jgit.subtree;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.junit.Test;

public class SubtreeSplitterTest extends SubtreeTestCase {

	/**
	 * <pre>
	 * A--B--E
	 *  \   /
	 *   -C-
	 *   /
	 *  D
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testAddSubtreeOnBranch() throws Exception {
		RevCommit A = commit(tree(file("a", blob("a"))));
		RevCommit B = commit(tree(file("a", blob("a'"))), A);
		RevCommit D = commit(tree(file("b", blob("b"))));
		RevCommit C = subtreeAdd("suba", A, D);
		rw.parseCommit(C);
		RevCommit E = commit(editTree(C.getTree(), file("a", blob("a'"))), B, C);

		SubtreeValidator sv = new SubtreeValidator(db, rw);
		sv.normal("A", A).setParents();
		sv.normal("B", B).setParents("A");
		sv.normal("C", C).setParents("A", "D").addSubtree("suba", "D");
		sv.subtree("D", D).setParents();
		sv.normal("E", E).setParents("B", "C").addSubtree("suba", "D");
		sv.setStart(E);
		sv.validate();
	}

	/**
	 * <ul>
	 * <li>A adds subtree D as 'suba'</li>
	 * <li>B deletes subtree 'suba'</li>
	 * </ul>
	 *
	 * <pre>
	 *     A--B--C
	 *    /
	 *   /
	 *  /
	 * D
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testDeleteSubtree() throws Exception {
		RevTree parentTree = rw.parseCommit(commit(tree(file("a", blob("a")))))
				.getTree();

		RevCommit D = commit(tree(file("b", blob("b"))));
		RevCommit A = commit(subtreeAdd("suba", commit(parentTree), D)
				.getTree(), D);
		RevCommit B = commit(parentTree, A);
		RevCommit C = commit(tree(file("a", blob("a''"))), B);

		SubtreeValidator sv = new SubtreeValidator(db, rw);
		sv.normal("A", A).setParents("D").addSubtree("suba", "D");
		sv.normal("B", B).setParents("A");
		sv.normal("C", C).setParents("B");
		sv.subtree("D", D).setParents();
		sv.setStart(C);
		sv.validate();
	}

	/**
	 * <ul>
	 * <li>A adds subtree D as 'suba'</li>
	 * <li>C deletes subtree 'suba'</li>
	 * </ul>
	 *
	 * <pre>
	 *     A--B--E
	 *    / \   /
	 *   /   -C-
	 *  /
	 * D
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testDeleteSubtreeOnBranch() throws Exception {
		RevCommit D = commit(tree(file("b", blob("b"))));
		RevCommit A = commit(
				subtreeAdd("suba", commit(tree(file("a", blob("a")))), D)
						.getTree(), D);
		RevCommit B = edit(A, false, file("a", blob("a'")));
		RevCommit C = commit(tree(file("b", blob("b"))), A);
		rw.parseCommit(C);
		RevCommit E = commit(editTree(C.getTree(), file("a", blob("a'"))), B, C);

		SubtreeValidator sv = new SubtreeValidator(db, rw);
		sv.normal("A", A).setParents("D").addSubtree("suba", "D");
		sv.normal("B", B).setParents("A").addSubtree("suba", "D");
		sv.normal("C", C).setParents("A");
		sv.subtree("D", D).setParents();
		sv.normal("E", E).setParents("B", "C");
		sv.setStart(E);
		sv.validate();
	}

	/**
	 *
	 * <p>
	 * Basic setup
	 * </p>
	 * <ul>
	 * <li>B subtree adds C as 'suba'</li>
	 * <li>B modifies subtree 'suba' (on super branch)</li>
	 * <li>D modifies subtree 'suba' (on subtree branch)</li>
	 * <li>F 'resets' subtree 'suba' back to state of C (on super branch)</li>
	 * </ul>
	 *
	 * <pre>
	 * A--B--E--F  super
	 *   /  /  /
	 *  C--D  /    sub
	 *   \   /
	 *    --/
	 * </pre>
	 *
	 * Test 1 - test split starting from B
	 *
	 * <pre>
	 *   A--B' super
	 *     /
	 *    G    sub
	 *   /
	 *  C      sub
	 * </pre>
	 *
	 * Test 2 - test split starting from E
	 *
	 * <pre>
	 *   A--B'--E' super
	 *     /   /
	 *    G---H    sub
	 *   /   /
	 *  C---D      sub
	 * </pre>
	 *
	 * Test 3 - test split starting from F
	 *
	 * <pre>
	 *   A--B'--E'--F' super
	 *     /   /   /
	 *    G---H   /    sub
	 *   /   /   /
	 *  C---D   /      sub
	 *   \     /
	 *    \---/
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testModifySubtreeAtAdd() throws Exception {
		RevCommit A = commit(tree(file("a", blob("a"))));
		RevCommit C = commit(tree(file("b1", blob("b1")),
				file("b2", blob("b2"))));
		RevCommit B = edit(subtreeAdd("suba", A, C), true,
				file("suba/b2", blob("b2'")));
		rw.parseCommit(B);
		RevCommit D = edit(commit(C.getTree(), C), true,
				file("b2", blob("b2''")));
		RevCommit E = edit(commit(B.getTree(), B, D), true,
				file("suba/b2", blob("b2'''")));
		RevCommit F = subtreeAdd("suba", E, C);

		SubtreeValidator sv = new SubtreeValidator(db, rw);
		sv.normal("A", A).setParents();
		sv.normal("B", B).setParents("A", "C").addSubtree("suba", "G");
		sv.rewritten("B'", "B").setParents("A", "G");
		sv.subtree("C", C).setParents();
		sv.split("G", "suba", B).setParents("C");

		// Test 1
		sv.setStart(B);
		sv.validate();

		// Test 2
		sv.reset();
		sv.subtree("D", D).setParents("C");
		sv.split("H", "suba", E).setParents("G", "D");
		sv.normal("E", E).setParents("B", "D").addSubtree("suba", "H");
		sv.rewritten("E'", "E").setParents("B'", "H");
		sv.setStart(E);
		sv.validate();

		// Test 3
		sv.reset();
		sv.normal("F", F).setParents("E", "C").addSubtree("suba", "C");
		sv.rewritten("F'", "F").setParents("E'", "C");
		sv.setStart(F);
		sv.validate();

	}

	/**
	 *
	 * <ul>
	 * <li>C subtree adds B as 'suba'</li>
	 * <li>D modifies subtree 'suba'</li>
	 * </ul>
	 *
	 * <pre>
	 * A--C--D  super
	 *   /
	 *  B  sub
	 * </pre>
	 *
	 * Result:
	 *
	 * <pre>
	 * A--C--D'  super
	 *   /  /
	 *  B--E  sub
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testModifySubtree() throws Exception {
		RevCommit A = commit(tree(file("a", blob("a"))));
		RevCommit B = commit(tree(file("b1", blob("b1")),
				file("b2", blob("b2"))));
		RevCommit C = subtreeAdd("suba", A, B);
		RevCommit D = edit(C, false, file("suba/b2", blob("b2'")));

		SubtreeValidator sv = new SubtreeValidator(db, rw).setStart(D);
		sv.normal("A", A).setParents();
		sv.subtree("B", B).setParents();
		sv.normal("C", C).setParents("A", "B").addSubtree("suba", "B");
		sv.normal("D", D).setParents("C").addSubtree("suba", "E");
		sv.split("E", "suba", D).setParents("B");
		sv.rewritten("D'", "D").setParents("C", "E");
		sv.validate();

	}

	/**
	 *
	 * <ul>
	 * <li>A adds suba/...</li>
	 * <li>B modifies suba/...</li>
	 * <li>C adds subb/...</li>
	 * <li>D modifies subb/...</li>
	 * <li>suba and subb are then split out by path.</li>
	 * </ul>
	 *
	 * <pre>
	 * A--B--C--D  super
	 * </pre>
	 *
	 * Result:
	 *
	 * <pre>
	 *   A'-B'-C'-D' super
	 *  /  /  /  /
	 * E--F  G--H
	 *
	 * suba  subb
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testSplitSubtreeByPath() throws Exception {
		RevCommit A = commit(tree(file("a", blob("a")),
				file("suba/a1,", blob("a1")), file("suba/a2", blob("a2"))));
		RevCommit B = edit(A, false, file("suba/a1", blob("a1'")));
		RevCommit C = edit(B, false, file("subb/b1", blob("b1")),
				file("subb/b2", blob("b2")));
		RevCommit D = edit(C, false, file("subb/b1", blob("b1'")));

		SubtreeValidator sv = new SubtreeValidator(db, rw).setSplitPaths("suba",
				"subb").setStart(D);
		sv.normal("A", A).setParents().addSubtree("suba", "E");
		sv.rewritten("A'", "A").setParents("E");
		sv.normal("B", B).setParents("A").addSubtree("suba", "F");
		sv.rewritten("B'", "B").setParents("A'", "F");
		sv.normal("C", C).setParents("B").addSubtree("suba", "F")
				.addSubtree("subb", "G");
		sv.rewritten("C'", "C").setParents("B'", "G");
		sv.normal("D", D).setParents("C").addSubtree("suba", "F")
				.addSubtree("subb", "H");
		sv.rewritten("D'", "D").setParents("C'", "H");
		sv.split("E", "suba", A).setParents();
		sv.split("F", "suba", B).setParents("E");
		sv.split("G", "subb", C).setParents();
		sv.split("H", "subb", D).setParents("G");
		sv.validate();
	}

	/**
	 * Test various permutations of modifying subtrees on branches.
	 *
	 * <p>
	 * Basic setup:
	 * </p>
	 *
	 * <pre>
	 *  A--B--C--E   super
	 *    / \   /
	 *   /   -D-     super
	 *  /
	 * F             subtree
	 * </pre>
	 *
	 * Test 1 - Modify only super project
	 * <ul>
	 * <li>C: a: a'</li>
	 * <li>D: a: a''</li>
	 * <li>E: a: a'''</li>
	 * </ul>
	 *
	 * <pre>
	 * Result matches setup.
	 * </pre>
	 *
	 * Test 2 - Modify one branch, merge point matches
	 * <ul>
	 * <li>C: a: a'</li>
	 * <li>D: suba/b2: b''</li>
	 * <li>E: suba/b2: b''</li>
	 * </ul>
	 *
	 * <pre>
	 *  A--B--C---E'  super
	 *    / \    /
	 *   /   D'-/     super
	 *  /   /
	 * F---G          subtree
	 * </pre>
	 *
	 * Test 3 - Modify one branch, branch point matches neither
	 * <ul>
	 * <li>C: a: a'</li>
	 * <li>D: suba/b: b''</li>
	 * <li>E: suba/b: b'''
	 * </ul>
	 *
	 * <pre>
	 *  A--B--C---E'
	 *    / \    /
	 *   /   D'-/
	 *  /   /  /
	 * F---G--H
	 * </pre>
	 *
	 * Test 4 - Modify both branches, merge point matches neither (this is
	 * harder to visualize with ASCII)
	 * <ul>
	 * <li>C: suba/b: b'''</li>
	 * <li>D: suba/b: b''</li>
	 * <li>E: suba/b: b''''</li>
	 * </ul>
	 *
	 * <pre>
	 *         G   I   subtree
	 *          \   \
	 *    A--B--C'--E' super project
	 *   /    \    /
	 *  F      -D'-    super projet
	 *         /
	 *        H        subtree
	 *
	 *
	 *    B  C' E'  super project
	 *   /  /  /
	 *  F--G--I     subtree
	 *   \   /
	 *    -H-       subtree
	 *      \
	 *       D'     super project
	 * </pre>
	 *
	 * Test 5 - Modify both branches, merge point matches one
	 * <ul>
	 * <li>C: suba/b: b''</li>
	 * <li>D: suba/b: b'''</li>
	 * <li>E: suba/b: b'''</li>
	 * </ul>
	 *
	 * <pre>
	 * Result is same as test 4
	 * </pre>
	 *
	 * Test 6 - Make same change to both branches, merge point matches
	 * <ul>
	 * <li>C: suba/b: b''</li>
	 * <li>D: suba/b: b''</li>
	 * <li>E: suba/b: b''</li>
	 * </ul>
	 *
	 * <pre>
	 * Result is same as test 4
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testModifySubtreeOnBranch() throws Exception {
		RevCommit A = commit(tree(file("a", blob("a"))));
		RevCommit F = commit(tree(file("b", blob("b")), file("b2", blob("b'"))));
		RevCommit B = subtreeAdd("suba", A, F);

		SubtreeValidator sv = new SubtreeValidator(db, rw).setStart(B);
		sv.normal("A", A).setParents();
		sv.normal("B", B).setParents("A", "F").addSubtree("suba", "F");
		sv.subtree("F", F).setParents();
		sv.validate();

		// Test 1
		{
			RevCommit C = edit(B, false, file("a", blob("a'")));
			RevCommit D = edit(B, false, file("a", blob("a''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("a", blob("a'''"))), C, D);

			SubtreeValidator sv2 = sv.clone();
			sv2.normal("C", C).setParents("B").addSubtree("suba", "F");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "F");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "F");
			sv2.setStart(E);
			sv2.validate();
		}

		// Test 2
		{
			RevCommit C = edit(B, false, file("a", blob("a'")));
			RevCommit D = edit(B, false, file("suba/b", blob("b''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("suba/b", blob("b''"))), C, D);

			SubtreeValidator sv2 = sv.clone();
			sv2.normal("C", C).setParents("B").addSubtree("suba", "F");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "G");
			sv2.rewritten("D'", "D").setParents("B", "G");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "G");
			sv2.rewritten("E'", "E").setParents("C", "D'");
			sv2.split("G", "suba", D).setParents("F");
			sv2.setStart(E);
			sv2.validate();
		}

		// Test 3
		{
			RevCommit C = edit(B, false, file("a", blob("a'")));
			RevCommit D = edit(B, false, file("suba/b", blob("b''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("suba/b", blob("b'''"))), C, D);

			SubtreeValidator sv2 = sv.clone();
			sv2.normal("C", C).setParents("B").addSubtree("suba", "F");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "G");
			sv2.rewritten("D'", "D").setParents("B", "G");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "H");
			sv2.rewritten("E'", "E").setParents("C", "D'", "H");
			sv2.split("G", "suba", D).setParents("F");
			sv2.split("H", "suba", E).setParents("G");
			sv2.setStart(E);
			sv2.validate();
		}

		// Test 4
		{
			SubtreeValidator sv2 = sv.clone();
			RevCommit C = edit(B, false, file("suba/b", blob("b'''")));
			RevCommit D = edit(B, false, file("suba/b", blob("b''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("suba/b", blob("b''''"))), C, D);
			sv2.normal("C", C).setParents("B").addSubtree("suba", "G");
			sv2.rewritten("C'", "C").setParents("B", "G");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "H");
			sv2.rewritten("D'", "D").setParents("B", "H");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "I");
			sv2.rewritten("E'", "E").setParents("C'", "D'", "I");
			sv2.split("G", "suba", C).setParents("F");
			sv2.split("H", "suba", D).setParents("F");
			sv2.split("I", "suba", E).setParents("G", "H");
			sv2.setStart(E);
			sv2.validate();
		}

		// Test 5
		{
			SubtreeValidator sv2 = sv.clone();
			RevCommit C = edit(B, false, file("suba/b", blob("b''")));
			RevCommit D = edit(B, false, file("suba/b", blob("b'''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("suba/b", blob("b'''"))), C, D);
			sv2.normal("C", C).setParents("B").addSubtree("suba", "G");
			sv2.rewritten("C'", "C").setParents("B", "G");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "H");
			sv2.rewritten("D'", "D").setParents("B", "H");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "I");
			sv2.rewritten("E'", "E").setParents("C'", "D'", "I");
			sv2.split("G", "suba", C).setParents("F");
			sv2.split("H", "suba", D).setParents("F");
			sv2.split("I", "suba", E).setParents("G", "H");
			sv2.setStart(E);
			sv2.validate();
		}

		// Test 6
		{
			SubtreeValidator sv2 = sv.clone();
			RevCommit C = edit(B, false, file("suba/b", blob("b''")));
			RevCommit D = edit(B, false, file("suba/b", blob("b''")));
			rw.parseCommit(C);
			RevCommit E = commit(
					editTree(C.getTree(), file("suba/b", blob("b''"))), C, D);
			sv2.normal("C", C).setParents("B").addSubtree("suba", "G");
			sv2.rewritten("C'", "C").setParents("B", "G");
			sv2.normal("D", D).setParents("B").addSubtree("suba", "H");
			sv2.rewritten("D'", "D").setParents("B", "H");
			sv2.normal("E", E).setParents("C", "D").addSubtree("suba", "I");
			sv2.rewritten("E'", "E").setParents("C'", "D'", "I");
			sv2.split("G", "suba", C).setParents("F");
			sv2.split("H", "suba", D).setParents("F");
			sv2.split("I", "suba", E).setParents("G", "H");
			sv2.setStart(E);
			sv2.validate();
		}

	}

}
