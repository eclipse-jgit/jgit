/*
 * Copyright (C) 2017 Ericsson and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class GcOrphanFilesTest extends GcTestCase {
	private static final String PACK = "pack";

	private static final String BITMAP_File_1 = PACK + "-1.bitmap";

	private static final String IDX_File_2 = PACK + "-2.idx";

	private static final String IDX_File_malformed = PACK + "-1234idx";

	private static final String PACK_File_2 = PACK + "-2.pack";

	private static final String PACK_File_3 = PACK + "-3.pack";

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
