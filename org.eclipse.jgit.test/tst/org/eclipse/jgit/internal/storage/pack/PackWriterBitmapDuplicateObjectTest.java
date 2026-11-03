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
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevBlob;
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

	/**
	 * When CompressedBitmap.iterator() yields the same OID twice (once
	 * from the mutableIndex partition and once from the packIndex
	 * partition), addObject must silently drop the second occurrence.
	 * Verify by feeding the same RevBlob twice through the public
	 * addObject and asserting the pack contains it exactly once.
	 */
	@Test
	public void addObjectDeduplicates() throws Exception {
		RevBlob blob = tr.blob("duplicate me");

		try (PackWriter pw = new PackWriter(repo)) {
			pw.addObject(blob);
			pw.addObject(blob);

			// objectsLists must contain the blob only once.
			assertEquals(1, pw.getObjectCount());

			ByteArrayOutputStream packBytes = new ByteArrayOutputStream();
			ByteArrayOutputStream idxBytes = new ByteArrayOutputStream();
			NullProgressMonitor m = NullProgressMonitor.INSTANCE;
			pw.writePack(m, m, packBytes);
			pw.writeIndex(idxBytes);

			PackIndex idx = PackIndex.read(
					new ByteArrayInputStream(idxBytes.toByteArray()));
			assertEquals(1, idx.getObjectCount());

			Set<ObjectId> seen = new HashSet<>();
			for (PackIndex.MutableEntry entry : idx) {
				assertTrue(
						"duplicate object in pack: "
								+ entry.toObjectId().name(),
						seen.add(entry.toObjectId()));
			}
		}
	}
}
