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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GcOrphanFilesTest extends GcTestCase {
	private static final String PACK = "pack";

	private static final String BITMAP_File_1 = PACK + "-1.bitmap";

	private static final String BITMAP_File_2 = PACK + "-2.bitmap";

	private static final String IDX_File_2 = PACK + "-2.idx";

	private static final String IDX_File_malformed = PACK + "-1234idx";

	private static final String KEEP_File_2 = PACK + "-2.keep";

	private static final String PACK_File_2 = PACK + "-2.pack";

	private static final String PACK_File_3 = PACK + "-3.pack";

	private File packDir;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		packDir = repo.getObjectDatabase().getPackDirectory();
	}

	@Test
	void bitmapAndIdxDeletedButPackNot() throws Exception {
		createFileInPackFolder(BITMAP_File_1);
		createFileInPackFolder(IDX_File_2);
		createFileInPackFolder(PACK_File_3);
		gc.gc().get();
		assertFalse(new File(packDir, BITMAP_File_1).exists());
		assertFalse(new File(packDir, IDX_File_2).exists());
		assertTrue(new File(packDir, PACK_File_3).exists());
	}

	@Test
	void bitmapDeletedButIdxAndPackNot() throws Exception {
		createFileInPackFolder(BITMAP_File_1);
		createFileInPackFolder(IDX_File_2);
		createFileInPackFolder(PACK_File_2);
		createFileInPackFolder(PACK_File_3);
		gc.gc().get();
		assertFalse(new File(packDir, BITMAP_File_1).exists());
		assertTrue(new File(packDir, IDX_File_2).exists());
		assertTrue(new File(packDir, PACK_File_2).exists());
		assertTrue(new File(packDir, PACK_File_3).exists());
	}

	@Test
	void malformedIdxNotDeleted() throws Exception {
		createFileInPackFolder(IDX_File_malformed);
		gc.gc().get();
		assertTrue(new File(packDir, IDX_File_malformed).exists());
	}

	@Test
	void keepPreventsDeletionOfIndexFilesForMissingPackFile()
			throws Exception {
		createFileInPackFolder(BITMAP_File_1);
		createFileInPackFolder(IDX_File_2);
		createFileInPackFolder(BITMAP_File_2);
		createFileInPackFolder(KEEP_File_2);
		createFileInPackFolder(PACK_File_3);
		gc.gc().get();
		assertFalse(new File(packDir, BITMAP_File_1).exists());
		assertTrue(new File(packDir, BITMAP_File_2).exists());
		assertTrue(new File(packDir, IDX_File_2).exists());
		assertTrue(new File(packDir, KEEP_File_2).exists());
		assertTrue(new File(packDir, PACK_File_3).exists());
	}

	private void createFileInPackFolder(String fileName) throws IOException {
		if (!packDir.exists() || !packDir.isDirectory()) {
			assertTrue(packDir.mkdirs());
		}
		assertTrue(new File(packDir, fileName).createNewFile());
	}

	@Test
	void noSuchPackFolder() throws Exception {
		assertTrue(packDir.delete());
		gc.gc().get();
	}
}
