/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.util.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.NativeLibrary;
import org.junit.Before;
import org.junit.Test;

public class FileAccessNativeTest extends LocalDiskRepositoryTestCase {
	private static boolean skipTest() {
		return NativeLibrary.isDisabled();
	}

	private Repository db;

	private File root;

	private FileAccessNative access;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		if (!skipTest()) {
			db = createWorkRepository();
			root = db.getWorkTree();
			access = new FileAccessNative();
		}
	}

	@Test
	public void testStatRegularFile() throws IOException {
		if (skipTest())
			return;

		File path = write("Hello Java");
		FileInfo stat = access.lstat(path);

		assertEquals(10, stat.length());
		assertEquals(path.lastModified() / 1000, stat.lastModifiedSeconds());
		assertTrue(stat.created() >= stat.lastModified());
		assertTrue(FileMode.REGULAR_FILE.equals(stat.mode()));
		final long created = stat.created();

		write(path, "Longer text to see if stat notices change.");
		path.setLastModified(System.currentTimeMillis() - 24 * 3600 * 1000L);
		stat = access.lstat(path);

		assertEquals(42, stat.length());
		assertEquals(path.lastModified() / 1000, stat.lastModifiedSeconds());
		assertEquals(created, stat.created());
		assertTrue(FileMode.REGULAR_FILE.equals(stat.mode()));

		if (isPosix()) {
			assertTrue(stat.device() > 0);
			assertTrue(stat.inode() > 0);
			assertTrue(stat.uid() > 0);
			assertTrue(stat.gid() > 0);
		}
	}

	@Test
	public void testStatDirectory() throws IOException {
		if (skipTest())
			return;

		File path = new File(root, "src");
		assertTrue("created " + path, path.mkdir());

		FileInfo stat = access.lstat(path);
		assertEquals(path.lastModified() / 1000, stat.lastModifiedSeconds());
		assertTrue(FileMode.TREE.equals(stat.mode()));

		if (isPosix()) {
			assertTrue(stat.device() > 0);
			assertTrue(stat.inode() > 0);
			assertTrue(stat.uid() > 0);
			assertTrue(stat.gid() > 0);
		}
	}

	@Test
	public void testMissingFile() throws IOException {
		if (skipTest())
			return;

		final File file = new File("this.not.here");
		try {
			access.lstat(file);
			fail("expected exception");
		} catch (NoSuchFileException noFile) {
			assertEquals(file.getPath(), noFile.getMessage());
		}
	}

	@Test
	public void testNotDirectory() throws IOException {
		if (skipTest())
			return;

		final File dir = write("a file");
		final File file = new File(dir, "this.not.here");
		try {
			access.lstat(file);
			fail("expected exception");
		} catch (NotDirectoryException notDir) {
			assertEquals(file.getPath(), notDir.getMessage());
		}
	}

	private static boolean isPosix() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") == -1;
	}
}
