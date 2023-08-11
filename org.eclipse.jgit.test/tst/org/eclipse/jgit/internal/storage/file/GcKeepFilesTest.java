/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.junit.Test;

public class GcKeepFilesTest extends GcTestCase {
	@Test
	public void testKeepFiles() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);

		Iterator<Pack> packIt = repo.getObjectDatabase().getPacks()
				.iterator();
		Pack singlePack = packIt.next();
		assertFalse(packIt.hasNext());
		PackFile keepFile = singlePack.getPackFile().create(PackExt.KEEP);
		assertFalse(keepFile.exists());
		assertTrue(keepFile.createNewFile());
		bb.commit().add("A", "A2").add("B", "B2").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		PackFile bitmapFile = singlePack.getPackFile().create(PackExt.BITMAP_INDEX);
		assertTrue(keepFile.exists());
		assertTrue(bitmapFile.delete());
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(12, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
		assertEquals(2, stats.numberOfBitmaps);

		// The objects in the packfile with a keep file are also present in the second packfile
		Iterator<Pack> packs = repo.getObjectDatabase().getPacks()
				.iterator();
		PackIndex ind1 = packs.next().getIndex();
		assertEquals(8, ind1.getObjectCount());
		PackIndex ind2 = packs.next().getIndex();
		assertEquals(4, ind2.getObjectCount());
		for (MutableEntry e: ind2)
			assertTrue("the following commit from a keep packfile was not found in the consolidated packfile: "
					+ e.toObjectId(), ind1.hasObject(e.toObjectId()));
	}

	@Test
	public void testKeepFileAllowsBitmapRemapping() throws Exception {
		TestRepository<FileRepository>.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").create();
		gc.gc();

		bb.commit().add("B", "B").create();
		gc.gc();

		// Create a keep file, simulating a write operation ongoing
		Iterator<Pack> packIt = repo.getObjectDatabase().getPacks()
				.iterator();
		Pack pack1 = packIt.next();
		assertNotNull(pack1);
		Pack singlePack = packIt.next();
		PackFile keepFile = singlePack.getPackFile().create(PackExt.KEEP);
		assertTrue(keepFile.createNewFile());

		// BOOM!
		gc.gc();
	}
}
