/*
 * Copyright (C) 2012 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AddTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	void testAddNothing() throws Exception {
		try {
			execute("git add");
			fail("Must die");
		} catch (Die e) {
			// expected, requires argument
		}
	}

	@Test
	void testAddUsage() throws Exception {
		execute("git add --help");
	}

	@Test
	void testAddAFile() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}

	@Test
	void testAddFileTwice() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}

	@Test
	void testAddAlreadyAdded() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		git.add().addFilepattern("greeting").call();
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}
}
