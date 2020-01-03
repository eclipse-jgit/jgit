/*
 * Copyright (C) 2010, 2012 Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class RmCommandTest extends RepositoryTestCase {

	private Git git;

	private static final String FILE = "test.txt";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile(FILE, "Hello world");
		git.add().addFilepattern(FILE).call();
		git.commit().setMessage("Initial commit").call();
	}

	@Test
	public void testRemove() throws JGitInternalException,
			IllegalStateException, IOException, GitAPIException {
		assertEquals("[test.txt, mode:100644, content:Hello world]",
				indexState(CONTENT));
		RmCommand command = git.rm();
		command.addFilepattern(FILE);
		command.call();
		assertEquals("", indexState(CONTENT));
	}

	@Test
	public void testRemoveCached() throws Exception {
		File newFile = writeTrashFile("new.txt", "new");
		git.add().addFilepattern(newFile.getName()).call();
		assertEquals("[new.txt, mode:100644][test.txt, mode:100644]",
				indexState(0));

		git.rm().setCached(true).addFilepattern(newFile.getName()).call();

		assertEquals("[test.txt, mode:100644]", indexState(0));
		assertTrue("File should not have been removed.", newFile.exists());
	}
}
