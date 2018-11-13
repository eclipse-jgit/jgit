/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class DfsReftableDatabaseTest {
	private TestRepository<InMemoryRepository> git;

	private InMemoryRepository repo;

	private DfsReftableDatabase refdb;

	@Before
	public void setUp() throws Exception {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		git = new TestRepository<>(new InMemoryRepository(desc));
		repo = git.getRepository();

		RevCommit zero = git.commit().message("0").create();
		RevCommit one = git.commit().message("1").create();

		git.update("branchX", one); // update index = 1
		git.update("master", zero); // update index = 2
		git.update("HEAD", zero); // update index = 3

		refdb = (DfsReftableDatabase) repo.getRefDatabase();
	}

	@Test
	public void testHasRefs() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("HEAD", Long.valueOf(3));
		expectations.put("refs/heads/master", Long.valueOf(2));
		expectations.put("refs/heads/branchX", Long.valueOf(1));

		assertTrue(refdb.hasRefs(expectations));
	}

	@Test
	public void testHasRefs_subset() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("refs/heads/branchX", Long.valueOf(1));
		expectations.put("refs/heads/master", Long.valueOf(2));

		assertTrue(refdb.hasRefs(expectations));
	}

	@Test
	public void testHasRefs_moreRecent() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("refs/heads/branchX", Long.valueOf(0));
		expectations.put("refs/heads/master", Long.valueOf(0));

		assertTrue(refdb.hasRefs(expectations));
	}

	@Test
	public void testCannotServe_tooOld() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("refs/heads/master", Long.valueOf(30));
		expectations.put("refs/heads/branchX", Long.valueOf(1));

		assertFalse(refdb.hasRefs(expectations));
	}

	@Test
	public void testCannotServe_unknownBranch() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("refs/heads/master", Long.valueOf(0));
		expectations.put("refs/heads/unknown", Long.valueOf(0));

		assertFalse(refdb.hasRefs(expectations));
	}

	@Test
	public void testCannotServe_nonQualifiedName() throws Exception {
		Map<String, Long> expectations = new HashMap<>();
		expectations.put("master", Long.valueOf(0));

		assertFalse(refdb.hasRefs(expectations));
	}

}
