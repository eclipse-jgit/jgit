/*
 * Copyright (C) 2026, Youtirsin <brocodzhang@tencent.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PackWriterBitmapDuplicateObjectTest
		extends LocalDiskRepositoryTestCase {

	private TestRepository<FileRepository> tr;
	private FileRepository repo;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createWorkRepository();
		tr = new TestRepository<>(repo, new RevWalk(repo), mockSystemReader);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		tr.close();
		super.tearDown();
	}

	@Test
	public void noDuplicateObjectsWhenBitmapCoverageIsIncomplete()
			throws Exception {
		// Build initial history and GC to create bitmaps.
		tr.branch("refs/heads/master").commit()
				.add("shared.txt", "shared content")
				.add("a.txt", "aaa")
				.message("c1").create();
		tr.branch("refs/heads/master").commit()
				.add("shared.txt", "shared content")
				.add("b.txt", "bbb")
				.message("c2").create();

		GC gc = new GC(repo);
		gc.setPackExpireAge(Duration.ZERO);
		gc.setExpireAge(Duration.ZERO);
		gc.gc().get();
		assertTrue("bitmap index must exist after GC",
				gc.getStatistics().numberOfBitmaps > 0);

		// Add commits that are NOT covered by the bitmap. They reuse
		// "shared.txt", so the shared blob already has a position in
		// the bitmap pack index. BitmapWalker will discover the same
		// blob again via RevWalk fallback (bitmap miss) and insert it
		// into mutableIndex, giving it a second position.
		for (int i = 0; i < 5; i++) {
			tr.branch("refs/heads/master").commit()
					.add("shared.txt", "shared content")
					.add("file" + i + ".txt", "content " + i)
					.message("extra " + i).create();
		}

		// Pack all reachable objects using bitmaps.
		Set<ObjectId> wants = new HashSet<>();
		for (Ref ref : repo.getRefDatabase().getRefs()) {
			if (ref.getObjectId() != null) {
				wants.add(ref.getObjectId());
			}
		}

		ByteArrayOutputStream packBytes = new ByteArrayOutputStream();
		ByteArrayOutputStream idxBytes = new ByteArrayOutputStream();
		try (PackWriter pw = new PackWriter(repo)) {
			pw.setUseBitmaps(true);
			pw.preparePack(NullProgressMonitor.INSTANCE, wants,
					Collections.emptySet());
			pw.writePack(NullProgressMonitor.INSTANCE,
					NullProgressMonitor.INSTANCE, packBytes);
			pw.writeIndex(idxBytes);
		}

		// Every object must appear exactly once. Before the fix the
		// bitmap iterator yielded the shared blob twice and addObject
		// inserted both, causing "The same object appears twice in
		// the pack" on the client side.
		PackIndex idx = PackIndex
				.read(new ByteArrayInputStream(idxBytes.toByteArray()));
		Set<ObjectId> seen = new HashSet<>();
		for (PackIndex.MutableEntry entry : idx) {
			assertTrue(
					"duplicate object in pack: " + entry.toObjectId().name(),
					seen.add(entry.toObjectId()));
		}
		assertEquals(seen.size(), (int) idx.getObjectCount());
	}
}
