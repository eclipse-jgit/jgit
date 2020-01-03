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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class GcBasicPackingTest extends GcTestCase {
	@DataPoints
	public static boolean[] aggressiveValues = { true, false };

	@Theory
	public void repackEmptyRepo_noPackCreated(boolean aggressive)
			throws IOException {
		configureGc(gc, aggressive);
		gc.repack();
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Theory
	public void testPackRepoWithNoRefs(boolean aggressive) throws Exception {
		tr.commit().add("A", "A").add("B", "B").create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		assertEquals(0, stats.numberOfPackFiles);
		assertEquals(0, stats.numberOfBitmaps);
	}

	@Theory
	public void testPack2Commits(boolean aggressive) throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(2, stats.numberOfBitmaps);
	}

	@Theory
	public void testPackAllObjectsInOnePack(boolean aggressive)
			throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		stats = gc.getStatistics();
		assertEquals(4, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);

		// Do the gc again and check that it hasn't changed anything
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);
	}

	@Theory
	public void testPackCommitsAndLooseOne(boolean aggressive)
			throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		stats = gc.getStatistics();
		assertEquals(8, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(8, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);
		assertEquals(1, stats.numberOfBitmaps);
	}

	@Theory
	public void testNotPackTwice(boolean aggressive) throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().message("M").add("M", "M").create();
		bb.commit().message("B").add("B", "Q").create();
		bb.commit().message("A").add("A", "A").create();
		RevCommit second = tr.commit().parent(first).message("R").add("R", "Q")
				.create();
		tr.update("refs/tags/t1", second);

		Collection<PackFile> oldPacks = tr.getRepository().getObjectDatabase()
				.getPacks();
		assertEquals(0, oldPacks.size());
		stats = gc.getStatistics();
		assertEquals(11, stats.numberOfLooseObjects);
		assertEquals(0, stats.numberOfPackedObjects);

		gc.setExpireAgeMillis(0);
		fsTick();
		configureGc(gc, aggressive);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);

		List<PackFile> packs = new ArrayList<>(
				repo.getObjectDatabase().getPacks());
		assertEquals(11, packs.get(0).getObjectCount());
	}

	@Test
	public void testDonePruneTooYoungPacks() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("M").add("M", "M").create();

		String tempRef = "refs/heads/soon-to-be-unreferenced";
		BranchBuilder bb2 = tr.branch(tempRef);
		bb2.commit().message("M").add("M", "M").create();

		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(4, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
		File oldPackfile = tr.getRepository().getObjectDatabase().getPacks()
				.iterator().next().getPackFile();

		fsTick();

		// delete the temp ref, orphaning its commit
		RefUpdate update = tr.getRepository().getRefDatabase().newUpdate(tempRef, false);
		update.setForceUpdate(true);
		update.delete();

		bb.commit().message("B").add("B", "Q").create();

		// The old packfile is too young to be deleted. We should end up with
		// two pack files
		gc.setExpire(new Date(oldPackfile.lastModified() - 1));
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		// if objects exist in multiple packFiles then they are counted multiple
		// times
		assertEquals(10, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);

		// repack again but now without a grace period for loose objects. Since
		// we don't have loose objects anymore this shouldn't change anything
		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		// if objects exist in multiple packFiles then they are counted multiple
		// times
		assertEquals(10, stats.numberOfPackedObjects);
		assertEquals(2, stats.numberOfPackFiles);

		// repack again but now without a grace period for packfiles. We should
		// end up with one packfile
		gc.setPackExpireAgeMillis(0);

		// we want to keep newly-loosened objects though
		gc.setExpireAgeMillis(-1);

		gc.gc();
		stats = gc.getStatistics();
		assertEquals(1, stats.numberOfLooseObjects);
		// if objects exist in multiple packFiles then they are counted multiple
		// times
		assertEquals(6, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testImmediatePruning() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("M").add("M", "M").create();

		String tempRef = "refs/heads/soon-to-be-unreferenced";
		BranchBuilder bb2 = tr.branch(tempRef);
		bb2.commit().message("M").add("M", "M").create();

		gc.setExpireAgeMillis(0);
		gc.gc();
		stats = gc.getStatistics();

		fsTick();

		// delete the temp ref, orphaning its commit
		RefUpdate update = tr.getRepository().getRefDatabase().newUpdate(tempRef, false);
		update.setForceUpdate(true);
		update.delete();

		bb.commit().message("B").add("B", "Q").create();

		// We want to immediately prune deleted objects
		FileBasedConfig config = repo.getConfig();
		config.setString(ConfigConstants.CONFIG_GC_SECTION, null,
			ConfigConstants.CONFIG_KEY_PRUNEEXPIRE, "now");
		config.save();

		//And we don't want to keep packs full of dead objects
		gc.setPackExpireAgeMillis(0);

		gc.gc();
		stats = gc.getStatistics();
		assertEquals(0, stats.numberOfLooseObjects);
		assertEquals(6, stats.numberOfPackedObjects);
		assertEquals(1, stats.numberOfPackFiles);
	}

	@Test
	public void testPreserveAndPruneOldPacks() throws Exception {
		testPreserveOldPacks();
		configureGc(gc, false).setPrunePreserved(true);
		gc.gc();

		assertFalse(repo.getObjectDatabase().getPreservedDirectory().exists());
	}

	private void testPreserveOldPacks() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("P").add("P", "P").create();

		// pack loose object into packfile
		gc.setExpireAgeMillis(0);
		gc.gc();
		File oldPackfile = tr.getRepository().getObjectDatabase().getPacks()
				.iterator().next().getPackFile();
		assertTrue(oldPackfile.exists());

		fsTick();
		bb.commit().message("B").add("B", "Q").create();

		// repack again but now without a grace period for packfiles. We should
		// end up with a new packfile and the old one should be placed in the
		// preserved directory
		gc.setPackExpireAgeMillis(0);
		configureGc(gc, false).setPreserveOldPacks(true);
		gc.gc();

		File oldPackDir = repo.getObjectDatabase().getPreservedDirectory();
		String oldPackFileName = oldPackfile.getName();
		String oldPackName = oldPackFileName.substring(0,
				oldPackFileName.lastIndexOf('.')) + ".old-pack";  //$NON-NLS-1$
		File preservePackFile = new File(oldPackDir, oldPackName);
		assertTrue(preservePackFile.exists());
	}

	private PackConfig configureGc(GC myGc, boolean aggressive) {
		PackConfig pconfig = new PackConfig(repo);
		if (aggressive) {
			pconfig.setDeltaSearchWindowSize(250);
			pconfig.setMaxDeltaDepth(250);
			pconfig.setReuseObjects(false);
		} else
			pconfig = new PackConfig(repo);
		myGc.setPackConfig(pconfig);
		return pconfig;
	}
}
