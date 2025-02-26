/*
 * Copyright (C) 2012, 2025 Google Inc. and others
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class AddTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testAddNothing() throws Exception {
		assertThrows(Die.class, () -> execute("git add"));
	}

	@Test
	public void testAddUsage() throws Exception {
		execute("git add --help");
	}

	@Test
	public void testAddInvalidOptionCombinations() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		assertThrows(Die.class, () -> execute("git add -u -A greeting"));
		assertThrows(Die.class,
				() -> execute("git add -u --ignore-removed greeting"));
		// --renormalize implies -u
		assertThrows(Die.class,
				() -> execute("git add --renormalize --all greeting"));
	}

	@Test
	public void testAddAFile() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}

	@Test
	public void testAddFileTwice() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}

	@Test
	public void testAddAlreadyAdded() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		git.add().addFilepattern("greeting").call();
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
	}

	@Test
	public void testAddDeleted() throws Exception {
		File greeting = writeTrashFile("greeting", "Hello, world!");
		git.add().addFilepattern("greeting").call();
		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
		assertTrue(greeting.delete());
		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		cache = db.readDirCache();
		assertEquals(0, cache.getEntryCount());
	}

	@Test
	public void testAddDeleted2() throws Exception {
		File greeting = writeTrashFile("greeting", "Hello, world!");
		git.add().addFilepattern("greeting").call();
		DirCache cache = db.readDirCache();
		assertNotNull(cache.getEntry("greeting"));
		assertEquals(1, cache.getEntryCount());
		assertTrue(greeting.delete());
		assertArrayEquals(new String[] { "" }, //
				execute("git add -A"));

		cache = db.readDirCache();
		assertEquals(0, cache.getEntryCount());
	}
}
