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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
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
		packAndPrune();
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
		packAndPrune();

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
		packAndPrune();

		assertEquals(1L, gc.getStatistics().numberOfPackFilesSinceBitmap);
	}

	private void packAndPrune() throws Exception {
		try (SkipNonExistingFilesTestRepository testRepo = new SkipNonExistingFilesTestRepository(
				repo)) {
			testRepo.packAndPrune();
		}
	}

	private RevCommit addCommit(RevCommit parent) throws Exception {
		return tr.branch("master").commit()
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

	/**
	 * The TestRepository has a {@link TestRepository#packAndPrune()} function
	 * but it fails in the last step after GC was performed as it doesn't
	 * SKIP_MISSING files. In order to circumvent it was copied and improved
	 * here.
	 */
	private static class SkipNonExistingFilesTestRepository
			extends TestRepository<FileRepository> {
		private final FileRepository repo;

		private SkipNonExistingFilesTestRepository(FileRepository db) throws IOException {
			super(db);
			repo = db;
		}

		@Override
		public void packAndPrune() throws Exception {
			ObjectDirectory odb = repo.getObjectDatabase();
			NullProgressMonitor m = NullProgressMonitor.INSTANCE;

			final PackFile pack, idx;
			try (PackWriter pw = new PackWriter(repo)) {
				Set<ObjectId> all = new HashSet<>();
				for (Ref r : repo.getRefDatabase().getRefs())
					all.add(r.getObjectId());
				pw.preparePack(m, all, PackWriter.NONE);

				pack = new PackFile(odb.getPackDirectory(), pw.computeName(),
						PackExt.PACK);
				try (OutputStream out = new BufferedOutputStream(
						new FileOutputStream(pack))) {
					pw.writePack(m, m, out);
				}
				pack.setReadOnly();

				idx = pack.create(PackExt.INDEX);
				try (OutputStream out = new BufferedOutputStream(
						new FileOutputStream(idx))) {
					pw.writeIndex(out);
				}
				idx.setReadOnly();
			}

			odb.openPack(pack);
			updateServerInfo();

			// alternative packAndPrune implementation that skips missing files
			// after GC.
			for (Pack p : odb.getPacks()) {
				for (MutableEntry e : p)
					FileUtils.delete(odb.fileFor(e.toObjectId()),
							FileUtils.SKIP_MISSING);
			}
		}
	}
}
