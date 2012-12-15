/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2011, Philipp Thun <philipp.thun@sap.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

/**
 * Testing the 'commit only' option:
 *
 * I. A single file (f1.txt) specified as part of the --only/ -o option can have
 * one of the following (14) states:
 *
 * <pre>
 *        |                          | expected result
 * ---------------------------------------------------------------------
 *        | HEAD  DirCache  Worktree | HEAD  DirCache
 * ---------------------------------------------------------------------
 *  f1_1  |  -       -       c       |                => e: path unknown
 *  f1_2  |  -       c       -       |                => no changes
 *  f1_3  |  c       -       -       |  -       -
 *  f1_4  |  -       c       c       |  c       c
 *  f1_5  |  c       c       -       |  -       -
 *  f1_6  |  c       -       c       |                => no changes
 *  f1_7  |  c       c       c       |                => no changes
 * ---------------------------------------------------------------------
 *  f1_8  |  -       c       c'      |  c'      c'
 *  f1_9  |  c       -       c'      |  c'      c'
 * f1_10  |  c       c'      -       |  -       -
 * f1_11  |  c       c       c'      |  c'      c'
 * f1_12  |  c       c'      c       |                => no changes
 * f1_13  |  c       c'      c'      |  c'      c'
 * ---------------------------------------------------------------------
 * f1_14  |  c       c'      c''     |  c''     c''
 * </pre>
 *
 * II. Scenarios that do not end with a successful commit (1, 2, 6, 7, 12) have
 * to be tested with a second file (f2.txt) specified that would lead to a
 * successful commit, if it were executed separately (e.g. scenario 14).
 *
 * <pre>
 *              |                          | expected result
 * ---------------------------------------------------------------------------
 *              | HEAD  DirCache  Worktree | HEAD  DirCache
 * ---------------------------------------------------------------------------
 *  f1_1_f2_14  |  -       -       c       |                => e: path unknown
 *  f1_2_f2_14  |  -       c       -       |  -       -
 *  f1_6_f2_14  |  c       -       c       |  c       c
 *  f1_7_f2_14  |  c       c       c       |  c       c
 * ---------------------------------------------------------------------------
 * f1_12_f2_14  |  c       c'      c       |  c       c
 * </pre>
 *
 * III. All scenarios (1-14, I-II) have to be tested with different repository
 * states, to check that the --only/ -o option does not change existing content
 * (HEAD and DirCache). The following states for a file (f3.txt) not specified
 * shall be tested:
 *
 * <pre>
 *       | HEAD  DirCache
 * --------------------
 *  *_a  |  -       -
 *  *_b  |  -       c
 *  *_c  |  c       c
 *  *_d  |  c       -
 * --------------------
 *  *_e  |  c       c'
 * </pre>
 **/
public class CommitOnlyTest extends RepositoryTestCase {
	private static final String F1 = "f1.txt";

	private static final String F2 = "f2.txt";

	private static final String F3 = "f3.txt";

	private static final String MSG = "commit";

	private static int A = 0;

	private static int B = 1;

	private static int C = 2;

	private static int D = 3;

	private static int E = 4;

	@Test
	public void testOnlyOption_f1_1_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_1(git);
		executeAndCheck_f1_1(git, A);
	}

	@Test
	public void testOnlyOption_f1_1_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_1(git);
		executeAndCheck_f1_1(git, B);
	}

	@Test
	public void testOnlyOption_f1_1_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_1(git);
		executeAndCheck_f1_1(git, C);
	}

	@Test
	public void testOnlyOption_f1_1_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_1(git);
		executeAndCheck_f1_1(git, D);
	}

	@Test
	public void testOnlyOption_f1_1_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_1(git);
		executeAndCheck_f1_1(git, E);
	}

	@Test
	public void testOnlyOption_f1_1_f2_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, A);
		prepare_f1_1(git);
		executeAndCheck_f1_1_f2_f14(git, A);
	}

	@Test
	public void testOnlyOption_f1_1_f2_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, B);
		prepare_f1_1(git);
		executeAndCheck_f1_1_f2_f14(git, B);
	}

	@Test
	public void testOnlyOption_f1_1_f2_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, C);
		prepare_f1_1(git);
		executeAndCheck_f1_1_f2_f14(git, C);
	}

	@Test
	public void testOnlyOption_f1_1_f2_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, D);
		prepare_f1_1(git);
		executeAndCheck_f1_1_f2_f14(git, D);
	}

	@Test
	public void testOnlyOption_f1_1_f2_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, E);
		prepare_f1_1(git);
		executeAndCheck_f1_1_f2_f14(git, E);
	}

	@Test
	public void testOnlyOption_f1_2_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_2(git);
		executeAndCheck_f1_2(git, A);
	}

	@Test
	public void testOnlyOption_f1_2_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_2(git);
		executeAndCheck_f1_2(git, B);
	}

	@Test
	public void testOnlyOption_f1_2_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_2(git);
		executeAndCheck_f1_2(git, C);
	}

	@Test
	public void testOnlyOption_f1_2_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_2(git);
		executeAndCheck_f1_2(git, D);
	}

	@Test
	public void testOnlyOption_f1_2_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_2(git);
		executeAndCheck_f1_2(git, E);
	}

	@Test
	public void testOnlyOption_f1_2_f2_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, A);
		prepare_f1_2(git);
		executeAndCheck_f1_2_f2_f14(git, A);
	}

	@Test
	public void testOnlyOption_f1_2_f2_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, B);
		prepare_f1_2(git);
		executeAndCheck_f1_2_f2_f14(git, B);
	}

	@Test
	public void testOnlyOption_f1_2_f2_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, C);
		prepare_f1_2(git);
		executeAndCheck_f1_2_f2_f14(git, C);
	}

	@Test
	public void testOnlyOption_f1_2_f2_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, D);
		prepare_f1_2(git);
		executeAndCheck_f1_2_f2_f14(git, D);
	}

	@Test
	public void testOnlyOption_f1_2_f2_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, E);
		prepare_f1_2(git);
		executeAndCheck_f1_2_f2_f14(git, E);
	}

	@Test
	public void testOnlyOption_f1_3_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_3(git);
		executeAndCheck_f1_3(git, A);
	}

	@Test
	public void testOnlyOption_f1_3_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_3(git);
		executeAndCheck_f1_3(git, B);
	}

	@Test
	public void testOnlyOption_f1_3_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_3(git);
		executeAndCheck_f1_3(git, C);
	}

	@Test
	public void testOnlyOption_f1_3_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_3(git);
		executeAndCheck_f1_3(git, D);
	}

	@Test
	public void testOnlyOption_f1_3_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_3(git);
		executeAndCheck_f1_3(git, E);
	}

	@Test
	public void testOnlyOption_f1_4_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_4(git);
		executeAndCheck_f1_4(git, A);
	}

	@Test
	public void testOnlyOption_f1_4_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_4(git);
		executeAndCheck_f1_4(git, B);
	}

	@Test
	public void testOnlyOption_f1_4_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_4(git);
		executeAndCheck_f1_4(git, C);
	}

	@Test
	public void testOnlyOption_f1_4_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_4(git);
		executeAndCheck_f1_4(git, D);
	}

	@Test
	public void testOnlyOption_f1_4_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_4(git);
		executeAndCheck_f1_4(git, E);
	}

	@Test
	public void testOnlyOption_f1_5_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_5(git);
		executeAndCheck_f1_5(git, A);
	}

	@Test
	public void testOnlyOption_f1_5_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_5(git);
		executeAndCheck_f1_5(git, B);
	}

	@Test
	public void testOnlyOption_f1_5_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_5(git);
		executeAndCheck_f1_5(git, C);
	}

	@Test
	public void testOnlyOption_f1_5_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_5(git);
		executeAndCheck_f1_5(git, D);
	}

	@Test
	public void testOnlyOption_f1_5_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_5(git);
		executeAndCheck_f1_5(git, E);
	}

	@Test
	public void testOnlyOption_f1_6_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_6(git);
		executeAndCheck_f1_6(git, A);
	}

	@Test
	public void testOnlyOption_f1_6_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_6(git);
		executeAndCheck_f1_6(git, B);
	}

	@Test
	public void testOnlyOption_f1_6_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_6(git);
		executeAndCheck_f1_6(git, C);
	}

	@Test
	public void testOnlyOption_f1_6_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_6(git);
		executeAndCheck_f1_6(git, D);
	}

	@Test
	public void testOnlyOption_f1_6_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_6(git);
		executeAndCheck_f1_6(git, E);
	}

	@Test
	public void testOnlyOption_f1_6_f2_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, A);
		prepare_f1_6(git);
		executeAndCheck_f1_6_f2_14(git, A);
	}

	@Test
	public void testOnlyOption_f1_6_f2_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, B);
		prepare_f1_6(git);
		executeAndCheck_f1_6_f2_14(git, B);
	}

	@Test
	public void testOnlyOption_f1_6_f2_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, C);
		prepare_f1_6(git);
		executeAndCheck_f1_6_f2_14(git, C);
	}

	@Test
	public void testOnlyOption_f1_6_f2_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, D);
		prepare_f1_6(git);
		executeAndCheck_f1_6_f2_14(git, D);
	}

	@Test
	public void testOnlyOption_f1_6_f2_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, E);
		prepare_f1_6(git);
		executeAndCheck_f1_6_f2_14(git, E);
	}

	@Test
	public void testOnlyOption_f1_7_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_7(git);
		executeAndCheck_f1_7(git, A);
	}

	@Test
	public void testOnlyOption_f1_7_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_7(git);
		executeAndCheck_f1_7(git, B);
	}

	@Test
	public void testOnlyOption_f1_7_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_7(git);
		executeAndCheck_f1_7(git, C);
	}

	@Test
	public void testOnlyOption_f1_7_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_7(git);
		executeAndCheck_f1_7(git, D);
	}

	@Test
	public void testOnlyOption_f1_7_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_7(git);
		executeAndCheck_f1_7(git, E);
	}

	@Test
	public void testOnlyOption_f1_7_f2_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, A);
		prepare_f1_7(git);
		executeAndCheck_f1_7_f2_14(git, A);
	}

	@Test
	public void testOnlyOption_f1_7_f2_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, B);
		prepare_f1_7(git);
		executeAndCheck_f1_7_f2_14(git, B);
	}

	@Test
	public void testOnlyOption_f1_7_f2_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, C);
		prepare_f1_7(git);
		executeAndCheck_f1_7_f2_14(git, C);
	}

	@Test
	public void testOnlyOption_f1_7_f2_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, D);
		prepare_f1_7(git);
		executeAndCheck_f1_7_f2_14(git, D);
	}

	@Test
	public void testOnlyOption_f1_7_f2_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, E);
		prepare_f1_7(git);
		executeAndCheck_f1_7_f2_14(git, E);
	}

	@Test
	public void testOnlyOption_f1_8_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_8(git);
		executeAndCheck_f1_8(git, A);
	}

	@Test
	public void testOnlyOption_f1_8_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_8(git);
		executeAndCheck_f1_8(git, B);
	}

	@Test
	public void testOnlyOption_f1_8_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_8(git);
		executeAndCheck_f1_8(git, C);
	}

	@Test
	public void testOnlyOption_f1_8_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_8(git);
		executeAndCheck_f1_8(git, D);
	}

	@Test
	public void testOnlyOption_f1_8_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_8(git);
		executeAndCheck_f1_8(git, E);
	}

	@Test
	public void testOnlyOption_f1_9_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_9(git);
		executeAndCheck_f1_9(git, A);
	}

	@Test
	public void testOnlyOption_f1_9_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_9(git);
		executeAndCheck_f1_9(git, B);
	}

	@Test
	public void testOnlyOption_f1_9_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_9(git);
		executeAndCheck_f1_9(git, C);
	}

	@Test
	public void testOnlyOption_f1_9_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_9(git);
		executeAndCheck_f1_9(git, D);
	}

	@Test
	public void testOnlyOption_f1_9_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_9(git);
		executeAndCheck_f1_9(git, E);
	}

	@Test
	public void testOnlyOption_f1_10_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_10(git);
		executeAndCheck_f1_10(git, A);
	}

	@Test
	public void testOnlyOption_f1_10_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_10(git);
		executeAndCheck_f1_10(git, B);
	}

	@Test
	public void testOnlyOption_f1_10_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_10(git);
		executeAndCheck_f1_10(git, C);
	}

	@Test
	public void testOnlyOption_f1_10_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_10(git);
		executeAndCheck_f1_10(git, D);
	}

	@Test
	public void testOnlyOption_f1_10_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_10(git);
		executeAndCheck_f1_10(git, E);
	}

	@Test
	public void testOnlyOption_f1_11_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_11(git);
		executeAndCheck_f1_11(git, A);
	}

	@Test
	public void testOnlyOption_f1_11_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_11(git);
		executeAndCheck_f1_11(git, B);
	}

	@Test
	public void testOnlyOption_f1_11_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_11(git);
		executeAndCheck_f1_11(git, C);
	}

	@Test
	public void testOnlyOption_f1_11_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_11(git);
		executeAndCheck_f1_11(git, D);
	}

	@Test
	public void testOnlyOption_f1_11_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_11(git);
		executeAndCheck_f1_11(git, E);
	}

	@Test
	public void testOnlyOption_f1_12_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_12(git);
		executeAndCheck_f1_12(git, A);
	}

	@Test
	public void testOnlyOption_f1_12_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_12(git);
		executeAndCheck_f1_12(git, B);
	}

	@Test
	public void testOnlyOption_f1_12_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_12(git);
		executeAndCheck_f1_12(git, C);
	}

	@Test
	public void testOnlyOption_f1_12_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_12(git);
		executeAndCheck_f1_12(git, D);
	}

	@Test
	public void testOnlyOption_f1_12_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_12(git);
		executeAndCheck_f1_12(git, E);
	}

	@Test
	public void testOnlyOption_f1_12_f2_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, A);
		prepare_f1_12(git);
		executeAndCheck_f1_12_f2_14(git, A);
	}

	@Test
	public void testOnlyOption_f1_12_f2_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, B);
		prepare_f1_12(git);
		executeAndCheck_f1_12_f2_14(git, B);
	}

	@Test
	public void testOnlyOption_f1_12_f2_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, C);
		prepare_f1_12(git);
		executeAndCheck_f1_12_f2_14(git, C);
	}

	@Test
	public void testOnlyOption_f1_12_f2_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, D);
		prepare_f1_12(git);
		executeAndCheck_f1_12_f2_14(git, D);
	}

	@Test
	public void testOnlyOption_f1_12_f2_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3_f2_14(git, E);
		prepare_f1_12(git);
		executeAndCheck_f1_12_f2_14(git, E);
	}

	@Test
	public void testOnlyOption_f1_13_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_13(git);
		executeAndCheck_f1_13(git, A);
	}

	@Test
	public void testOnlyOption_f1_13_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_13(git);
		executeAndCheck_f1_13(git, B);
	}

	@Test
	public void testOnlyOption_f1_13_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_13(git);
		executeAndCheck_f1_13(git, C);
	}

	@Test
	public void testOnlyOption_f1_13_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_13(git);
		executeAndCheck_f1_13(git, D);
	}

	@Test
	public void testOnlyOption_f1_13_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_13(git);
		executeAndCheck_f1_13(git, E);
	}

	@Test
	public void testOnlyOption_f1_14_a() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, A);
		prepare_f1_14(git);
		executeAndCheck_f1_14(git, A);
	}

	@Test
	public void testOnlyOption_f1_14_b() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, B);
		prepare_f1_14(git);
		executeAndCheck_f1_14(git, B);
	}

	@Test
	public void testOnlyOption_f1_14_c() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, C);
		prepare_f1_14(git);
		executeAndCheck_f1_14(git, C);
	}

	@Test
	public void testOnlyOption_f1_14_d() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, D);
		prepare_f1_14(git);
		executeAndCheck_f1_14(git, D);
	}

	@Test
	public void testOnlyOption_f1_14_e() throws Exception {
		final Git git = new Git(db);
		prepare_f3(git, E);
		prepare_f1_14(git);
		executeAndCheck_f1_14(git, E);
	}

	@Test
	public void testOnlyOptionWithDirectory() throws Exception {
		final Git git = new Git(db);

		// write files
		final File f1 = writeTrashFile("d1/d2/f1.txt", "c1");
		writeTrashFile("d1/d2/f2.txt", "c2");
		final File f3 = writeTrashFile("d1/f3.txt", "c3");
		writeTrashFile("d1/f4.txt", "c4");
		final File f5 = writeTrashFile("d3/d4/f5.txt", "c5");
		writeTrashFile("d3/d4/f6.txt", "c6");
		final File f7 = writeTrashFile("d3/f7.txt", "c7");
		writeTrashFile("d3/f8.txt", "c8");
		final File f9 = writeTrashFile("d5/f9.txt", "c9");
		writeTrashFile("d5/f10.txt", "c10");
		final File f11 = writeTrashFile("d6/f11.txt", "c11");
		writeTrashFile("d6/f12.txt", "c12");

		// add files
		git.add().addFilepattern(".").call();

		// modify files, but do not stage changes
		write(f1, "c1'");
		write(f3, "c3'");
		write(f5, "c5'");
		write(f7, "c7'");
		write(f9, "c9'");
		write(f11, "c11'");

		// commit selected files only
		git.commit().setOnly("d1").setOnly("d3/d4/").setOnly("d5")
				.setOnly("d6/f11.txt").setMessage(MSG).call();

		assertEquals("c1'", getHead(git, "d1/d2/f1.txt"));
		assertEquals("c2", getHead(git, "d1/d2/f2.txt"));
		assertEquals("c3'", getHead(git, "d1/f3.txt"));
		assertEquals("c4", getHead(git, "d1/f4.txt"));
		assertEquals("c5'", getHead(git, "d3/d4/f5.txt"));
		assertEquals("c6", getHead(git, "d3/d4/f6.txt"));
		assertEquals("", getHead(git, "d3/f7.txt"));
		assertEquals("", getHead(git, "d3/f8.txt"));
		assertEquals("c9'", getHead(git, "d5/f9.txt"));
		assertEquals("c10", getHead(git, "d5/f10.txt"));
		assertEquals("c11'", getHead(git, "d6/f11.txt"));
		assertEquals("", getHead(git, "d6/f12.txt"));
		assertEquals("[d1/d2/f1.txt, mode:100644, content:c1']"
				+ "[d1/d2/f2.txt, mode:100644, content:c2]"
				+ "[d1/f3.txt, mode:100644, content:c3']"
				+ "[d1/f4.txt, mode:100644, content:c4]"
				+ "[d3/d4/f5.txt, mode:100644, content:c5']"
				+ "[d3/d4/f6.txt, mode:100644, content:c6]"
				+ "[d3/f7.txt, mode:100644, content:c7]"
				+ "[d3/f8.txt, mode:100644, content:c8]"
				+ "[d5/f10.txt, mode:100644, content:c10]"
				+ "[d5/f9.txt, mode:100644, content:c9']"
				+ "[d6/f11.txt, mode:100644, content:c11']"
				+ "[d6/f12.txt, mode:100644, content:c12]", indexState(CONTENT));
	}

	@SuppressWarnings("unused")
	private File prepare_f1_1(final Git git) throws IOException {
		return writeTrashFile(F1, "c1");
	}

	private File prepare_f1_2(final Git git) throws Exception {
		final File f1 = prepare_f1_4(git);
		f1.delete();
		return f1;
	}

	private File prepare_f1_3(final Git git) throws Exception {
		final File f1 = prepare_f1_7(git);
		git.rm().addFilepattern(F1).call();
		return f1;
	}

	private File prepare_f1_4(final Git git) throws Exception {
		final File f1 = prepare_f1_1(git);
		git.add().addFilepattern(F1).call();
		return f1;
	}

	private File prepare_f1_5(final Git git) throws Exception {
		final File f1 = prepare_f1_7(git);
		f1.delete();
		return f1;
	}

	private File prepare_f1_6(final Git git) throws Exception {
		final File f1 = prepare_f1_3(git);
		write(f1, "c1");
		return f1;
	}

	private File prepare_f1_7(final Git git) throws Exception {
		final File f1 = prepare_f1_4(git);
		git.commit().setOnly(F1).setMessage(MSG).call();
		return f1;
	}

	private File prepare_f1_8(final Git git) throws Exception {
		final File f1 = prepare_f1_4(git);
		write(f1, "c1'");
		return f1;
	}

	private File prepare_f1_9(final Git git) throws Exception {
		final File f1 = prepare_f1_3(git);
		write(f1, "c1'");
		return f1;
	}

	private File prepare_f1_10(final Git git) throws Exception {
		final File f1 = prepare_f1_9(git);
		git.add().addFilepattern(F1).call();
		f1.delete();
		return f1;
	}

	private File prepare_f1_11(final Git git) throws Exception {
		final File f1 = prepare_f1_7(git);
		write(f1, "c1'");
		return f1;
	}

	private File prepare_f1_12(final Git git) throws Exception {
		final File f1 = prepare_f1_13(git);
		write(f1, "c1");
		return f1;
	}

	private File prepare_f1_13(final Git git) throws Exception {
		final File f1 = prepare_f1_11(git);
		git.add().addFilepattern(F1).call();
		return f1;
	}

	private File prepare_f1_14(final Git git) throws Exception {
		final File f1 = prepare_f1_13(git);
		write(f1, "c1''");
		return f1;
	}

	@SuppressWarnings("null")
	private void executeAndCheck_f1_1(final Git git, final int state)
			throws Exception {
		JGitInternalException exception = null;
		try {
			git.commit().setOnly(F1).setMessage(MSG).call();
		} catch (JGitInternalException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception.getMessage().contains(F1));

		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals(expected_f3_idx(state), indexState(CONTENT));
	}

	@SuppressWarnings("null")
	private void executeAndCheck_f1_1_f2_f14(final Git git, final int state)
			throws Exception {
		JGitInternalException exception = null;
		try {
			git.commit().setOnly(F1).setOnly(F2).setMessage(MSG).call();
		} catch (JGitInternalException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception.getMessage().contains(F1));

		assertEquals("c2", getHead(git, F2));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f2.txt, mode:100644, content:c2']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	@SuppressWarnings("null")
	private void executeAndCheck_f1_2(final Git git, final int state)
			throws Exception {
		JGitInternalException exception = null;
		try {
			git.commit().setOnly(F1).setMessage(MSG).call();
		} catch (JGitInternalException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception.getMessage().contains("No changes"));

		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1]"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_2_f2_f14(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setOnly(F2).setMessage(MSG).call();

		assertEquals("", getHead(git, F1));
		assertEquals("c2''", getHead(git, F2));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f2.txt, mode:100644, content:c2'']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_3(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setMessage(MSG).call();

		assertEquals("", getHead(git, F1));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals(expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_4(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setMessage(MSG).call();

		assertEquals("c1", getHead(git, F1));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1]"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_5(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_3(git, state);
	}

	@SuppressWarnings("null")
	private void executeAndCheck_f1_6(final Git git, final int state)
			throws Exception {
		JGitInternalException exception = null;
		try {
			git.commit().setOnly(F1).setMessage(MSG).call();
		} catch (JGitInternalException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception.getMessage().contains("No changes"));

		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals(expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_6_f2_14(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setOnly(F2).setMessage(MSG).call();

		assertEquals("c1", getHead(git, F1));
		assertEquals("c2''", getHead(git, F2));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1]"
				+ "[f2.txt, mode:100644, content:c2'']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_7(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_2(git, state);
	}

	private void executeAndCheck_f1_7_f2_14(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_6_f2_14(git, state);
	}

	private void executeAndCheck_f1_8(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setMessage(MSG).call();

		assertEquals("c1'", getHead(git, F1));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_9(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_8(git, state);
	}

	private void executeAndCheck_f1_10(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_3(git, state);
	}

	private void executeAndCheck_f1_11(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_8(git, state);
	}

	@SuppressWarnings("null")
	private void executeAndCheck_f1_12(final Git git, final int state)
			throws Exception {
		JGitInternalException exception = null;
		try {
			git.commit().setOnly(F1).setMessage(MSG).call();
		} catch (JGitInternalException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception.getMessage().contains("No changes"));

		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void executeAndCheck_f1_12_f2_14(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_6_f2_14(git, state);
	}

	private void executeAndCheck_f1_13(final Git git, final int state)
			throws Exception {
		executeAndCheck_f1_8(git, state);
	}

	private void executeAndCheck_f1_14(final Git git, final int state)
			throws Exception {
		git.commit().setOnly(F1).setMessage(MSG).call();

		assertEquals("c1''", getHead(git, F1));
		assertEquals(expected_f3_head(state), getHead(git, F3));
		assertEquals("[f1.txt, mode:100644, content:c1'']"
				+ expected_f3_idx(state), indexState(CONTENT));
	}

	private void prepare_f3(final Git git, final int state) throws Exception {
		prepare_f3_f2_14(git, state, false);
	}

	private void prepare_f3_f2_14(final Git git, final int state)
			throws Exception {
		prepare_f3_f2_14(git, state, true);
	}

	private void prepare_f3_f2_14(final Git git, final int state,
			final boolean include_f2) throws Exception {
		File f2 = null;
		if (include_f2) {
			f2 = writeTrashFile(F2, "c2");
			git.add().addFilepattern(F2).call();
			git.commit().setMessage(MSG).call();
		}

		if (state >= 1) {
			writeTrashFile(F3, "c3");
			git.add().addFilepattern(F3).call();
		}
		if (state >= 2)
			git.commit().setMessage(MSG).call();
		if (state >= 3)
			git.rm().addFilepattern(F3).call();
		if (state == 4) {
			writeTrashFile(F3, "c3'");
			git.add().addFilepattern(F3).call();
		}

		if (include_f2) {
			write(f2, "c2'");
			git.add().addFilepattern(F2).call();
			write(f2, "c2''");
		}
	}

	private static String expected_f3_head(final int state) {
		switch (state) {
		case 0:
		case 1:
			return "";
		case 2:
		case 3:
		case 4:
			return "c3";
		}
		return null;
	}

	private static String expected_f3_idx(final int state) {
		switch (state) {
		case 0:
		case 3:
			return "";
		case 1:
		case 2:
			return "[f3.txt, mode:100644, content:c3]";
		case 4:
			return "[f3.txt, mode:100644, content:c3']";
		}
		return null;
	}

	static private String getHead(final Git git, final String path)
			throws Exception {
		try {
			final Repository repo = git.getRepository();
			final ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}");
			final TreeWalk tw = TreeWalk.forPath(repo, path,
					new RevWalk(repo).parseTree(headId));
			return new String(tw.getObjectReader().open(tw.getObjectId(0))
					.getBytes());
		} catch (Exception e) {
			return "";
		}
	}
}
