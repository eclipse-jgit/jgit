/*
 * Copyright (C) 2015, Google Inc.
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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.NotRevFilter;
import org.eclipse.jgit.revwalk.filter.ObjectFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ObjectWalkFilterTest {
	private TestRepository<InMemoryRepository> tr;
	private ObjectWalk rw;

	// 3 commits, 2 top-level trees, 4 subtrees, 3 blobs
	private static final int OBJECT_COUNT = 12;

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(new InMemoryRepository(
				new DfsRepositoryDescription("test")));
		rw = new ObjectWalk(tr.getRepository());

		rw.markStart(tr.branch("master").commit()
			.add("a/a", "1")
			.add("b/b", "2")
			.add("c/c", "3")
			.message("initial commit")

			.child()
			.rm("a/a")
			.add("a/A", "1")
			.message("capitalize a/a")

			.child()
			.rm("a/A")
			.add("a/a", "1")
			.message("make a/A lowercase again")
			.create());
	}

	@After
	public void tearDown() {
		rw.close();
		tr.getRepository().close();
	}

	private static class BlacklistObjectFilter extends ObjectFilter {
		final Set<AnyObjectId> badObjects;

		BlacklistObjectFilter(Set<AnyObjectId> badObjects) {
			this.badObjects = badObjects;
		}

		@Override
		public boolean include(ObjectWalk walker, AnyObjectId o) {
			return !badObjects.contains(o);
		}
	}

	private AnyObjectId resolve(String revstr) throws Exception {
		return tr.getRepository().resolve(revstr);
	}

	private int countObjects() throws IOException {
		int n = 0;
		while (rw.next() != null) {
			n++;
		}
		while (rw.nextObject() != null) {
			n++;
		}
		return n;
	}

	@Test
	public void testDefaultFilter() throws Exception {
		assertTrue("filter is ALL",
				rw.getObjectFilter() == ObjectFilter.ALL);
		assertEquals(OBJECT_COUNT, countObjects());
	}

	@Test
	public void testObjectFilterCanFilterOutBlob() throws Exception {
		AnyObjectId one = rw.parseAny(resolve("master:a/a"));
		AnyObjectId two = rw.parseAny(resolve("master:b/b"));
		rw.setObjectFilter(new BlacklistObjectFilter(Sets.of(one, two)));

		// 2 blobs filtered out
		assertEquals(OBJECT_COUNT - 2, countObjects());
	}

	@Test
	public void testFilteringCommitsHasNoEffect() throws Exception {
		AnyObjectId initial = rw.parseCommit(resolve("master^^"));
		rw.setObjectFilter(new BlacklistObjectFilter(Sets.of(initial)));
		assertEquals(OBJECT_COUNT, countObjects());
	}

	@Test
	public void testRevFilterAndObjectFilterCanCombine() throws Exception {
		AnyObjectId one = rw.parseAny(resolve("master:a/a"));
		AnyObjectId two = rw.parseAny(resolve("master:b/b"));
		rw.setObjectFilter(new BlacklistObjectFilter(Sets.of(one, two)));
		rw.setRevFilter(NotRevFilter.create(
				MessageRevFilter.create("capitalize")));

		// 2 blobs, one commit, two trees filtered out
		assertEquals(OBJECT_COUNT - 5, countObjects());
	}

	@Test
	public void testFilteringTreeFiltersSubtrees() throws Exception {
		AnyObjectId capitalizeTree = rw.parseAny(resolve("master^:"));
		rw.setObjectFilter(new BlacklistObjectFilter(
				Sets.of(capitalizeTree)));

		// trees "master^:" and "master^:a" filtered out
		assertEquals(OBJECT_COUNT - 2, countObjects());
	}

	@Test
	public void testFilteringTreeFiltersReferencedBlobs() throws Exception {
		AnyObjectId a1 = rw.parseAny(resolve("master:a"));
		AnyObjectId a2 = rw.parseAny(resolve("master^:a"));
		rw.setObjectFilter(new BlacklistObjectFilter(Sets.of(a1, a2)));

		// 2 trees, one blob filtered out
		assertEquals(OBJECT_COUNT - 3, countObjects());
	}
}
