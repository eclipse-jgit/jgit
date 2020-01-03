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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class TagTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
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
}
