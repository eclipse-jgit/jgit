/*
 * Copyright (C) 2018 Ericsson
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

public class GcDeleteEmptyRefsFoldersTest extends GcTestCase {
	private static final String REF_FOLDER_01 = "A/B/01";
	private static final String REF_FOLDER_02 = "C/D/02";

	private Path refsDir;
	private Path heads;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		refsDir = Paths.get(repo.getDirectory().getAbsolutePath())
				.resolve("refs");
		heads = refsDir.resolve("heads");
	}

	@Test
	public void emptyRefFoldersAreDeleted() throws Exception {
		FileTime fileTime = FileTime.from(Instant.now().minusSeconds(31));
		Path refDir01 = Files.createDirectories(heads.resolve(REF_FOLDER_01));
		Path refDir02 = Files.createDirectories(heads.resolve(REF_FOLDER_02));
		setLastModifiedTime(fileTime, heads, REF_FOLDER_01);
		setLastModifiedTime(fileTime, heads, REF_FOLDER_02);
		assertTrue(refDir01.toFile().exists());
		assertTrue(refDir02.toFile().exists());
		gc.gc();

		assertFalse(refDir01.toFile().exists());
		assertFalse(refDir01.getParent().toFile().exists());
		assertFalse(refDir01.getParent().getParent().toFile().exists());
		assertFalse(refDir02.toFile().exists());
		assertFalse(refDir02.getParent().toFile().exists());
		assertFalse(refDir02.getParent().getParent().toFile().exists());
	}

	private void setLastModifiedTime(FileTime fileTime, Path path, String folder) throws IOException {
		long numParents = folder.chars().filter(c -> c == '/').count();
		Path folderPath = path.resolve(folder);
		for(int folderLevel = 0; folderLevel <= numParents; folderLevel ++ ) {
			Files.setLastModifiedTime(folderPath, fileTime);
			folderPath = folderPath.getParent();
		}
	}

	@Test
	public void emptyRefFoldersAreKeptIfTheyAreTooRecent()
			throws Exception {
		Path refDir01 = Files.createDirectories(heads.resolve(REF_FOLDER_01));
		Path refDir02 = Files.createDirectories(heads.resolve(REF_FOLDER_02));
		assertTrue(refDir01.toFile().exists());
		assertTrue(refDir02.toFile().exists());
		gc.gc();

		assertTrue(refDir01.toFile().exists());
		assertTrue(refDir02.toFile().exists());
	}

	@Test
	public void nonEmptyRefsFoldersAreKept() throws Exception {
		Path refDir01 = Files.createDirectories(heads.resolve(REF_FOLDER_01));
		Path refDir02 = Files.createDirectories(heads.resolve(REF_FOLDER_02));
		Path ref01 = Files.createFile(refDir01.resolve("ref01"));
		Path ref02 = Files.createFile(refDir01.resolve("ref02"));
		assertTrue(refDir01.toFile().exists());
		assertTrue(refDir02.toFile().exists());
		assertTrue(ref01.toFile().exists());
		assertTrue(ref02.toFile().exists());
		gc.gc();
		assertTrue(refDir01.toFile().exists());
		assertTrue(refDir02.toFile().exists());
		assertTrue(ref01.toFile().exists());
		assertTrue(ref02.toFile().exists());
	}
}