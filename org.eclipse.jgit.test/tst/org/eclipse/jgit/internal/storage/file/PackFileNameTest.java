/*
 * Copyright (c) 2021, The Linux Foundation
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class PackFileNameTest {
	private static final String TEST_ID = "0123456789012345678901234567890123456789";

	private static final String PREFIX = "pack-";

	private static final String OLD_PREFIX = "old-";

	private static final String OLD_PACK = PREFIX + TEST_ID + "." + OLD_PREFIX
			+ PackExt.PACK.getExtension();
	private static final File TEST_PACK_DIR = new File(
			"/path/to/repo.git/objects/pack");

	private static final File TEST_PRESERVED_DIR = new File(
			TEST_PACK_DIR, "preserved");

	private static final File TEST_PACK_FILE_NO_EXT = new File(
			TEST_PACK_DIR, PREFIX + TEST_ID);

	private static final PackFileName TEST_PACKFILENAME_NO_EXT = new PackFileName(
			TEST_PACK_FILE_NO_EXT);

	@Test
	public void packFileNameIsSameFromFileOrDir() throws Exception {
		File pack = new File(TEST_PACK_DIR, PREFIX + TEST_ID);
		PackFileName pfn = new PackFileName(pack);
		PackFileName pfnFromDir = TEST_PACKFILENAME_NO_EXT;
		assertEquals(pfn.getName(), pfnFromDir.getName());
	}

	@Test
	public void idIsSameFromFileWithOrWithoutExt() throws Exception {
		PackFileName packWithExt = new PackFileName(
				new File(TEST_PACK_DIR,
						PREFIX + TEST_ID + "." + PackExt.PACK.getExtension()));
		assertEquals(packWithExt.getId(), TEST_PACKFILENAME_NO_EXT.getId());
	}

	@Test
	public void idIsSameFromFileWithOrWithoutPrefix() throws Exception {
		PackFileName packWithoutPrefix = new PackFileName(
				new File(TEST_PACK_DIR, TEST_ID));
		assertEquals(packWithoutPrefix.getId(),
				TEST_PACKFILENAME_NO_EXT.getId());
	}

	@Test
	public void canCreatePreservedFromFile() throws Exception {
		PackFileName preserved = new PackFileName(
				new File(TEST_PRESERVED_DIR, OLD_PACK));
		assertTrue(preserved.getName().contains(OLD_PACK));
		assertEquals(preserved.getId(), TEST_ID);
		assertEquals(preserved.getPackExt(), PackExt.PACK);
	}

	@Test
	public void canCreatePreservedFromDirAndName() throws Exception {
		PackFileName preserved = new PackFileName(TEST_PRESERVED_DIR, OLD_PACK);
		assertTrue(preserved.getName().contains(OLD_PACK));
		assertEquals(preserved.getId(), TEST_ID);
		assertEquals(preserved.getPackExt(), PackExt.PACK);
	}

	@Test
	public void cannotCreatePreservedNoExtFromNonPreservedNoExt()
			throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> TEST_PACKFILENAME_NO_EXT
						.createForDirectory(TEST_PRESERVED_DIR, true));
	}

	@Test
	public void canCreateAnyExtFromAnyExt() throws Exception {
		for (PackExt from : PackExt.values()) {
			PackFileName dotFrom = TEST_PACKFILENAME_NO_EXT.create(from);
			for (PackExt to : PackExt.values()) {
				PackFileName dotTo = dotFrom.create(to);
				File expected = new File(TEST_PACK_DIR,
						PREFIX + TEST_ID + "." + to.getExtension());
				assertEquals(dotTo.getPackExt(), to);
				assertEquals(dotFrom.getId(), dotTo.getId());
				assertEquals(expected.getName(), dotTo.getName());
			}
		}
	}

	@Test
	public void canCreatePreservedFromAnyExt() throws Exception {
		for (PackExt ext : PackExt.values()) {
			PackFileName nonPreserved = TEST_PACKFILENAME_NO_EXT.create(ext);
			PackFileName preserved = nonPreserved
					.createForDirectory(TEST_PRESERVED_DIR, true);
			File expected = new File(TEST_PRESERVED_DIR,
					PREFIX + TEST_ID + "." + OLD_PREFIX + ext.getExtension());
			assertEquals(preserved.getName(), expected.getName());
			assertEquals(preserved.getId(), TEST_ID);
			assertEquals(preserved.getPackExt(), nonPreserved.getPackExt());
		}
	}

	@Test
	public void canCreateAnyPreservedExtFromAnyPreservedExt() throws Exception {
		// Preserved PackFileNames must have an extension
		PackFileName preserved = new PackFileName(TEST_PRESERVED_DIR, OLD_PACK);
		for (PackExt from : PackExt.values()) {
			PackFileName preservedWithExt = preserved.create(from);
			for (PackExt to : PackExt.values()) {
				PackFileName preservedNewExt = preservedWithExt.create(to);
				File expected = new File(TEST_PRESERVED_DIR,
						PREFIX + TEST_ID + "." + OLD_PREFIX
								+ to.getExtension());
				assertEquals(preservedNewExt.getPackExt(), to);
				assertEquals(preservedWithExt.getId(), preservedNewExt.getId());
				assertEquals(preservedNewExt.getName(), expected.getName());
			}
		}
	}

	@Test
	public void canCreateNonPreservedFromAnyPreservedExt() throws Exception {
		// Preserved PackFileNames must have an extension
		PackFileName preserved = new PackFileName(TEST_PRESERVED_DIR, OLD_PACK);
		for (PackExt ext : PackExt.values()) {
			PackFileName preservedWithExt = preserved.create(ext);
			PackFileName nonPreserved = preservedWithExt
					.createForDirectory(TEST_PACK_DIR, false);
			File expected = new File(TEST_PACK_DIR,
					PREFIX + TEST_ID + "." + ext.getExtension());
			assertEquals(nonPreserved.getName(), expected.getName());
			assertEquals(nonPreserved.getId(), TEST_ID);
			assertEquals(nonPreserved.getPackExt(),
					preservedWithExt.getPackExt());
		}
	}
}
