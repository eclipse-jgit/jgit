/*
 * Copyright (C) 2010, 2013 Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.regex.Matcher;

import javax.management.remote.JMXProviderException;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class FileUtilsTest {
	private static final String MSG = "Stale file handle";

	private static final String SOME_ERROR_MSG = "some error message";

	private static final IOException IO_EXCEPTION = new UnsupportedEncodingException(
			MSG);

	private static final IOException IO_EXCEPTION_WITH_CAUSE = new RemoteException(
			SOME_ERROR_MSG,
			new JMXProviderException(SOME_ERROR_MSG, IO_EXCEPTION));

	private File trash;

	@Before
	public void setUp() throws Exception {
		trash = File.createTempFile("tmp_", "");
		trash.delete();
		assertTrue("mkdir " + trash, trash.mkdir());
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
			assertTrue(e.getMessage().endsWith(f.getAbsolutePath()));
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

	@Test
	public void testCreateSymlink() throws IOException {
		FS fs = FS.DETECTED;
		// show test as ignored if the FS doesn't support symlinks
		Assume.assumeTrue(fs.supportsSymlinks());
		fs.createSymLink(new File(trash, "x"), "y");
		String target = fs.readSymLink(new File(trash, "x"));
		assertEquals("y", target);
	}

	@Test
	public void testCreateSymlinkOverrideExisting() throws IOException {
		FS fs = FS.DETECTED;
		// show test as ignored if the FS doesn't support symlinks
		Assume.assumeTrue(fs.supportsSymlinks());
		File file = new File(trash, "x");
		fs.createSymLink(file, "y");
		String target = fs.readSymLink(file);
		assertEquals("y", target);
		fs.createSymLink(file, "z");
		target = fs.readSymLink(file);
		assertEquals("z", target);
	}

	@Test
	public void testRelativize_doc() {
		// This is the example from the javadoc
		String base = toOSPathString("c:\\Users\\jdoe\\eclipse\\git\\project");
		String other = toOSPathString("c:\\Users\\jdoe\\eclipse\\git\\another_project\\pom.xml");
		String expected = toOSPathString("..\\another_project\\pom.xml");

		String actual = FileUtils.relativizeNativePath(base, other);
		assertEquals(expected, actual);
	}

	@Test
	public void testRelativize_mixedCase() {
		SystemReader systemReader = SystemReader.getInstance();
		String base = toOSPathString("C:\\git\\jgit");
		String other = toOSPathString("C:\\Git\\test\\d\\f.txt");
		String expectedCaseInsensitive = toOSPathString("..\\test\\d\\f.txt");
		String expectedCaseSensitive = toOSPathString("..\\..\\Git\\test\\d\\f.txt");

		if (systemReader.isWindows()) {
			String actual = FileUtils.relativizeNativePath(base, other);
			assertEquals(expectedCaseInsensitive, actual);
		} else if (systemReader.isMacOS()) {
			String actual = FileUtils.relativizeNativePath(base, other);
			assertEquals(expectedCaseInsensitive, actual);
		} else {
			String actual = FileUtils.relativizeNativePath(base, other);
			assertEquals(expectedCaseSensitive, actual);
		}
	}

	@Test
	public void testRelativize_scheme() {
		String base = toOSPathString("file:/home/eclipse/runtime-New_configuration/project_1/file.java");
		String other = toOSPathString("file:/home/eclipse/runtime-New_configuration/project");
		// 'file.java' is treated as a folder
		String expected = toOSPathString("../../project");

		String actual = FileUtils.relativizeNativePath(base, other);
		assertEquals(expected, actual);
	}

	@Test
	public void testRelativize_equalPaths() {
		String base = toOSPathString("file:/home/eclipse/runtime-New_configuration/project_1");
		String other = toOSPathString("file:/home/eclipse/runtime-New_configuration/project_1");
		String expected = "";

		String actual = FileUtils.relativizeNativePath(base, other);
		assertEquals(expected, actual);
	}

	@Test
	public void testRelativize_whitespaces() {
		String base = toOSPathString("/home/eclipse 3.4/runtime New_configuration/project_1");
		String other = toOSPathString("/home/eclipse 3.4/runtime New_configuration/project_1/file");
		String expected = "file";

		String actual = FileUtils.relativizeNativePath(base, other);
		assertEquals(expected, actual);
	}

	@Test
	public void testDeleteSymlinkToDirectoryDoesNotDeleteTarget()
			throws IOException {
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = FS.DETECTED;
		File dir = new File(trash, "dir");
		File file = new File(dir, "file");
		File link = new File(trash, "link");
		FileUtils.mkdirs(dir);
		FileUtils.createNewFile(file);
		fs.createSymLink(link, "dir");
		FileUtils.delete(link, FileUtils.RECURSIVE);
		assertFalse(link.exists());
		assertTrue(dir.exists());
		assertTrue(file.exists());
	}

	@Test
	public void testAtomicMove() throws IOException {
		File src = new File(trash, "src");
		Files.createFile(src.toPath());
		File dst = new File(trash, "dst");
		FileUtils.rename(src, dst, StandardCopyOption.ATOMIC_MOVE);
		assertFalse(Files.exists(src.toPath()));
		assertTrue(Files.exists(dst.toPath()));
	}

	private String toOSPathString(String path) {
		return path.replaceAll("/|\\\\",
				Matcher.quoteReplacement(File.separator));
	}

	@Test
	public void testIsStaleFileHandleWithDirectCause() throws Exception {
		assertTrue(FileUtils.isStaleFileHandle(IO_EXCEPTION));
	}

	@Test
	public void testIsStaleFileHandleWithIndirectCause() throws Exception {
		assertFalse(
				FileUtils.isStaleFileHandle(IO_EXCEPTION_WITH_CAUSE));
	}

	@Test
	public void testIsStaleFileHandleInCausalChainWithDirectCause()
			throws Exception {
		assertTrue(
				FileUtils.isStaleFileHandleInCausalChain(IO_EXCEPTION));
	}

	@Test
	public void testIsStaleFileHandleInCausalChainWithIndirectCause()
			throws Exception {
		assertTrue(FileUtils
				.isStaleFileHandleInCausalChain(IO_EXCEPTION_WITH_CAUSE));
	}
}
