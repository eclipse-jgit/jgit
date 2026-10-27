/*
 * Copyright (C) 2026, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Interoperability tests verifying that cruft packs with {@code .mtimes} files
 * written by JGit can be read by C git and vice versa.
 */
public class CGitCruftPackCompatibilityTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private GC gc;

	private RevCommit orphaned;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
		gc = new GC(db);
		orphaned = setupPackedOrphanedCommit();
	}

	/**
	 * JGit writes a cruft pack; C git verifies it with
	 * {@code git fsck --connectivity-only}.
	 */
	@Test
	public void jgitWritesCruftPack_cgitFsckPasses() throws Exception {
		gc.gc().get();

		Pack cruftPack = findCruftPack(orphaned);
		assertNotNull("JGit must produce a cruft pack", cruftPack);
		assertTrue("Cruft pack must have a .mtimes file",
				cruftPack.hasCruftMtimes());

		// Verify C git can read the orphaned object from the cruft pack
		ProcessBuilder catFile = new ProcessBuilder("git", "cat-file", "-t",
				orphaned.name());
		catFile.directory(db.getDirectory());
		ExecutionResult catResult = FS.DETECTED.execute(catFile, null);
		assertEquals("cgit cat-file exit code", 0, catResult.getRc());
		assertEquals("cgit must find orphaned object as a commit", "commit",
				new String(catResult.getStdout().toByteArray(), UTF_8).trim());

		// Full object validation (not just connectivity)
		ProcessBuilder fsck = new ProcessBuilder("git", "fsck");
		fsck.directory(db.getDirectory());
		ExecutionResult fsckResult = FS.DETECTED.execute(fsck, null);
		assertEquals("cgit fsck exit code", 0, fsckResult.getRc());
	}

	/**
	 * C git writes a cruft pack; JGit reads the {@code .mtimes} file without
	 * error and finds a positive mtime for the orphaned commit.
	 *
	 * <p>The old pack's mtime is set to 3 weeks in the past so that C git's
	 * default two-week pack expiry treats it as eligible for replacement.
	 */
	@Test
	public void cgitWritesCruftPack_jgitReadsMtimes() throws Exception {
		// Back-date pack files so C git's default 2-week pack expiry treats
		// them as eligible for replacement. Set gc.pruneExpire=never so C git
		// moves unreachable objects into a cruft pack rather than pruning them.
		FileBasedConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_GC_SECTION, null,
				"pruneExpire", "never");
		config.save();
		FileTime threeWeeksAgo = FileTime
				.from(Instant.now().minus(Duration.ofDays(21)));
		long expectedMtimeSecs = threeWeeksAgo.toInstant().getEpochSecond();
		for (Pack p : db.getObjectDatabase().getPacks()) {
			File packFile = p.getPackFile();
			String base = packFile.getPath().replaceFirst("\\.pack$", "");
			for (String ext : new String[] { ".pack", ".idx", ".bitmap" }) {
				File f = new File(base + ext);
				if (f.exists()) {
					Files.setLastModifiedTime(f.toPath(), threeWeeksAgo);
				}
			}
		}

		ProcessBuilder pb = new ProcessBuilder("git", "gc", "--cruft");
		pb.directory(db.getWorkTree());
		ExecutionResult result = FS.DETECTED.execute(pb, null);
		assertEquals("cgit gc exit code", 0, result.getRc());

		// reload pack list after C git rewrote the object store
		db.getObjectDatabase().close();

		Collection<Pack> packs = db.getObjectDatabase().getPacks();
		Pack cruftPack = null;
		for (Pack p : packs) {
			if (p.hasCruftMtimes()) {
				cruftPack = p;
				break;
			}
		}
		assertNotNull("C git must produce a cruft pack", cruftPack);
		assertTrue("Orphaned commit must be in cruft pack",
				cruftPack.hasObject(orphaned));
		assertEquals("Cruft mtime of orphaned commit must match old pack mtime",
				expectedMtimeSecs, cruftPack.getCruftMtime(orphaned));
	}

	/**
	 * Mirrors {@code GcBasicPackingTest.testCruftPack()}: pack everything,
	 * advance the filesystem clock, orphan a commit, then enable cruft packs.
	 */
	private RevCommit setupPackedOrphanedCommit() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().message("M").add("M", "M").create();

		String tempRef = "refs/heads/soon-to-be-unreferenced";
		BranchBuilder bb2 = tr.branch(tempRef);
		RevCommit orphan = bb2.commit().message("U").add("U", "U").create();

		gc.setExpireAge(Duration.ZERO);
		gc.gc().get();

		fsTick(null);

		RefUpdate update = tr.getRepository().getRefDatabase()
				.newUpdate(tempRef, false);
		update.setForceUpdate(true);
		update.delete();

		bb.commit().message("B").add("B", "Q").create();

		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_CRUFTPACKS, true);
		config.save();
		gc.setPackExpireAge(Duration.ZERO);

		return orphan;
	}

	private Pack findCruftPack(RevCommit orphan) throws Exception {
		for (Pack p : db.getObjectDatabase().getPacks()) {
			if (p.hasObject(orphan)) {
				return p;
			}
		}
		return null;
	}
}
