/*
 * Copyright (C) 2013, Google Inc.
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

import java.util.Map;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class NameRevCommandTest extends RepositoryTestCase {
	private TestRepository<Repository> tr;
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<Repository>(db);
		git = new Git(db);
	}

	@Test
	public void nameExact() throws Exception {
		RevCommit c = tr.commit().create();
		tr.update("master", c);
		assertOneResult("master", c);
	}

	@Test
	public void prefix() throws Exception {
		RevCommit c = tr.commit().create();
		tr.update("refs/heads/master", c);
		tr.update("refs/tags/tag", c);
		assertOneResult("master", c);
		assertOneResult("master",
				git.nameRev().addPrefix("refs/heads/").addPrefix("refs/tags/"),
				c);
		assertOneResult("tag",
				git.nameRev().addPrefix("refs/tags/").addPrefix("refs/heads/"),
				c);
	}

	@Test
	public void ref() throws Exception {
		RevCommit c = tr.commit().create();
		tr.update("refs/heads/master", c);
		tr.update("refs/tags/tag", c);
		assertOneResult("master",
				git.nameRev().addRef(db.getRef("refs/heads/master")), c);
		assertOneResult("tag",
				git.nameRev().addRef(db.getRef("refs/tags/tag")), c);
	}

	@Test
	public void annotatedTags() throws Exception {
		RevCommit c = tr.commit().create();
		tr.update("refs/heads/master", c);
		tr.update("refs/tags/tag1", c);
		tr.update("refs/tags/tag2", tr.tag("tag2", c));
		assertOneResult("tag2", git.nameRev().addAnnotatedTags(), c);
	}

	@Test
	public void simpleAncestor() throws Exception {
		// 0--1--2
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c2 = tr.commit().parent(c1).create();
		tr.update("master", c2);
		Map<ObjectId, String> result = git.nameRev().add(c0).add(c1).add(c2).call();
		assertEquals(3, result.size());
		assertEquals("master~2", result.get(c0));
		assertEquals("master~1", result.get(c1));
		assertEquals("master", result.get(c2));
	}

	@Test
	public void multiplePathsNoMerge() throws Exception {
		// 0--1    <- master
		//  \-2--3 <- branch
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c2 = tr.commit().parent(c0).create();
		RevCommit c3 = tr.commit().parent(c2).create();
		tr.update("master", c1);
		tr.update("branch", c3);
		assertOneResult("master~1", c0);
	}

	@Test
	public void onePathMerge() throws Exception {
		// 0--1--3
		//  \-2-/
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c2 = tr.commit().parent(c0).create();
		RevCommit c3 = tr.commit().parent(c1).parent(c2).create();
		tr.update("master", c3);
		assertOneResult("master~2", c0);
	}

	@Test
	public void onePathMergeSecondParent() throws Exception {
		// 0--1-----4
		//  \-2--3-/
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c2 = tr.commit().parent(c0).create();
		RevCommit c3 = tr.commit().parent(c2).create();
		RevCommit c4 = tr.commit().parent(c1).parent(c3).create();
		tr.update("master", c4);
		assertOneResult("master^2", c3);
		assertOneResult("master^2~1", c2);
	}

	@Test
	public void onePathMergeLongerFirstParentPath() throws Exception {
		// 0--1--2--4
		//  \--3---/
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c2 = tr.commit().parent(c1).create();
		RevCommit c3 = tr.commit().parent(c0).create();
		RevCommit c4 = tr.commit().parent(c2).parent(c3).create();
		tr.update("master", c4);
		assertOneResult("master^2", c3);
		assertOneResult("master~3", c0);
	}

	@Test
	public void multiplePathsSecondParent() throws Exception {
		// 0--...--2
		//  \--1--/
		RevCommit c0 = tr.commit().create();
		RevCommit c1 = tr.commit().parent(c0).create();
		RevCommit c = c0;
		int mergeCost = 5;
		for (int i = 0; i < mergeCost; i++) {
			c = tr.commit().parent(c).create();
		}
		RevCommit c2 = tr.commit().parent(c).parent(c1).create();
		tr.update("master", c2);
		assertOneResult("master^2~1", git.nameRev().setMergeCost(mergeCost), c0);
	}

	private static void assertOneResult(String expected, NameRevCommand nameRev,
			ObjectId id) throws Exception {
		Map<ObjectId, String> result = nameRev.add(id).call();
		assertEquals(1, result.size());
		assertEquals(expected, result.get(id));
	}

	private void assertOneResult(String expected, ObjectId id) throws Exception {
		assertOneResult(expected, git.nameRev(), id);
	}
}
