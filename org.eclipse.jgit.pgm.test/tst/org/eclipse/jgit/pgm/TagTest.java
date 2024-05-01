/*
 * Copyright (C) 2012, Tomasz Zarna <tomasz.zarna@tasktop.com> and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class TagTest extends CLIRepositoryTestCase {
	private Git git;

	private RevCommit initialCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		initialCommit = git.commit().setMessage("initial commit").call();
	}

	@Test
	public void testTagTwice() throws Exception {
		git.tag().setName("test").call();
		writeTrashFile("file", "content");
		git.add().addFilepattern("file").call();
		git.commit().setMessage("commit").call();

		assertEquals("fatal: tag 'test' already exists",
				executeUnchecked("git tag test")[0]);
	}

	@Test
	public void testTagDelete() throws Exception {
		git.tag().setName("test").call();
		assertNotNull(git.getRepository().exactRef("refs/tags/test"));
		assertEquals("", executeUnchecked("git tag -d test")[0]);
		assertNull(git.getRepository().exactRef("refs/tags/test"));
	}

	@Test
	public void testTagDeleteFail() throws Exception {
		try {
			assertEquals("fatal: error: tag 'test' not found.",
					executeUnchecked("git tag -d test")[0]);
		} catch (Die e) {
			assertEquals("fatal: error: tag 'test' not found", e.getMessage());
		}
	}

	@Test
	public void testContains() throws Exception {
		/*      c3
		 *      |
		 * v2 - c2   b2 - v1
		 *      |    |
		 *      c1   b1
		 *       \   /
		 *         a
		 */
		try (TestRepository<Repository> r = new TestRepository<>(
				db)) {
			RevCommit b1 = r.commit(initialCommit);
			RevCommit b2 = r.commit(b1);
			RevCommit c1 = r.commit(initialCommit);
			RevCommit c2 = r.commit(c1);
			RevCommit c3 = r.commit(c2);
			r.update("refs/tags/v1", r.tag("v1", b2));
			r.update("refs/tags/v2", r.tag("v1.1", c2));

			assertArrayEquals(
					new String[] { "v1", "v2", "" },
					execute("git tag --contains " + initialCommit.name()));

			assertArrayEquals(new String[] { "v1", "" },
					execute("git tag --contains " + b1.name()));

			assertArrayEquals(new String[] { "v2", "" },
					execute("git tag --contains " + c1.name()));

			assertArrayEquals(new String[] { "" },
					execute("git tag --contains " + c3.name()));
		}
	}
}
