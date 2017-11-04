/*
 * Copyright (C) 2017 Ericsson
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

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class GcOrphanFilesTest extends GcTestCase {
	private final static String PACK = "pack";

	private final static String BITMAP_File_1 = PACK + "-1.bitmap";

	private final static String IDX_File_2 = PACK + "-2.idx";

	private final static String IDX_File_malformed = PACK + "-1234idx";

	private final static String PACK_File_2 = PACK + "-2.pack";

	private final static String PACK_File_3 = PACK + "-3.pack";

	private File packDir;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		packDir = repo.getObjectDatabase().getPackDirectory();
	}

	@Test
	public void bitmapAndIdxDeletedButPackNot() throws Exception {
		createFileInPackFolder(BITMAP_File_1);
		createFileInPackFolder(IDX_File_2);
		createFileInPackFolder(PACK_File_3);
		gc.gc();
		assertFalse(new File(packDir, BITMAP_File_1).exists());
		assertFalse(new File(packDir, IDX_File_2).exists());
		assertTrue(new File(packDir, PACK_File_3).exists());
	}

	@Test
	public void bitmapDeletedButIdxAndPackNot() throws Exception {
		createFileInPackFolder(BITMAP_File_1);
		createFileInPackFolder(IDX_File_2);
		createFileInPackFolder(PACK_File_2);
		createFileInPackFolder(PACK_File_3);
		gc.gc();
		assertFalse(new File(packDir, BITMAP_File_1).exists());
		assertTrue(new File(packDir, IDX_File_2).exists());
		assertTrue(new File(packDir, PACK_File_2).exists());
		assertTrue(new File(packDir, PACK_File_3).exists());
	}

	@Test
	public void malformedIdxNotDeleted() throws Exception {
		createFileInPackFolder(IDX_File_malformed);
		gc.gc();
		assertTrue(new File(packDir, IDX_File_malformed).exists());
	}

	private void createFileInPackFolder(String fileName) throws IOException {
		if (!packDir.exists() || !packDir.isDirectory()) {
			assertTrue(packDir.mkdirs());
		}
		assertTrue(new File(packDir, fileName).createNewFile());
	}

	@Test
	public void noSuchPackFolder() throws Exception {
		assertTrue(packDir.delete());
		gc.gc();
	}
}
