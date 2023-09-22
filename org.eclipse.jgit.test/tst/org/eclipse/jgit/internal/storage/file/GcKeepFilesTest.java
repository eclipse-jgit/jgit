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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);

		// check that no object is packed twice
		Iterator<Pack> packs = repo.getObjectDatabase().getPacks()
				.iterator();
		PackIndex ind1 = packs.next().getIndex();
		assertEquals(4, ind1.getObjectCount());
		PackIndex ind2 = packs.next().getIndex();
		assertEquals(4, ind2.getObjectCount());
		for (MutableEntry e: ind1)
			if (ind2.hasObject(e.toObjectId()))
				assertFalse(
						"the following object is in both packfiles: "
								+ e.toObjectId(),
						ind2.hasObject(e.toObjectId()));
	}

	@Test
	public void testKeepFileAllowsBitmapRemapping2() throws Exception {
		TestRepository<FileRepository>.BranchBuilder bb = tr
				.branch("refs/heads/master");
		bb.commit().add("A", "A").message("first").create();
		bb.commit().add("B", "B").message("second").create();
		// all good - just pack the objects
		gc.gc();

		TestRepository<Repository> clone = new TestRepository<>(
				cloneRepository(repo));
		// amend last commit to create new commit object and reuse tree and blob
		RevCommit amended = clone.amendRef("refs/heads/master")
				.message("amended").create();

		// materialise new pack on the parent repository
		clone.git().push().setForce(true).call();

		// create new repository to access pushed pack files
		FileRepository tmpRepo = new FileRepository(repo.getDirectory());
		Iterator<Pack> packIt = tmpRepo.getObjectDatabase().getPacks()
				.iterator();
		Pack pack1 = packIt.next();
		assertNotNull(pack1);
		// ensure that we have multiple pack files in repository
		assertTrue(packIt.hasNext());
		// ensure that we lock pack file that has amended commit
		assertTrue(pack1.hasObject(amended));
		PackFile keepFile = pack1.getPackFile().create(PackExt.KEEP);
		// create keep file
		assertTrue(keepFile.createNewFile());

		// BOOM
		gc.gc();
	}

	private Repository cloneRepository(Repository repo) throws Exception {
		return Git.cloneRepository()
				.setURI(repo.getDirectory().toURI().toString())
				.setDirectory(createUniqueTestGitDir(true)).call()
				.getRepository();
	}
}
