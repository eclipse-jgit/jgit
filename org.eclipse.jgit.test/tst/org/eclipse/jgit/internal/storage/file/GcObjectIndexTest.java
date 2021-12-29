/*
 * Copyright (C) 2022, Google LLC. and others
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GcObjectIndexTest extends GcTestCase {

	@Test
	public void gc_2commits_noSizeLimit_blobsInIndex() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevBlob blobA1 = tr.blob("7-bytes");
		RevBlob blobA2 = tr.blob("11-bytes xx");
		RevBlob blobB1 = tr.blob("B");
		RevBlob blobB2 = tr.blob("B2");
		bb.commit().add("A", blobA1).add("B", blobB1).create();
		bb.commit().add("A", blobA2).add("B", blobB2).create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, 0);
		gc.gc().get();

		stats = gc.getStatistics();
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(4, stats.numberOfSizeIndexedObjects);

		assertTrue(getOnlyPack(repo).hasObjSizeIndex());
		Pack pack = getOnlyPack(repo);
		assertEquals(7, pack.getIndexedObjectSize(blobA1));
		assertEquals(11, pack.getIndexedObjectSize(blobA2));
		assertEquals(1, pack.getIndexedObjectSize(blobB1));
		assertEquals(2, pack.getIndexedObjectSize(blobB2));
	}

	@Test
	public void gc_2commits_sizeLimit_biggerBlobsInIndex() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevBlob blobA1 = tr.blob("7-bytes");
		RevBlob blobA2 = tr.blob("11-bytes xx");
		RevBlob blobB1 = tr.blob("B");
		RevBlob blobB2 = tr.blob("B2");
		bb.commit().add("A", blobA1).add("B", blobB1).create();
		bb.commit().add("A", blobA2).add("B", blobB2).create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, 5);
		gc.gc().get();

		stats = gc.getStatistics();
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(2, stats.numberOfSizeIndexedObjects);

		assertTrue(getOnlyPack(repo).hasObjSizeIndex());
		Pack pack = getOnlyPack(repo);
		assertEquals(7, pack.getIndexedObjectSize(blobA1));
		assertEquals(11, pack.getIndexedObjectSize(blobA2));
		assertEquals(-1, pack.getIndexedObjectSize(blobB1));
		assertEquals(-1, pack.getIndexedObjectSize(blobB2));
	}

	@Test
	public void gc_2commits_disableSizeIdx_noIdx() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevBlob blobA1 = tr.blob("7-bytes");
		RevBlob blobA2 = tr.blob("11-bytes xx");
		RevBlob blobB1 = tr.blob("B");
		RevBlob blobB2 = tr.blob("B2");
		bb.commit().add("A", blobA1).add("B", blobB1).create();
		bb.commit().add("A", blobA2).add("B", blobB2).create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, -1);
		gc.gc().get();


		stats = gc.getStatistics();
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(0, stats.numberOfSizeIndexedObjects);
	}

	@Test
	public void gc_alreadyPacked_noChanges()
			throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, 0);
		gc.gc().get();

		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertTrue(getOnlyPack(repo).hasObjSizeIndex());
		assertEquals(2, stats.numberOfSizeIndexedObjects);

		// Do the gc again and check that it hasn't changed anything
		gc.gc().get();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertTrue(getOnlyPack(repo).hasObjSizeIndex());
		assertEquals(2, stats.numberOfSizeIndexedObjects);
	}

	@Test
	public void gc_twoReachableCommits_oneUnreachable_twoPacks()
			throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, 0);
		gc.gc().get();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
		assertEquals(4, stats.numberOfSizeIndexedObjects);
	}

	@Test
	public void gc_preserved_objSizeIdxIsPreserved() throws Exception {
		Collection<Pack> oldPacks = preserveOldPacks();
		assertEquals(1, oldPacks.size());
		PackFile preserved = oldPacks.iterator().next().getPackFile()
				.create(PackExt.OBJECT_SIZE_INDEX)
				.createPreservedForDirectory(
						repo.getObjectDatabase().getPreservedDirectory());
		assertTrue(preserved.exists());
	}

	@Test
	public void gc_preserved_prune_noPreserves() throws Exception {
		preserveOldPacks();
		configureGc(gc, 0).setPrunePreserved(true);
		gc.gc().get();

		assertFalse(repo.getObjectDatabase().getPreservedDirectory().exists());
	}

	private Collection<Pack> preserveOldPacks() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("P").add("P", "P").create();

		// pack loose object into packfile
		configureGc(gc, 0);
		gc.setExpireAgeMillis(0);
		gc.gc().get();
		Collection<Pack> oldPacks = tr.getRepository().getObjectDatabase()
				.getPacks();
		PackFile oldPackfile = oldPacks.iterator().next().getPackFile();
		assertTrue(oldPackfile.exists());

		fsTick();
		bb.commit().message("B").add("B", "Q").create();

		// repack again but now without a grace period for packfiles. We should
		// end up with a new packfile and the old one should be placed in the
		// preserved directory
		gc.setPackExpireAgeMillis(0);
		configureGc(gc, 0).setPreserveOldPacks(true);
		gc.gc().get();

		File preservedPackFile = oldPackfile.createPreservedForDirectory(
				repo.getObjectDatabase().getPreservedDirectory());
		assertTrue(preservedPackFile.exists());
		return oldPacks;
	}

	@Ignore
	public void testPruneAndRestoreOldPacks() throws Exception {
		String tempRef = "refs/heads/soon-to-be-unreferenced";
		BranchBuilder bb = tr.branch(tempRef);
		bb.commit().add("A", "A").add("B", "B").create();

		// Verify setup conditions
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		// Force all referenced objects into packs (to avoid having loose objects)
		configureGc(gc, 0);
		gc.setExpireAgeMillis(0);
		gc.setPackExpireAgeMillis(0);
		gc.gc().get();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);

		// Delete the temp ref, orphaning its commit
		RefUpdate update = tr.getRepository().getRefDatabase().newUpdate(tempRef, false);
		update.setForceUpdate(true);
		ObjectId objectId = update.getOldObjectId(); // remember it so we can restore it!
		RefUpdate.Result result = update.delete();
		assertEquals(RefUpdate.Result.FORCED, result);

		fsTick();

		// Repack with only orphaned commit, so packfile will be pruned
		configureGc(gc, 0).setPreserveOldPacks(true);
		gc.gc().get();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);

		// Restore the temp ref to the deleted commit, should restore old-packs!
		update = tr.getRepository().getRefDatabase().newUpdate(tempRef, false);
		update.setNewObjectId(objectId);
		update.setExpectedOldObjectId(null);
		result = update.update();
		assertEquals(RefUpdate.Result.NEW, result);

		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	private PackConfig configureGc(GC myGc, int minSize) {
		PackConfig pconfig = new PackConfig(repo);
		pconfig.setMinBytesForObjSizeIndex(minSize);
		myGc.setPackConfig(pconfig);
		return pconfig;
	}

	private Pack getOnlyPack(FileRepository fileRepo)
			throws IOException {
		Collection<Pack> packs = fileRepo.getObjectDatabase().getPacks();
		if (packs.isEmpty() || packs.size() > 1) {
			throw new IOException("More than one pack");
		}

		return packs.iterator().next();
	}
}
