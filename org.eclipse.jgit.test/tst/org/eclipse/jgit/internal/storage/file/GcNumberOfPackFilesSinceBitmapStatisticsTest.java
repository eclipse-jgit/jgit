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
import java.util.stream.StreamSupport;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class GcNumberOfPackFilesSinceBitmapStatisticsTest extends GcTestCase {
	@Test
	public void testShouldReportZeroObjectsForInitializedRepo()
			throws IOException {
		assertEquals(0L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	@Test
	public void testShouldReportAllPackFilesWhenNoGcWasPerformed()
			throws Exception {
		tr.packAndPrune();
		long result = gc.getStatistics().numberOfPackFilesSinceBitmap;

		assertEquals(repo.getObjectDatabase().getPacks().size(), result);
	}

	@Test
	public void testShouldReportNoObjectsDirectlyAfterGc() throws Exception {
		// given
		addCommit(null);
		gc.gc().get();
		assertEquals(1L, repositoryBitmapFiles());
		assertEquals(0L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	@Test
	public void testShouldReportNewObjectsSinceGcWhenRepositoryProgresses()
			throws Exception {
		// commit & gc
		RevCommit parent = addCommit(null);
		gc.gc().get();
		assertEquals(1L, repositoryBitmapFiles());

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

	private RevCommit addCommit(RevCommit parent) throws Exception {
		PersonIdent ident = new PersonIdent("repo-metrics", "repo@metrics.com");
		TestRepository<FileRepository>.CommitBuilder builder = tr.commit()
				.author(ident);
		if (parent != null) {
			builder.parent(parent);
		}
		RevCommit commit = builder.create();
		tr.update("master", commit);
		parent = commit;
		return parent;
	}

	private long repositoryBitmapFiles() throws IOException {
		return StreamSupport
				.stream(Files
						.newDirectoryStream(repo.getObjectDatabase()
								.getPackDirectory().toPath(), "pack-*.bitmap")
						.spliterator(), false)
				.count();
	}
}
