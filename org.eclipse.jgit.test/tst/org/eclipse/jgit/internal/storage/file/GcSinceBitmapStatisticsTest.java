/*
 * Copyright (c) 2024 Jacek Centkowski <geminica.programs@gmail.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;

public class GcSinceBitmapStatisticsTest extends GcTestCase {
	@Test
	public void testShouldReportZeroPacksAndObjectsForInitializedRepo()
			throws IOException {
		RepoStatistics s = gc.getStatistics();
		assertEquals(0L, s.numberOfPackFilesSinceBitmap);
		assertEquals(0L, s.numberOfObjectsSinceBitmap);
		assertEquals(0L, s.numberOfTagsHeadsObjectsSinceBitmap);
	}

	@Test
	public void testShouldReportAllPackFilesWhenNoGcWasPerformed()
			throws Exception {
		tr.packAndPrune();
		long result = gc.getStatistics().numberOfPackFilesSinceBitmap;

		assertEquals(repo.getObjectDatabase().getPacks().size(), result);
	}

	@Test
	public void testShouldReportAllObjectsWhenNoGcWasPerformed()
			throws Exception {
		tr.packAndPrune();

		assertEquals(
				getNumberOfObjectsInPacks(repo.getObjectDatabase().getPacks()),
				gc.getStatistics().numberOfObjectsSinceBitmap);
	}

	@Test
	public void testShouldReportNoPacksFilesSinceBitmapWhenPackfilesAreOlderThanBitmapFile()
			throws Exception {
		addCommit(null);
		configureGC(/* buildBitmap */ false).gc().get();
		assertEquals(1L, gc.getStatistics().numberOfPackFiles);
		assertEquals(0L, repositoryBitmapFiles());
		assertEquals(1L, gc.getStatistics().numberOfPackFilesSinceBitmap);

		addCommit(null);
		configureGC(/* buildBitmap */ true).gc().get();

		assertEquals(1L, repositoryBitmapFiles());
		assertEquals(2L, gc.getStatistics().numberOfPackFiles);
		assertEquals(0L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	@Test
	public void testShouldReportNoObjectsDirectlyAfterGc() throws Exception {
		// given
		addCommit(null);
		assertEquals(2L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(1L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		gc.gc().get();
		assertEquals(0L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);
	}

	@Test
	public void testShouldReportNewPacksSinceGcWhenRepositoryProgresses()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();
		assertEquals(1L, repositoryBitmapFiles());

		// progress & pack
		addCommit(parent);
		assertEquals(1L, gc.getStatistics().numberOfPackFiles);
		assertEquals(0L, gc.getStatistics().numberOfPackFilesSinceBitmap);

		tr.packAndPrune();
		assertEquals(2L, gc.getStatistics().numberOfPackFiles);
		assertEquals(1L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	@Test
	public void testShouldReportNewObjectsSinceGcWhenRepositoryProgresses()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();
		assertEquals(0L, gc.getStatistics().numberOfLooseObjects);
		assertEquals(0L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		// progress & pack
		addCommit(parent);
		assertEquals(1L, gc.getStatistics().numberOfLooseObjects);
		assertEquals(1L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(1L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		tr.packAndPrune();
		assertEquals(0L, gc.getStatistics().numberOfLooseObjects);
		// Number of objects contained in the newly created PackFile
		assertEquals(3L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(3L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);
	}

	@Test
	public void testShouldReportNewPacksFromTheLatestBitmapWhenRepositoryProgresses()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();
		assertEquals(1L, repositoryBitmapFiles());

		// progress & gc
		parent = addCommit(parent);
		gc.gc().get();
		assertEquals(2L, repositoryBitmapFiles());

		// progress & pack
		addCommit(parent);
		tr.packAndPrune();

		assertEquals(1L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	@Test
	public void testShouldReportNewObjectsFromTheLatestBitmapWhenRepositoryProgresses()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();

		// progress & gc
		parent = addCommit(parent);
		gc.gc().get();
		assertEquals(0L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		// progress & pack
		addCommit(parent);
		assertEquals(1L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(1L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		tr.packAndPrune();
		assertEquals(4L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(4L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);
	}

	@Test
	public void testShouldNotReportNewTagsOrCommitsObjectsWhenRepositoryProgressesWithNoHeads()
			throws Exception {
		// commit & gc
		String refsMeta = "refs/changes";
		RevCommit parent = addCommit(null, refsMeta);
		gc.gc().get();

		// progress & gc
		parent = addCommit(parent, refsMeta);
		gc.gc().get();
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		// progress & pack
		addCommit(parent, refsMeta);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		tr.packAndPrune();
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);
	}

	@Test
	public void testShouldReportNewTagsOrCommitsObjectsWhenRepositoryProgressesWithHeadsAndNoHeads()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();

		// progress & gc
		parent = addCommit(parent);
		gc.gc().get();
		assertEquals(0L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		// progress & pack
		String refsMeta = "refs/changes";
		addCommit(parent, refsMeta);
		assertEquals(1L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(0L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);

		// both heads and no-heads commits are grouped in the the same pack
		// hence they are reported collectively
		tr.packAndPrune();
		assertEquals(4L, gc.getStatistics().numberOfObjectsSinceBitmap);
		assertEquals(4L,
				gc.getStatistics().numberOfTagsHeadsObjectsSinceBitmap);
	}

	private RevCommit addCommit(RevCommit parent) throws Exception {
		return addCommit(parent, "master");
	}

	private RevCommit addCommit(RevCommit parent, String branch)
			throws Exception {
		return tr.branch(branch).commit()
				.author(new PersonIdent("repo-metrics", "repo@metrics.com"))
				.parent(parent).create();
	}

	private long repositoryBitmapFiles() throws IOException {
		return StreamSupport
				.stream(Files
						.newDirectoryStream(repo.getObjectDatabase()
								.getPackDirectory().toPath(), "pack-*.bitmap")
						.spliterator(), false)
				.count();
	}

	private long getNumberOfObjectsInPacks(Collection<Pack> packs) {
		return packs.stream().mapToLong(pack -> {
			try {
				return pack.getObjectCount();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}).sum();
	}

	private GC configureGC(boolean buildBitmap) {
		PackConfig pc = new PackConfig(repo.getObjectDatabase().getConfig());
		pc.setBuildBitmaps(buildBitmap);
		gc.setPackConfig(pc);
		return gc;
	}
}
