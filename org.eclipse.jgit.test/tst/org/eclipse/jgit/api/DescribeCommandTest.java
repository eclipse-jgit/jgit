/*
 * Copyright (C) 2013, CloudBees, Inc.
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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.*;

public class DescribeCommandTest extends RepositoryTestCase {

	private Git git;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test(expected=IllegalArgumentException.class)
	public void noTargetSet() throws Exception {
		git.describe().call();
	}

	@Test
	public void testDescribe() throws Exception {
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("t1");

		ObjectId c3 = modify("ccc");
		tag("t2");

		ObjectId c4 = modify("ddd");


		assertNull(describe(c1));
		assertEquals("t1", describe(c2));
		assertEquals("t2", describe(c3));

		assertNameStartsWith(c4, "3e563c5");
		assertEquals("t2-1-g3e563c5", describe(c4)); // the value verified with git-describe(1)
	}

	/**
	 * Make sure it finds a tag when not all ancestries include a tag.
	 *
	 * c1 -+-> T  -
	 *     |       |
	 *     +-> c3 -+-> c4
	 */
	@Test
	public void testDescribeBranch() throws Exception {
		ObjectId c1 = modify("aaa");

		ObjectId c2 = modify("bbb");
		tag("t");

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		assertEquals("t-2-g119892b", describe(c4)); // 2 commits: c4 and c3
		assertNull(describe(c3));
	}

	private void branch(String name, ObjectId base) throws GitAPIException {
		git.checkout().setCreateBranch(true).setName(name).setStartPoint(base.name()).call();
	}

	/**
	 * When t2 dominates t1, it's clearly preferable to describe by using t2.
	 *
	 * t1 -+-> t2  -
	 *     |       |
	 *     +-> c3 -+-> c4
	 */
	@Test
	public void t1DominatesT2() throws Exception {
		ObjectId c1 = modify("aaa");
		tag("t1");

		ObjectId c2 = modify("bbb");
		tag("t2");

		branch("b", c1);

		ObjectId c3 = modify("ccc");

		ObjectId c4 = merge(c2);

		assertNameStartsWith(c4, "119892b");
		assertEquals("t2-2-g119892b", describe(c4)); // 2 commits: c4 and c3

		assertNameStartsWith(c3, "0244e7f");
		assertEquals("t1-1-g0244e7f", describe(c3));
	}

	private ObjectId merge(ObjectId c2) throws GitAPIException {
		return git.merge().include(c2).call().getNewHead();
	}

	private ObjectId modify(String content) throws Exception {
		File a = new File(db.getWorkTree(),"a.txt");
		touch(a, content);
		return git.commit().setAll(true).setMessage(content).call().getId();
	}

	private void tag(String tag) throws GitAPIException {
		git.tag().setName(tag).setMessage(tag).call();
	}

	private void touch(File f, String contents) throws Exception {
		FileWriter w = new FileWriter(f);
		w.write(contents);
		w.close();
	}

	private String describe(ObjectId c1) throws GitAPIException, IOException {
		return git.describe().setTarget(c1).call();
	}

	private void assertNameStartsWith(ObjectId c4, String prefix) {
		assertTrue(c4.name(), c4.name().startsWith(prefix));
	}
}
