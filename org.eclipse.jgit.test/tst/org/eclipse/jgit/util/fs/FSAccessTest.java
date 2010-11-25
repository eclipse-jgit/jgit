/*
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;

public class FSAccessTest extends LocalDiskRepositoryTestCase {
	private Repository db;

	private File trash;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
		trash = db.getWorkTree();
	}

	/**
	 * Test the native implementation if available. If you run the tests on a
	 * platform where we don't provide the native library set the System
	 * property org.eclipse.jgit.fs.native to false to skip this test
	 *
	 * @throws IOException
	 */
	public void test_lstat_native() throws IOException {
		if (Boolean.getBoolean("jgit.native.skip"))
			return;
		FSAccess fsa = new FSAccessNative();
		do_test_lstat(fsa, true);
	}

	public void test_lstat_java() throws IOException {
		// test the Java implementation
		FSAccess fsa = new FSAccessJava();
		do_test_lstat(fsa, false);
	}

	private boolean isPosix() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") == -1;
	}

	public void do_test_lstat(FSAccess fsa, boolean isNative)
			throws IOException {
		// test lstat for file
		File test = writeTrashFile("test/test.txt", "Hello Java");

		LStat stat = fsa.lstat(db.getFS(), test);

		// mtime should be > 0
		assertTrue(stat.getMTimeSec() > 0);
		// file size
		assertTrue(stat.getSize() == 10);
		assertTrue(stat.getMode() == FileMode.REGULAR_FILE);
		// test lstat features not supported by Java
		if (isNative) {
			// mtime should be always >= ctime
			assertTrue(stat.getMTimeSec() >= stat.getCTimeSec());
			if (isPosix()) {
				// at least these values should be > 0
				assertTrue(stat.getDevice() > 0);
				assertTrue(stat.getGroupId() > 0);
				assertTrue(stat.getUserId() > 0);
				assertTrue(stat.getInode() > 0);
			}
		}

		BufferedWriter out = new BufferedWriter(new FileWriter(test));
		out.write("Changed the text to see if lstat sees that");
		out.close();
		stat = fsa.lstat(db.getFS(), test);
		assertTrue(stat.getSize() == 42);

		// test lstat for directory
		File dir = test.getParentFile();
		stat = fsa.lstat(db.getFS(), dir);
		assertTrue(stat.getMode() == FileMode.TREE);

		// test lstat for executable file
		if (db.getFS().supportsExecute()) {
			db.getFS().setExecute(test, true);
			stat = fsa.lstat(db.getFS(), test);
			assertTrue(stat.getMode() == FileMode.EXECUTABLE_FILE);
		}

		// TODO add test for symbolic link as soon as we have JNI support for
		// creating symbolic links

		// TODO add tests for error conditions (ENOENT, ENOTDIR,EACCES)

		test.delete();
	}

	private File writeTrashFile(String name, String body) throws IOException {
		final File path = new File(trash, name);
		write(path, body);
		return path;
	}
}
