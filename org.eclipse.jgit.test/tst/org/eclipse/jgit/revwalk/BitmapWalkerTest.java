/*
 * Copyright (C) 2023, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.BitmapIndex.BitmapLookupListener;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectReader;
import org.junit.Before;
import org.junit.Test;

public class BitmapWalkerTest extends LocalDiskRepositoryTestCase {

	private static final String MAIN = "refs/heads/main";

	TestRepository<FileRepository> repo;

	RevCommit tipWithBitmap;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository db = createWorkRepository();
		repo = new TestRepository<>(db);

		RevCommit base = repo.commit().create();
		RevCommit one = repo.commit().parent(base).create();
		tipWithBitmap = repo.commit().parent(one).create();
		repo.update(MAIN, tipWithBitmap);

		GC gc = new GC(repo.getRepository());
		gc.setAuto(false);
		gc.gc().get();

		assertNotNull(repo.getRevWalk().getObjectReader().getBitmapIndex());
	}

	private static class BitmapWalkCounter implements BitmapLookupListener {
		int withBitmap;

		int withoutBitmap;

		@Override
		public void onBitmapFound(AnyObjectId oid) {
			withBitmap += 1;
		}

		@Override
		public void onBitmapNotFound(AnyObjectId oid) {
			withoutBitmap += 1;
		}
	}

	@Test
	public void counters_bitmapAtTip() throws Exception {
		try (RevWalk rw = repo.getRevWalk();
				ObjectReader or = rw.getObjectReader()) {
			BitmapWalkCounter counter = new BitmapWalkCounter();
			BitmapIndex bitmapIndex = or.getBitmapIndex();
			bitmapIndex.addBitmapLookupListener(counter);
			BitmapWalker bw = new BitmapWalker(rw.toObjectWalkWithSameObjects(),
					bitmapIndex, NullProgressMonitor.INSTANCE);
			BitmapBuilder bitmap = bw.findObjects(List.of(tipWithBitmap), null,
					true);
			// First commit has a tree, so in total 4 objects
			assertEquals(4, bitmap.cardinality());
			assertEquals(1, counter.withBitmap);
			assertEquals(0, counter.withoutBitmap);
			assertEquals(0, bw.getCountOfBitmapIndexMisses());
		}
	}

	@Test
	public void counters_bitmapAfterAStep() throws Exception {
		System.out.println("Old tip: " + tipWithBitmap);
		RevCommit newTip = repo.commit().parent(tipWithBitmap).create();
		System.out.println("New tip: " + newTip);
		try (RevWalk rw = repo.getRevWalk();
				ObjectReader or = rw.getObjectReader()) {
			BitmapWalkCounter counter = new BitmapWalkCounter();
			BitmapIndex bitmapIndex = or.getBitmapIndex();
			bitmapIndex.addBitmapLookupListener(counter);
			BitmapWalker bw = new BitmapWalker(rw.toObjectWalkWithSameObjects(),
					bitmapIndex, NullProgressMonitor.INSTANCE);

			bw.findObjects(List.of(newTip), null, true);

			assertEquals(1, counter.withBitmap);
			// It checks bitmap before marking as interesting, and again in the
			// walk
			assertEquals(2, counter.withoutBitmap);
			assertEquals(1, bw.getCountOfBitmapIndexMisses());
		}
	}

	@Test
	public void counters_bitmapAfterThreeSteps() throws Exception {
		RevCommit newOne = repo.commit().parent(tipWithBitmap).create();
		RevCommit newTwo = repo.commit().parent(newOne).create();
		RevCommit newTip = repo.commit().parent(newTwo).create();

		try (RevWalk rw = repo.getRevWalk();
				ObjectReader or = rw.getObjectReader()) {
			BitmapWalkCounter counter = new BitmapWalkCounter();
			BitmapIndex bitmapIndex = or.getBitmapIndex();
			bitmapIndex.addBitmapLookupListener(counter);
			BitmapWalker bw = new BitmapWalker(rw.toObjectWalkWithSameObjects(),
					bitmapIndex, NullProgressMonitor.INSTANCE);

			bw.findObjects(List.of(newTip), null, true);

			assertEquals(1, counter.withBitmap);
			assertEquals(4, counter.withoutBitmap);
			assertEquals(3, bw.getCountOfBitmapIndexMisses());
		}
	}

	@Test
	public void counters_bitmapAfterThreeStepsWithSeen() throws Exception {
		RevCommit newOne = repo.commit().parent(tipWithBitmap).create();
		RevCommit newTwo = repo.commit().parent(newOne).create();
		RevCommit newTip = repo.commit().parent(newTwo).create();

		try (RevWalk rw = repo.getRevWalk();
				ObjectReader or = rw.getObjectReader()) {
			BitmapIndex bitmapIndex = or.getBitmapIndex();
			Bitmap seen = bitmapIndex.getBitmap(tipWithBitmap);
			BitmapBuilder seenBB = bitmapIndex.newBitmapBuilder().or(seen);
			BitmapWalkCounter counter = new BitmapWalkCounter();
			bitmapIndex.addBitmapLookupListener(counter);
			BitmapWalker bw = new BitmapWalker(rw.toObjectWalkWithSameObjects(),
					bitmapIndex, NullProgressMonitor.INSTANCE);

			bw.findObjects(List.of(newTip), seenBB, true);

			assertEquals(0, counter.withBitmap);
			assertEquals(4, counter.withoutBitmap);
			assertEquals(3, bw.getCountOfBitmapIndexMisses());
		}
	}
}
