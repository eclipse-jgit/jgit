/*
 * Copyright (C) 2010, 2013 Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtilTest {

	private final File trash = new File(new File("target"), "trash");

	@Before
	public void setUp() throws Exception {
		assertTrue(trash.mkdirs());
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void testDeleteFile() throws IOException {
		File f = new File(trash, "test");
		FileUtils.createNewFile(f);
		FileUtils.delete(f);
		assertFalse(f.exists());

		try {
			FileUtils.delete(f);
			fail("deletion of non-existing file must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(f, FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("deletion of non-existing file must not fail with option SKIP_MISSING");
		}
	}

	@Test
	public void testDeleteRecursive() throws IOException {
		File f1 = new File(trash, "test/test/a");
		FileUtils.mkdirs(f1.getParentFile());
		FileUtils.createNewFile(f1);
		File f2 = new File(trash, "test/test/b");
		FileUtils.createNewFile(f2);
		File d = new File(trash, "test");
		FileUtils.delete(d, FileUtils.RECURSIVE);
		assertFalse(d.exists());

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE);
			fail("recursive deletion of non-existing directory must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("recursive deletion of non-existing directory must not fail with option SKIP_MISSING");
		}
	}

	@Test
	public void testDeleteRecursiveEmpty() throws IOException {
		File f1 = new File(trash, "test/test/a");
		File f2 = new File(trash, "test/a");
		File d1 = new File(trash, "test");
		File d2 = new File(trash, "test/test");
		File d3 = new File(trash, "test/b");
		FileUtils.mkdirs(f1.getParentFile());
		FileUtils.createNewFile(f2);
		FileUtils.createNewFile(f1);
		FileUtils.mkdirs(d3);

		// Cannot delete hierarchy since files exist
		try {
			FileUtils.delete(d1, FileUtils.EMPTY_DIRECTORIES_ONLY);
			fail("delete should fail");
		} catch (IOException e1) {
			try {
				FileUtils.delete(d1, FileUtils.EMPTY_DIRECTORIES_ONLY|FileUtils.RECURSIVE);
				fail("delete should fail");
			} catch (IOException e2) {
				// Everything still there
				assertTrue(f1.exists());
				assertTrue(f2.exists());
				assertTrue(d1.exists());
				assertTrue(d2.exists());
				assertTrue(d3.exists());
			}
		}

		// setup: delete files, only directories left
		assertTrue(f1.delete());
		assertTrue(f2.delete());

		// Shall not delete hierarchy without recursive
		try {
			FileUtils.delete(d1, FileUtils.EMPTY_DIRECTORIES_ONLY);
			fail("delete should fail");
		} catch (IOException e2) {
			// Everything still there
			assertTrue(d1.exists());
			assertTrue(d2.exists());
			assertTrue(d3.exists());
		}

		// Now delete the empty hierarchy
		FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY
				| FileUtils.RECURSIVE);
		assertFalse(d2.exists());

		// Will fail to delete non-existing without SKIP_MISSING
		try {
			FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY);
			fail("Cannot delete non-existent entity");
		} catch (IOException e) {
			// ok
		}

		// ..with SKIP_MISSING there is no exception
		FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY
				| FileUtils.SKIP_MISSING);
		FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY
				| FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);

		// essentially the same, using IGNORE_ERRORS
		FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY
				| FileUtils.IGNORE_ERRORS);
		FileUtils.delete(d2, FileUtils.EMPTY_DIRECTORIES_ONLY
				| FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
	}

	@Test
	public void testDeleteRecursiveEmptyNeedsToCheckFilesFirst()
			throws IOException {
		File d1 = new File(trash, "test");
		File d2 = new File(trash, "test/a");
		File d3 = new File(trash, "test/b");
		File f1 = new File(trash, "test/c");
		File d4 = new File(trash, "test/d");
		FileUtils.mkdirs(d1);
		FileUtils.mkdirs(d2);
		FileUtils.mkdirs(d3);
		FileUtils.mkdirs(d4);
		FileUtils.createNewFile(f1);

		// Cannot delete hierarchy since file exists
		try {
			FileUtils.delete(d1, FileUtils.EMPTY_DIRECTORIES_ONLY
					| FileUtils.RECURSIVE);
			fail("delete should fail");
		} catch (IOException e) {
			// Everything still there
			assertTrue(f1.exists());
			assertTrue(d1.exists());
			assertTrue(d2.exists());
			assertTrue(d3.exists());
			assertTrue(d4.exists());
		}
	}

	@Test
	public void testDeleteRecursiveEmptyDirectoriesOnlyButIsFile()
			throws IOException {
		File f1 = new File(trash, "test/test/a");
		FileUtils.mkdirs(f1.getParentFile());
		FileUtils.createNewFile(f1);
		try {
			FileUtils.delete(f1, FileUtils.EMPTY_DIRECTORIES_ONLY);
			fail("delete should fail");
		} catch (IOException e) {
			assertTrue(f1.exists());
		}
	}

	@Test
	public void testMkdir() throws IOException {
		File d = new File(trash, "test");
		FileUtils.mkdir(d);
		assertTrue(d.exists() && d.isDirectory());

		try {
			FileUtils.mkdir(d);
			fail("creation of existing directory must fail");
		} catch (IOException e) {
			// expected
		}

		FileUtils.mkdir(d, true);
		assertTrue(d.exists() && d.isDirectory());

		assertTrue(d.delete());
		File f = new File(trash, "test");
		FileUtils.createNewFile(f);
		try {
			FileUtils.mkdir(d);
			fail("creation of directory having same path as existing file must"
					+ " fail");
		} catch (IOException e) {
			// expected
		}
		assertTrue(f.delete());
	}

	@Test
	public void testMkdirs() throws IOException {
		File root = new File(trash, "test");
		assertTrue(root.mkdir());

		File d = new File(root, "test/test");
		FileUtils.mkdirs(d);
		assertTrue(d.exists() && d.isDirectory());

		try {
			FileUtils.mkdirs(d);
			fail("creation of existing directory hierarchy must fail");
		} catch (IOException e) {
			// expected
		}

		FileUtils.mkdirs(d, true);
		assertTrue(d.exists() && d.isDirectory());

		FileUtils.delete(root, FileUtils.RECURSIVE);
		File f = new File(trash, "test");
		FileUtils.createNewFile(f);
		try {
			FileUtils.mkdirs(d);
			fail("creation of directory having path conflicting with existing"
					+ " file must fail");
		} catch (IOException e) {
			// expected
		}
		assertTrue(f.delete());
	}

	@Test
	public void testCreateNewFile() throws IOException {
		File f = new File(trash, "x");
		FileUtils.createNewFile(f);
		assertTrue(f.exists());

		try {
			FileUtils.createNewFile(f);
			fail("creation of already existing file must fail");
		} catch (IOException e) {
			// expected
		}

		FileUtils.delete(f);
	}

	@Test
	public void testDeleteEmptyTreeOk() throws IOException {
		File t = new File(trash, "t");
		FileUtils.mkdir(t);
		FileUtils.mkdir(new File(t, "d"));
		FileUtils.mkdir(new File(new File(t, "d"), "e"));
		FileUtils.delete(t, FileUtils.EMPTY_DIRECTORIES_ONLY | FileUtils.RECURSIVE);
		assertFalse(t.exists());
	}

	@Test
	public void testDeleteNotEmptyTreeNotOk() throws IOException {
		File t = new File(trash, "t");
		FileUtils.mkdir(t);
		FileUtils.mkdir(new File(t, "d"));
		File f = new File(new File(t, "d"), "f");
		FileUtils.createNewFile(f);
		FileUtils.mkdir(new File(new File(t, "d"), "e"));
		try {
			FileUtils.delete(t, FileUtils.EMPTY_DIRECTORIES_ONLY | FileUtils.RECURSIVE);
			fail("expected failure to delete f");
		} catch (IOException e) {
			assertThat(e.getMessage(), endsWith(f.getAbsolutePath()));
		}
		assertTrue(t.exists());
	}

	@Test
	public void testDeleteNotEmptyTreeNotOkButIgnoreFail() throws IOException {
		File t = new File(trash, "t");
		FileUtils.mkdir(t);
		FileUtils.mkdir(new File(t, "d"));
		File f = new File(new File(t, "d"), "f");
		FileUtils.createNewFile(f);
		File e = new File(new File(t, "d"), "e");
		FileUtils.mkdir(e);
		FileUtils.delete(t, FileUtils.EMPTY_DIRECTORIES_ONLY | FileUtils.RECURSIVE
				| FileUtils.IGNORE_ERRORS);
		// Should have deleted as much as possible, but not all
		assertTrue(t.exists());
		assertTrue(f.exists());
		assertFalse(e.exists());
	}

	@Test
	public void testRenameOverNonExistingFile() throws IOException {
		File d = new File(trash, "d");
		FileUtils.mkdirs(d);
		File f1 = new File(trash, "d/f");
		File f2 = new File(trash, "d/g");
		JGitTestUtil.write(f1, "f1");
		// test
		FileUtils.rename(f1, f2);
		assertFalse(f1.exists());
		assertTrue(f2.exists());
		assertEquals("f1", JGitTestUtil.read(f2));
	}

	@Test
	public void testRenameOverExistingFile() throws IOException {
		File d = new File(trash, "d");
		FileUtils.mkdirs(d);
		File f1 = new File(trash, "d/f");
		File f2 = new File(trash, "d/g");
		JGitTestUtil.write(f1, "f1");
		JGitTestUtil.write(f2, "f2");
		// test
		FileUtils.rename(f1, f2);
		assertFalse(f1.exists());
		assertTrue(f2.exists());
		assertEquals("f1", JGitTestUtil.read(f2));
	}

	@Test
	public void testRenameOverExistingNonEmptyDirectory() throws IOException {
		File d = new File(trash, "d");
		FileUtils.mkdirs(d);
		File f1 = new File(trash, "d/f");
		File f2 = new File(trash, "d/g");
		File d1 = new File(trash, "d/g/h/i");
		File f3 = new File(trash, "d/g/h/f");
		FileUtils.mkdirs(d1);
		JGitTestUtil.write(f1, "f1");
		JGitTestUtil.write(f3, "f3");
		// test
		try {
			FileUtils.rename(f1, f2);
			fail("rename to non-empty directory should fail");
		} catch (IOException e) {
			assertEquals("f1", JGitTestUtil.read(f1)); // untouched source
			assertEquals("f3", JGitTestUtil.read(f3)); // untouched
			// empty directories within f2 may or may not have been deleted
		}
	}

	@Test
	public void testRenameOverExistingEmptyDirectory() throws IOException {
		File d = new File(trash, "d");
		FileUtils.mkdirs(d);
		File f1 = new File(trash, "d/f");
		File f2 = new File(trash, "d/g");
		File d1 = new File(trash, "d/g/h/i");
		FileUtils.mkdirs(d1);
		JGitTestUtil.write(f1, "f1");
		// test
		FileUtils.rename(f1, f2);
		assertFalse(f1.exists());
		assertTrue(f2.exists());
		assertEquals("f1", JGitTestUtil.read(f2));
	}
}
