/*
 * Copyright (C) 2026, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Before;
import org.junit.Test;

public class GcTest extends CLIRepositoryTestCase {
	private Git git;
	private TestRepository<?> tr;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		tr = new TestRepository<>(db);
		setupOrphanedCommit();
	}

	@Test
	public void testCruftFlag() throws Exception {
		// --cruft must collect unreachable objects into a cruft pack with
		// .mtimes
		execute("git gc --cruft");
		assertTrue("--cruft must produce a pack with a .mtimes file",
				hasCruftPack());
	}

	@Test
	public void testNoCruftFlag() throws Exception {
		// --no-cruft must not produce any cruft pack
		execute("git gc --no-cruft");
		assertFalse("--no-cruft must not produce a pack with a .mtimes file",
				hasCruftPack());
	}

	private void setupOrphanedCommit() throws Exception {
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("M").add("M", "M").create();

		String tempRef = "refs/heads/soon-to-be-unreferenced";
		tr.branch(tempRef).commit().message("U").add("U", "U").create();

		// pack everything so the orphaned commit ends up in an old pack
		git.gc().call();

		// wait so the old pack's mtime is strictly before "now" at second GC time
		Thread.sleep(1100);

		// delete the temp ref, orphaning its commit
		RefUpdate update = db.getRefDatabase().newUpdate(tempRef, false);
		update.setForceUpdate(true);
		update.delete();

		// add a new reachable commit so GC has reason to repack
		bb.commit().message("B").add("B", "Q").create();

		// expire old packs immediately via config
		FileBasedConfig config = getConfig();
		config.setString(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_PRUNEPACKEXPIRE, "now");
		config.save();
	}

	private FileBasedConfig getConfig() {
		return (FileBasedConfig) db.getConfig();
	}

	private boolean hasCruftPack() throws Exception {
		Collection<Pack> packs = ((ObjectDirectory) db.getObjectDatabase())
				.getPacks();
		for (Pack p : packs) {
			if (p.hasCruftMtimes()) {
				return true;
			}
		}
		return false;
	}
}
