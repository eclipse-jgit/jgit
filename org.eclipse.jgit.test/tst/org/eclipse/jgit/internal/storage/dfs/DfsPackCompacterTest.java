/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.COMPACT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.pack.PackExt.OBJECT_SIZE_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class DfsPackCompacterTest {
	private static final int AUTO_ADD_SIZE = 5 * 1024 * 1024; // 5 MiB

	private TestRepository<InMemoryRepository> git;
	private InMemoryRepository repo;
	private DfsObjDatabase odb;

	@Before
	public void setUp() throws IOException {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		git = new TestRepository<>(new InMemoryRepository(desc));
		repo = git.getRepository();
		odb = repo.getObjectDatabase();
	}

	@Test
	public void testEstimateCompactPackSizeInNewRepo() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		// Packs start out as INSERT.
		long inputPacksSize = 32;
		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			assertEquals(INSERT, pack.getPackDescription().getPackSource());
			inputPacksSize += pack.getPackDescription().getFileSize(PACK) - 32;
		}

		compact();

		// INSERT packs are compacted into a single COMPACT pack.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(COMPACT, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testEstimateGcPackSizeWithAnExistingGcPack() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		compact();

		RevCommit commit2 = commit().message("2").parent(commit1).create();
		git.update("master", commit2);

		// There will be one INSERT pack and one COMPACT pack.
		assertEquals(2, odb.getPacks().length);
		boolean compactPackFound = false;
		boolean insertPackFound = false;
		long inputPacksSize = 32;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription packDescription = pack.getPackDescription();
			if (packDescription.getPackSource() == COMPACT) {
				compactPackFound = true;
			}
			if (packDescription.getPackSource() == INSERT) {
				insertPackFound = true;
			}
			inputPacksSize += packDescription.getFileSize(PACK) - 32;
		}
		assertTrue(compactPackFound);
		assertTrue(insertPackFound);

		compact();

		// INSERT pack is combined into the COMPACT pack.
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(COMPACT, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testObjectSizeIndexWritten() throws Exception {
		writeObjectSizeIndex(repo, true);
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		compact();

		Optional<DfsPackFile> compactPack = Arrays.stream(odb.getPacks())
				.filter(pack -> pack.getPackDescription()
						.getPackSource() == COMPACT)
				.findFirst();
		assertTrue(compactPack.isPresent());
		assertTrue(compactPack.get().getPackDescription().hasFileExt(OBJECT_SIZE_INDEX));
	}

	@Test
	public void testObjectSizeIndexNotWritten() throws Exception {
		writeObjectSizeIndex(repo, false);
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		compact();

		Optional<DfsPackFile> compactPack = Arrays.stream(odb.getPacks())
				.filter(pack -> pack.getPackDescription()
						.getPackSource() == COMPACT)
				.findFirst();
		assertTrue(compactPack.isPresent());
		assertFalse(compactPack.get().getPackDescription().hasFileExt(OBJECT_SIZE_INDEX));
	}

	private TestRepository<InMemoryRepository>.CommitBuilder commit() {
		return git.commit();
	}

	private void compact() throws IOException {
		DfsPackCompactor compactor = new DfsPackCompactor(repo);
		DfsObjDatabase objdb = repo.getObjectDatabase();
		for (DfsPackFile pack : objdb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getFileSize(PACK) < AUTO_ADD_SIZE) {
				compactor.add(pack);
			} else {
				compactor.exclude(pack);
			}
		}
		compactor.compact(null);
		odb.clearCache();
	}

	private static void writeObjectSizeIndex(DfsRepository repo, boolean should) {
		repo.getConfig().setInt(ConfigConstants.CONFIG_PACK_SECTION, null,
				ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, should ? 0 : -1);
	}
}
