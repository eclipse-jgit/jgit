/*
 * Copyright (C) 2013 SATO taichi <ryushi@gmail.com>
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link CreateOrphanBranchCommand}.
 */
public class CreateOrphanBranchCommandTest extends RepositoryTestCase {

	Git git;

	List<RevCommit> commits;

	CreateOrphanBranchCommand target;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		this.git = new Git(db);
		this.commits = new ArrayList<RevCommit>();
		this.commits.add(this.newFile("aaa"));
		this.commits.add(this.newFile("bbb"));
		this.commits.add(this.newFile("ccc"));

		this.target = new CreateOrphanBranchCommand(db);
	}

	protected RevCommit newFile(String name) throws Exception {
		return commitFile(name + ".txt", name, "master");
	}

	@Test
	public void orphan() throws Exception {
		Ref ref = this.target.setName("ppp").call();
		assertNotNull(ref);
		assertEquals("refs/heads/ppp", ref.getTarget().getName());

		File HEAD = new File(trash, ".git/HEAD");
		String headRef = read(HEAD);
		assertEquals("ref: refs/heads/ppp\n", headRef);
		assertEquals(4, trash.list().length);

		File heads = new File(trash, ".git/refs/heads");
		assertEquals(1, heads.listFiles().length);

		this.noHead();
		this.assertStatus(3);
		assertEquals(CheckoutResult.NOT_TRIED_RESULT, this.target.getResult());
	}

	protected void noHead() throws GitAPIException {
		try {
			this.git.log().call();
			fail();
		} catch (NoHeadException e) {
			// except to hit here
		}
	}

	protected void assertStatus(int files) throws GitAPIException {
		Status status = this.git.status().call();
		assertFalse(status.isClean());
		assertEquals(files, status.getAdded().size());
	}

	@Test
	public void startCommit() throws Exception {
		this.target.setStartPoint(this.commits.get(1)).setName("qqq").call();
		assertEquals(3, trash.list().length);
		this.noHead();
		this.assertStatus(2);
	}

	@Test
	public void startPoint() throws Exception {
		this.target.setStartPoint("HEAD^^").setName("zzz").call();
		assertEquals(2, trash.list().length);
		this.noHead();
		this.assertStatus(1);
	}

	@Test
	public void linkFail() throws Exception {
		File HEAD = new File(trash, ".git/HEAD");
		FileInputStream in = new FileInputStream(HEAD);
		try {
			this.target.setName("aaa").call();
			fail("Should have failed");
		} catch (JGitInternalException e) {
			// except to hit here
		} finally {
			in.close();
		}
	}

	@Test
	public void invalidRefName() throws Exception {
		try {
			this.target.setName("../hoge").call();
			fail("Should have failed");
		} catch (InvalidRefNameException e) {
			// except to hit here
		}
	}

	@Test
	public void nullRefName() throws Exception {
		try {
			this.target.setName(null).call();
			fail("Should have failed");
		} catch (InvalidRefNameException e) {
			// except to hit here
		}
	}

	@Test
	public void alreadyExists() throws Exception {
		this.git.checkout().setCreateBranch(true).setName("ppp").call();
		this.git.checkout().setName("master").call();

		try {
			this.target.setName("ppp").call();
			fail("Should have failed");
		} catch (RefAlreadyExistsException e) {
			// except to hit here
		}
	}

	@Test
	public void refNotFound() throws Exception {
		try {
			this.target.setStartPoint("1234567").setName("ppp").call();
			fail("Should have failed");
		} catch (RefNotFoundException e) {
			// except to hit here
		}
	}

	@Test
	public void toBeDeleted() throws Exception {
		File ccc = new File(trash, "ccc.txt");
		FileInputStream in = new FileInputStream(ccc);
		try {
			this.target.setName("zzz").setStartPoint(this.commits.get(0))
					.call();
			assertEquals(1, this.target.getResult().getUndeletedList().size());
		} finally {
			in.close();
		}
	}

	@Test
	public void conflicts() throws Exception {
		this.git.checkout().setCreateBranch(true).setName("aaa").call();
		File bbb = new File(trash, "bbb.txt");
		write(bbb, "zzzz\nzzz");
		try {
			this.target.setName("zzz").setStartPoint(this.commits.get(0))
					.call();
			fail("Should have failed");
		} catch (CheckoutConflictException e) {
			assertEquals(1, this.target.getResult().getConflictList().size());
			assertEquals(1, e.getConflictingPaths().size());
		}
	}
}