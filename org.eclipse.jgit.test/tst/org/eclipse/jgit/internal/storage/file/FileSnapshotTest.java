/*
 * Copyright (C) 2010, Robin Rosenberg
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
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileSnapshotTest {

	private List<File> files = new ArrayList<File>();

	private File trash;

	@Before
	public void setUp() throws Exception {
		trash = File.createTempFile("tmp_", "");
		trash.delete();
		assertTrue("mkdir " + trash, trash.mkdir());
	}

	@Before
	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	private static void waitNextSec(File f) {
		long initialLastModified = f.lastModified();
		do {
			f.setLastModified(System.currentTimeMillis());
		} while (f.lastModified() == initialLastModified);
	}

	/**
	 * Change data and time stamp.
	 *
	 * @throws Exception
	 */
	@Test
	public void testActuallyIsModifiedTrivial() throws Exception {
		File f1 = createFile("simple");
		waitNextSec(f1);
		FileSnapshot save = FileSnapshot.save(f1);
		append(f1, (byte) 'x');
		waitNextSec(f1);
		assertTrue(save.isModified(f1));
	}

	/**
	 * Check that file is modified by looking at its size.
	 *
	 * @throws Exception
	 */
	@Test
	public void tesIsModifiedBySameLastModifiedAndDifferentSize() throws Exception {
		File f1 = createFile("foo", "lorem".getBytes());
		File f2 = createFile("bar", "lorem ipsum".getBytes());

		long now = System.currentTimeMillis();
		f1.setLastModified(f2.lastModified()); // Make sure f1 and f2 have the same lastModified
		FileSnapshot save = FileSnapshot.save(f1);

		Thread.sleep(3000L);

		assertEquals(save.lastModified(), f2.lastModified());
		assertTrue(save.isModified(f2));
	}

	/**
	 * Create a file, wait long enough and verify that it has not been modified.
	 * 3.5 seconds mean any difference between file system timestamp and system
	 * clock should be significant.
	 *
	 * @throws Exception
	 */
	@Test
	public void testOldFile() throws Exception {
		File f1 = createFile("oldfile");
		waitNextSec(f1);
		FileSnapshot save = FileSnapshot.save(f1);
		Thread.sleep(3500);
		assertFalse(save.isModified(f1));
	}

	/**
	 * Create a file, but don't wait long enough for the difference between file
	 * system clock and system clock to be significant. Assume the file may have
	 * been modified. It may have been, but the clock alone cannot determine
	 * this
	 *
	 * @throws Exception
	 */
	@Test
	public void testNewFileWithWait() throws Exception {
		File f1 = createFile("newfile");
		waitNextSec(f1);
		FileSnapshot save = FileSnapshot.save(f1);
		Thread.sleep(1500);
		assertTrue(save.isModified(f1));
	}

	/**
	 * Same as {@link #testNewFileWithWait()} but do not wait at all
	 *
	 * @throws Exception
	 */
	@Test
	public void testNewFileNoWait() throws Exception {
		File f1 = createFile("newfile");
		waitNextSec(f1);
		FileSnapshot save = FileSnapshot.save(f1);
		Thread.sleep(1500);
		assertTrue(save.isModified(f1));
	}

	private File createFile(String string) throws IOException {
		trash.mkdirs();
		File f = File.createTempFile(string, "tdat", trash);
		files.add(f);
		return f;
	}

	private File createFile(String string, byte[] content) throws IOException {
		File f = createFile(string);
		append(f, content);
		return f;
	}

	private static void append(File f, byte b) throws IOException {
		append(f, new byte[] { b });
	}

	private static void append(File f, byte[] content) throws IOException {
		FileOutputStream os = new FileOutputStream(f, true);
		try {
			for (byte b : content) {
				os.write(b);
			}
		} finally {
			os.close();
		}
	}

}
