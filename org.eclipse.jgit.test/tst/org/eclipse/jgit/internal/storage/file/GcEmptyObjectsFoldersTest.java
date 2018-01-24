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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

public class GcEmptyObjectsFoldersTest extends GcTestCase {

	private static final String OBJ_DIR_01 = "01";
	private static final String OBJ_DIR_02 = "02";

	private Path objEmptyDirs;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		objEmptyDirs = Paths.get(repo.getObjectsDirectory().getAbsolutePath());
	}

	@Test
	public void emptyObjFoldersAreDeleted() throws Exception {
		Path objDir01 = Files
				.createDirectory(objEmptyDirs.resolve(OBJ_DIR_01));
		Path objDir02 = Files
				.createDirectory(objEmptyDirs.resolve(OBJ_DIR_02));
		assertTrue(objDir01.toFile().exists());
		assertTrue(objDir02.toFile().exists());
		gc.gc();
		assertFalse(objDir01.toFile().exists());
		assertFalse(objDir02.toFile().exists());
	}


	@Test
	public void nonEmptyObjFoldersAreKept() throws Exception {
		Path objDir01 = Files
				.createDirectory(objEmptyDirs.resolve(OBJ_DIR_01));
		Path objDir02 = Files
				.createDirectory(objEmptyDirs.resolve(OBJ_DIR_02));
		Path obj01 = Files.createFile(objDir01.resolve("obj01"));
		Path obj02 = Files.createFile(objDir02.resolve("obj02"));
		assertTrue(objDir01.toFile().exists());
		assertTrue(objDir02.toFile().exists());
		assertTrue(obj01.toFile().exists());
		assertTrue(obj02.toFile().exists());
		gc.gc();
		assertTrue(objDir01.toFile().exists());
		assertTrue(objDir02.toFile().exists());
		assertTrue(obj01.toFile().exists());
		assertTrue(obj02.toFile().exists());
	}

	@Test
	public void nonRefsFoldersAreKept() throws Exception {
		Path infoFolder = objEmptyDirs.resolve("info");
		Path packFolder = objEmptyDirs.resolve("pack");
		assertTrue(infoFolder.toFile().exists());
		assertTrue(packFolder.toFile().exists());
		gc.gc();
		assertTrue(infoFolder.toFile().exists());
		assertTrue(packFolder.toFile().exists());
	}
}
