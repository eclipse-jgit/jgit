/*
 * Copyright (C) 2010, Google Inc.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class FileRepositoryBuilderTest extends LocalDiskRepositoryTestCase {
	@Test
	public void testShouldAutomagicallyDetectGitDirectory() throws Exception {
		FileRepository r = createWorkRepository();
		File d = new File(r.getDirectory(), "sub-dir");
		FileUtils.mkdir(d);

		assertEquals(r.getDirectory(), new FileRepositoryBuilder()
				.findGitDir(d).getGitDir());
	}

	@Test
	public void emptyRepositoryFormatVersion() throws Exception {
		FileRepository r = createWorkRepository();
		FileBasedConfig config = r.getConfig();
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, "");
		config.save();

		new FileRepository(r.getDirectory());
	}

	@Test
	public void invalidRepositoryFormatVersion() throws Exception {
		FileRepository r = createWorkRepository();
		FileBasedConfig config = r.getConfig();
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, "notanumber");
		config.save();

		try {
			new FileRepository(r.getDirectory());
			fail("IllegalArgumentException not thrown");
		} catch (IllegalArgumentException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void unknownRepositoryFormatVersion() throws Exception {
		FileRepository r = createWorkRepository();
		FileBasedConfig config = r.getConfig();
		config.setLong(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 1);
		config.save();

		try {
			new FileRepository(r.getDirectory());
			fail("IOException not thrown");
		} catch (IOException e) {
			assertNotNull(e.getMessage());
		}
	}

	@SuppressWarnings("resource" /* java 7 */)
	@Test
	public void absoluteGitDirRef() throws Exception {
		FileRepository repo1 = createWorkRepository();
		File dir = createTempDirectory("dir");
		File dotGit = new File(dir, Constants.DOT_GIT);
		new FileWriter(dotGit).append(
				"gitdir: " + repo1.getDirectory().getAbsolutePath()).close();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		builder.setWorkTree(dir);
		builder.setMustExist(true);
		FileRepository repo2 = builder.build();

		assertEquals(repo1.getDirectory(), repo2.getDirectory());
		assertEquals(dir, repo2.getWorkTree());
	}

	@SuppressWarnings("resource" /* java 7 */)
	@Test
	public void relativeGitDirRef() throws Exception {
		FileRepository repo1 = createWorkRepository();
		File dir = new File(repo1.getWorkTree(), "dir");
		assertTrue(dir.mkdir());
		File dotGit = new File(dir, Constants.DOT_GIT);
		new FileWriter(dotGit).append("gitdir: ../" + Constants.DOT_GIT)
				.close();

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setWorkTree(dir);
		builder.setMustExist(true);
		FileRepository repo2 = builder.build();

		assertEquals(repo1.getDirectory(), repo2.getDirectory());
		assertEquals(dir, repo2.getWorkTree());
	}

	@SuppressWarnings("resource" /* java 7 */)
	@Test
	public void scanWithGitDirRef() throws Exception {
		FileRepository repo1 = createWorkRepository();
		File dir = createTempDirectory("dir");
		File dotGit = new File(dir, Constants.DOT_GIT);
		new FileWriter(dotGit).append(
				"gitdir: " + repo1.getDirectory().getAbsolutePath()).close();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		builder.setWorkTree(dir);
		builder.findGitDir(dir);
		assertEquals(repo1.getDirectory(), builder.getGitDir());
		builder.setMustExist(true);
		FileRepository repo2 = builder.build();

		assertEquals(repo1.getDirectory(), repo2.getDirectory());
		assertEquals(dir, repo2.getWorkTree());
	}
}
