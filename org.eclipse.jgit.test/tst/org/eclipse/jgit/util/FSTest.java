/*
 * Copyright (C) 2012-2013, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FSTest {
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
	public void fsTest() throws Exception {
		FS fs = FS.DETECTED;

		if ("FS_Win32" == fs.getClass().getName()) {
			assertFalse(fs.isCaseSensitive());
		} else if (SystemReader.getInstance().isMacOS()) {
			assertFalse(fs.isCaseSensitive());
		} else {
			assertTrue(fs.isCaseSensitive());
		}

		if ("FS_Win32" == fs.getClass().getName()) {
			assertFalse(fs.supportsSymlinks());
		} else {
			assertTrue(fs.supportsSymlinks());
		}

		File file = new File(trash, "a");
		assertFalse(fs.exists(file));
		FileUtils.createNewFile(file);

		assertTrue(fs.isFile(file));
		assertFalse(fs.isDirectory(file));
		assertFalse(fs.isSymLink(file));
		assertTrue(fs.canExecute(file));
		assertTrue(fs.exists(file));
		assertEquals(2, fs.length(file));

		File directory = new File(trash, "d");
		assertFalse(fs.exists(directory));
		FileUtils.mkdir(directory);

		assertFalse(fs.isFile(directory));
		assertTrue(fs.isDirectory(directory));
		assertFalse(fs.isSymLink(directory));
		assertTrue(fs.canExecute(directory));
		assertTrue(fs.exists(directory));
		assertEquals(2, fs.length(directory));

		File symlink = new File(trash, "s");
		File target = new File(trash, "target");
		assertFalse(fs.exists(symlink));
		assertFalse(fs.exists(target));
		fs.createSymLink(symlink, target.getName());

		assertFalse(fs.isFile(symlink));
		assertFalse(fs.isDirectory(symlink));
		assertTrue(fs.isSymLink(symlink));
		assertFalse(fs.canExecute(symlink));
		assertTrue(fs.exists(symlink));
		assertEquals(2, fs.length(symlink));

		String targetName = fs.readSymLink(symlink);
		assertEquals(target.getName(), targetName);

		// fs.delete(f)
		// fs.getAttributes(path)
		// fs.isCaseSensitive()
		// fs.isHidden(path)
		// fs.lastModified(f)
		// fs.normalize(file)
		// fs.normalize(name)
		// fs.resolve(dir, name)
		// fs.setExecute(f, canExec)
		// fs.setHidden(path, hidden)
		// fs.setLastModified(f, time)
		// fs.supportsExecute()


	}

	/**
	 * The old File methods traverse symbolic links and look at the targets.
	 * With symbolic links we usually want to modify/look at the link. For some
	 * reason the executable attribute seems to always look at the target, but
	 * for the other attributes like lastModified, hidden and exists we must
	 * differ between the link and the target.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSymlinkAttributes() throws Exception {
		FS fs = FS.DETECTED;
		File link = new File(trash, "ä");
		File target = new File(trash, "å");
		fs.createSymLink(link, "å");
		assertTrue(fs.exists(link));
		String targetName = fs.readSymLink(link);
		assertEquals("å", targetName);
		assertTrue(fs.lastModified(link) > 0);
		assertTrue(fs.exists(link));
		assertFalse(fs.canExecute(link));
		assertEquals(2, fs.length(link));
		assertFalse(fs.exists(target));
		assertFalse(fs.isFile(target));
		assertFalse(fs.isDirectory(target));
		assertFalse(fs.canExecute(target));

		RepositoryTestCase.fsTick(link);
		// Now create the link target
		FileUtils.createNewFile(target);
		assertTrue(fs.exists(link));
		assertTrue(fs.lastModified(link) > 0);
		assertTrue(fs.lastModified(target) > fs.lastModified(link));
		assertFalse(fs.canExecute(link));
		fs.setExecute(target, true);
		assertFalse(fs.canExecute(link));
		assertTrue(fs.canExecute(target));
	}

}
