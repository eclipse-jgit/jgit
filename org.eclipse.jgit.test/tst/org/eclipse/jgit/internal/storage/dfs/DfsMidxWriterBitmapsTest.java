/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.writeMultipackIndex;
import static org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.writeSinglePackMidx;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BITMAP_DISTANT_COMMIT_SPAN;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.CommitObjects;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.googlecode.javaewah.EWAHCompressedBitmap;

@RunWith(Parameterized.class)
public class DfsMidxWriterBitmapsTest {

	@Parameterized.Parameters(name = "{0}")
	public static Iterable<TestInput> data() throws Exception {
		return List.of(setupOneMidxOverOnePack(), setupOneMidxOverNPacks());
	}

	private record TestInput(String testDesc, DfsRepository db,
			DfsPackFileMidx midx, List<CommitObjects> commitObjects,
			Map<String, CommitObjects> tips, int expectedBitmaps) {
		@Override
		public String toString() {
			return testDesc;
		}
	}

	private TestInput ti;

	public DfsMidxWriterBitmapsTest(TestInput ti) {
		this.ti = ti;
	}

	@Test
	public void bitmapIndex_allObjectsHaveBitmapPosition() throws IOException {
		try (DfsReader ctx = ti.db().getObjectDatabase().newReader()) {
			PackBitmapIndex bi = ti.midx().getBitmapIndex(ctx);
			for (int i = 0; i < ti.midx().getObjectCount(ctx); i++) {
				PackReverseIndex reverseIdx = ti.midx().getReverseIdx(ctx);
				// All objects in the bitmap
				ObjectId oidByOffset = reverseIdx.findObjectByPosition(i);
				assertEquals(i, bi.findPosition(oidByOffset));
			}
		}
	}

	@Test
	public void bitmapIndex_bitmapHasRightObjects() throws IOException {
		try (DfsReader ctx = ti.db().getObjectDatabase().newReader()) {
			PackBitmapIndex bi = ti.midx().getBitmapIndex(ctx);

			ObjectId mainTip = ti.tips().get("refs/heads/main").commit();
			ObjectId devTip = ti.tips().get("refs/heads/dev").commit();
			EWAHCompressedBitmap mainBitmap = bi.getBitmap(mainTip);
			EWAHCompressedBitmap devBitmap = bi.getBitmap(devTip);

			// main and dev commit chains do not have any commit in common
			assertTrue(mainBitmap.and(devBitmap).isEmpty());
			assertEquals(420, ti.midx().getObjectCount(ctx));

			RevWalk rw = new RevWalk(ti.db());
			rw.markStart(rw.parseCommit(mainTip));
			for (RevCommit c; (c = rw.next()) != null;) {
				int bitmapPos = bi.findPosition(c);
				assertTrue(mainBitmap.get(bitmapPos));
				assertFalse(devBitmap.get(bitmapPos));
			}
			rw.reset();

			// dev is an independent chain of commits. None of them
			// should be in the bitmap of "main"
			rw.markStart(rw.parseCommit(devTip));
			for (RevCommit c; (c = rw.next()) != null;) {
				int bitmapPos = bi.findPosition(c);
				assertTrue(devBitmap.get(bitmapPos));
				assertFalse(mainBitmap.get(bitmapPos));
			}
		}
	}

	static TestInput setupOneMidxOverNPacks() throws Exception {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("one_midx_n_packs"));
		db.getObjectDatabase().getReaderOptions().setUseMidxBitmaps(true);

		List<CommitObjects> mainObjs = MidxTestUtils.writeCommitChain(db,
				"refs/heads/main", 100);
		List<CommitObjects> devObjs = MidxTestUtils.writeCommitChain(db,
				"refs/heads/dev", 40);

		Map<String, CommitObjects> tips = new HashMap<>();
		tips.put("refs/heads/main", last(mainObjs));
		tips.put("refs/heads/dev", last(devObjs));

		List<CommitObjects> commitObjects = new ArrayList<>(160);
		commitObjects.addAll(mainObjs);
		commitObjects.addAll(devObjs);

		DfsPackFileMidx midx1 = writeMultipackIndex(db,
				db.getObjectDatabase().getPacks(), null);
		return new TestInput("one midx - n packs", db, midx1, commitObjects,
				tips, tips.size());
	}

	static TestInput setupOneMidxOverOnePack() throws Exception {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("one_midx_n_packs"));
		// Mo midx bitmaps in midx over one pack. No need to set useMidxBitmaps.
		enableMidxBitmaps(db);

		List<CommitObjects> mainObjs = MidxTestUtils.writeCommitChain(db,
				"refs/heads/main", 100);
		List<CommitObjects> devObjs = MidxTestUtils.writeCommitChain(db,
				"refs/heads/dev", 40);
		runGc(db);

		Map<String, CommitObjects> tips = new HashMap<>();
		tips.put("refs/heads/main", last(mainObjs));
		tips.put("refs/heads/dev", last(devObjs));

		List<CommitObjects> commitObjects = new ArrayList<>(160);
		commitObjects.addAll(mainObjs);
		commitObjects.addAll(devObjs);

		DfsPackFileMidx midx1 = writeSinglePackMidx(db);
		return new TestInput("one midx - one pack", db, midx1, commitObjects,
				tips, 0);
	}

	private static void enableMidxBitmaps(DfsRepository repo) {
		repo.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_BITMAP_DISTANT_COMMIT_SPAN, 1);
	}

	private static void runGc(DfsRepository db) throws IOException {
		DfsGarbageCollector garbageCollector = new DfsGarbageCollector(db);
		garbageCollector.pack(NullProgressMonitor.INSTANCE);
		assertEquals(1, garbageCollector.getNewPacks().size());
	}

	private static CommitObjects last(List<CommitObjects> l) {
		return l.get(l.size() - 1);
	}
}
