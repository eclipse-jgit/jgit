/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.dircache;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;

public class DirCacheEntryTest extends TestCase {
	public void testIsValidPath() {
		assertTrue(isValidPath("a"));
		assertTrue(isValidPath("a/b"));
		assertTrue(isValidPath("ab/cd/ef"));

		assertFalse(isValidPath(""));
		assertFalse(isValidPath("/a"));
		assertFalse(isValidPath("a//b"));
		assertFalse(isValidPath("ab/cd//ef"));
		assertFalse(isValidPath("a/"));
		assertFalse(isValidPath("ab/cd/ef/"));
		assertFalse(isValidPath("a\u0000b"));
	}

	private static boolean isValidPath(final String path) {
		return DirCacheEntry.isValidPath(Constants.encode(path));
	}

	public void testCreate_ByStringPath() {
		assertEquals("a", new DirCacheEntry("a").getPathString());
		assertEquals("a/b", new DirCacheEntry("a/b").getPathString());

		try {
			new DirCacheEntry("/a");
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid path: /a", err.getMessage());
		}
	}

	public void testCreate_ByStringPathAndStage() {
		DirCacheEntry e;

		e = new DirCacheEntry("a", 0);
		assertEquals("a", e.getPathString());
		assertEquals(0, e.getStage());

		e = new DirCacheEntry("a/b", 1);
		assertEquals("a/b", e.getPathString());
		assertEquals(1, e.getStage());

		e = new DirCacheEntry("a/c", 2);
		assertEquals("a/c", e.getPathString());
		assertEquals(2, e.getStage());

		e = new DirCacheEntry("a/d", 3);
		assertEquals("a/d", e.getPathString());
		assertEquals(3, e.getStage());

		try {
			new DirCacheEntry("/a", 1);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid path: /a", err.getMessage());
		}

		try {
			new DirCacheEntry("a", -11);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid stage -11 for path a", err.getMessage());
		}

		try {
			new DirCacheEntry("a", 4);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid stage 4 for path a", err.getMessage());
		}
	}

	public void testSetFileMode() {
		final DirCacheEntry e = new DirCacheEntry("a");

		assertEquals(0, e.getRawMode());

		e.setFileMode(FileMode.REGULAR_FILE);
		assertSame(FileMode.REGULAR_FILE, e.getFileMode());
		assertEquals(FileMode.REGULAR_FILE.getBits(), e.getRawMode());

		e.setFileMode(FileMode.EXECUTABLE_FILE);
		assertSame(FileMode.EXECUTABLE_FILE, e.getFileMode());
		assertEquals(FileMode.EXECUTABLE_FILE.getBits(), e.getRawMode());

		e.setFileMode(FileMode.SYMLINK);
		assertSame(FileMode.SYMLINK, e.getFileMode());
		assertEquals(FileMode.SYMLINK.getBits(), e.getRawMode());

		e.setFileMode(FileMode.GITLINK);
		assertSame(FileMode.GITLINK, e.getFileMode());
		assertEquals(FileMode.GITLINK.getBits(), e.getRawMode());

		try {
			e.setFileMode(FileMode.MISSING);
			fail("incorrectly accepted FileMode.MISSING");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid mode 0 for path a", err.getMessage());
		}

		try {
			e.setFileMode(FileMode.TREE);
			fail("incorrectly accepted FileMode.TREE");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid mode 40000 for path a", err.getMessage());
		}
	}
}
