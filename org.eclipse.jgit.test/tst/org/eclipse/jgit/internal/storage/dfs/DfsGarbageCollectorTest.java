package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests for pack creation and garbage expiration. */
public class DfsGarbageCollectorTest {
	private TestRepository<InMemoryRepository> git;
	private InMemoryRepository repo;
	private DfsObjDatabase odb;
	private MockSystemReader mockSystemReader;

	@Before
	public void setUp() throws IOException {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		git = new TestRepository<>(new InMemoryRepository(desc));
		repo = git.getRepository();
		odb = repo.getObjectDatabase();
		mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	public void testCollectionWithNoGarbage() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		assertTrue("commit0 reachable", isReachable(repo, commit0));
		assertTrue("commit1 reachable", isReachable(repo, commit1));

		// Packs start out as INSERT.
		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			assertEquals(INSERT, pack.getPackDescription().getPackSource());
		}

		gcNoTtl();

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC, pack.getPackDescription().getPackSource());
		assertTrue("commit0 in pack", isObjectInPack(commit0, pack));
		assertTrue("commit1 in pack", isObjectInPack(commit1, pack));
	}

	@Test
	public void testRacyNoReusePrefersSmaller() throws Exception {
		StringBuilder msg = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			msg.append(i).append(": i am a teapot\n");
		}
		RevBlob a = git.blob(msg.toString());
		RevCommit c0 = git.commit()
				.add("tea", a)
				.message("0")
				.create();

		msg.append("short and stout\n");
		RevBlob b = git.blob(msg.toString());
		RevCommit c1 = git.commit().parent(c0).tick(1)
				.add("tea", b)
				.message("1")
				.create();
		git.update("master", c1);

		PackConfig cfg = new PackConfig();
		cfg.setReuseObjects(false);
		cfg.setReuseDeltas(false);
		cfg.setDeltaCompress(false);
		cfg.setThreads(1);
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS); // disable TTL
		gc.setPackConfig(cfg);
		run(gc);

		assertEquals(1, odb.getPacks().length);
		DfsPackDescription large = odb.getPacks()[0].getPackDescription();
		assertSame(PackSource.GC, large.getPackSource());

		cfg.setDeltaCompress(true);
		gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS); // disable TTL
		gc.setPackConfig(cfg);
		run(gc);

		assertEquals(1, odb.getPacks().length);
		DfsPackDescription small = odb.getPacks()[0].getPackDescription();
		assertSame(PackSource.GC, small.getPackSource());
		assertTrue(
				"delta compression pack is smaller",
				small.getFileSize(PACK) < large.getFileSize(PACK));
		assertTrue(
				"large pack is older",
				large.getLastModified() < small.getLastModified());

		// Forcefully reinsert the older larger GC pack.
		odb.commitPack(Collections.singleton(large), null);
		odb.clearCache();
		assertEquals(2, odb.getPacks().length);

		gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS); // disable TTL
		run(gc);

		assertEquals(1, odb.getPacks().length);
		DfsPackDescription rebuilt = odb.getPacks()[0].getPackDescription();
		assertEquals(small.getFileSize(PACK), rebuilt.getFileSize(PACK));
	}

	@Test
	public void testCollectionWithGarbage() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		assertTrue("commit0 reachable", isReachable(repo, commit0));
		assertFalse("commit1 garbage", isReachable(repo, commit1));
		gcNoTtl();

		assertEquals(2, odb.getPacks().length);
		DfsPackFile gc = null;
		DfsPackFile garbage = null;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gc = pack;
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				garbage = pack;
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}

		assertNotNull("created GC pack", gc);
		assertTrue(isObjectInPack(commit0, gc));

		assertNotNull("created UNREACHABLE_GARBAGE pack", garbage);
		assertTrue(isObjectInPack(commit1, garbage));
	}

	@Test
	public void testCollectionWithGarbageAndGarbagePacksPurged()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		gcWithTtl();
		// The repository should have a GC pack with commit0 and an
		// UNREACHABLE_GARBAGE pack with commit1.
		assertEquals(2, odb.getPacks().length);
		boolean gcPackFound = false;
		boolean garbagePackFound = false;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gcPackFound = true;
				assertTrue("has commit0", isObjectInPack(commit0, pack));
				assertFalse("no commit1", isObjectInPack(commit1, pack));
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				garbagePackFound = true;
				assertFalse("no commit0", isObjectInPack(commit0, pack));
				assertTrue("has commit1", isObjectInPack(commit1, pack));
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
		assertTrue("gc pack found", gcPackFound);
		assertTrue("garbage pack found", garbagePackFound);

		gcWithTtl();
		// The gc operation should have removed UNREACHABLE_GARBAGE pack along with commit1.
		DfsPackFile[] packs = odb.getPacks();
		assertEquals(1, packs.length);

		assertEquals(GC, packs[0].getPackDescription().getPackSource());
		assertTrue("has commit0", isObjectInPack(commit0, packs[0]));
		assertFalse("no commit1", isObjectInPack(commit1, packs[0]));
	}

	@Test
	public void testCollectionWithGarbageAndRereferencingGarbage()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		gcWithTtl();
		// The repository should have a GC pack with commit0 and an
		// UNREACHABLE_GARBAGE pack with commit1.
		assertEquals(2, odb.getPacks().length);
		boolean gcPackFound = false;
		boolean garbagePackFound = false;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gcPackFound = true;
				assertTrue("has commit0", isObjectInPack(commit0, pack));
				assertFalse("no commit1", isObjectInPack(commit1, pack));
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				garbagePackFound = true;
				assertFalse("no commit0", isObjectInPack(commit0, pack));
				assertTrue("has commit1", isObjectInPack(commit1, pack));
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
		assertTrue("gc pack found", gcPackFound);
		assertTrue("garbage pack found", garbagePackFound);

		git.update("master", commit1);

		gcWithTtl();
		// The gc operation should have removed the UNREACHABLE_GARBAGE pack and
		// moved commit1 into GC pack.
		DfsPackFile[] packs = odb.getPacks();
		assertEquals(1, packs.length);

		assertEquals(GC, packs[0].getPackDescription().getPackSource());
		assertTrue("has commit0", isObjectInPack(commit0, packs[0]));
		assertTrue("has commit1", isObjectInPack(commit1, packs[0]));
	}

	@Test
	public void testCollectionWithPureGarbageAndGarbagePacksPurged()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();

		gcWithTtl();
		// The repository should have a single UNREACHABLE_GARBAGE pack with commit0
		// and commit1.
		DfsPackFile[] packs = odb.getPacks();
		assertEquals(1, packs.length);

		assertEquals(UNREACHABLE_GARBAGE, packs[0].getPackDescription().getPackSource());
		assertTrue("has commit0", isObjectInPack(commit0, packs[0]));
		assertTrue("has commit1", isObjectInPack(commit1, packs[0]));

		gcWithTtl();
		// The gc operation should have removed UNREACHABLE_GARBAGE pack along
		// with commit0 and commit1.
		assertEquals(0, odb.getPacks().length);
	}

	@Test
	public void testCollectionWithPureGarbageAndRereferencingGarbage()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();

		gcWithTtl();
		// The repository should have a single UNREACHABLE_GARBAGE pack with commit0
		// and commit1.
		DfsPackFile[] packs = odb.getPacks();
		assertEquals(1, packs.length);

		DfsPackDescription pack = packs[0].getPackDescription();
		assertEquals(UNREACHABLE_GARBAGE, pack.getPackSource());
		assertTrue("has commit0", isObjectInPack(commit0, packs[0]));
		assertTrue("has commit1", isObjectInPack(commit1, packs[0]));

		git.update("master", commit0);

		gcWithTtl();
		// The gc operation should have moved commit0 into the GC pack and
		// removed UNREACHABLE_GARBAGE along with commit1.
		packs = odb.getPacks();
		assertEquals(1, packs.length);

		pack = packs[0].getPackDescription();
		assertEquals(GC, pack.getPackSource());
		assertTrue("has commit0", isObjectInPack(commit0, packs[0]));
		assertFalse("no commit1", isObjectInPack(commit1, packs[0]));
	}

	@Test
	public void testCollectionWithGarbageCoalescence() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		for (int i = 0; i < 3; i++) {
			commit1 = commit().message("g" + i).parent(commit1).create();

			// Make sure we don't have more than 1 UNREACHABLE_GARBAGE pack
			// because they're coalesced.
			gcNoTtl();
			assertEquals(1, countPacks(UNREACHABLE_GARBAGE));
		}
	}

	@Test
	public void testCollectionWithGarbageNoCoalescence() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		for (int i = 0; i < 3; i++) {
			commit1 = commit().message("g" + i).parent(commit1).create();

			DfsGarbageCollector gc = new DfsGarbageCollector(repo);
			gc.setCoalesceGarbageLimit(0);
			gc.setGarbageTtl(0, TimeUnit.MILLISECONDS);
			run(gc);
			assertEquals(1 + i, countPacks(UNREACHABLE_GARBAGE));
		}
	}

	@Test
	public void testCollectionWithGarbageCoalescenceWithShortTtl()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		// Create commits at 1 minute intervals with 1 hour ttl.
		for (int i = 0; i < 100; i++) {
			mockSystemReader.tick(60);
			commit1 = commit().message("g" + i).parent(commit1).create();

			DfsGarbageCollector gc = new DfsGarbageCollector(repo);
			gc.setGarbageTtl(1, TimeUnit.HOURS);
			run(gc);

			// Make sure we don't have more than 4 UNREACHABLE_GARBAGE packs
			// because all the packs that are created in a 20 minutes interval
			// should be coalesced and the packs older than 60 minutes should be
			// removed due to ttl.
			int count = countPacks(UNREACHABLE_GARBAGE);
			assertTrue("Garbage pack count should not exceed 4, but found "
					+ count, count <= 4);
		}
	}

	@Test
	public void testCollectionWithGarbageCoalescenceWithLongTtl()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		// Create commits at 1 hour intervals with 2 days ttl.
		for (int i = 0; i < 100; i++) {
			mockSystemReader.tick(3600);
			commit1 = commit().message("g" + i).parent(commit1).create();

			DfsGarbageCollector gc = new DfsGarbageCollector(repo);
			gc.setGarbageTtl(2, TimeUnit.DAYS);
			run(gc);

			// Make sure we don't have more than 3 UNREACHABLE_GARBAGE packs
			// because all the packs that are created in a single day should
			// be coalesced and the packs older than 2 days should be
			// removed due to ttl.
			int count = countPacks(UNREACHABLE_GARBAGE);
			assertTrue("Garbage pack count should not exceed 3, but found "
					+ count, count <= 3);
		}
	}

	@Test
	public void testEstimateGcPackSizeInNewRepo() throws Exception {
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

		gcNoTtl();

		// INSERT packs are combined into a single GC pack.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testEstimateGcPackSizeWithAnExistingGcPack() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		gcNoTtl();

		RevCommit commit2 = commit().message("2").parent(commit1).create();
		git.update("master", commit2);

		// There will be one INSERT pack and one GC pack.
		assertEquals(2, odb.getPacks().length);
		boolean gcPackFound = false;
		boolean insertPackFound = false;
		long inputPacksSize = 32;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gcPackFound = true;
			} else if (d.getPackSource() == INSERT) {
				insertPackFound = true;
			} else {
				fail("unexpected " + d.getPackSource());
			}
			inputPacksSize += d.getFileSize(PACK) - 32;
		}
		assertTrue(gcPackFound);
		assertTrue(insertPackFound);

		gcNoTtl();

		// INSERT pack is combined into the GC pack.
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testEstimateGcRestPackSizeInNewRepo() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("refs/notes/note1", commit1);

		// Packs start out as INSERT.
		long inputPacksSize = 32;
		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			assertEquals(INSERT, pack.getPackDescription().getPackSource());
			inputPacksSize += pack.getPackDescription().getFileSize(PACK) - 32;
		}

		gcNoTtl();

		// INSERT packs are combined into a single GC_REST pack.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC_REST, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testEstimateGcRestPackSizeWithAnExistingGcPack()
			throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("refs/notes/note1", commit1);

		gcNoTtl();

		RevCommit commit2 = commit().message("2").parent(commit1).create();
		git.update("refs/notes/note2", commit2);

		// There will be one INSERT pack and one GC_REST pack.
		assertEquals(2, odb.getPacks().length);
		boolean gcRestPackFound = false;
		boolean insertPackFound = false;
		long inputPacksSize = 32;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC_REST) {
				gcRestPackFound = true;
			} else if (d.getPackSource() == INSERT) {
				insertPackFound = true;
			} else {
				fail("unexpected " + d.getPackSource());
			}
			inputPacksSize += d.getFileSize(PACK) - 32;
		}
		assertTrue(gcRestPackFound);
		assertTrue(insertPackFound);

		gcNoTtl();

		// INSERT pack is combined into the GC_REST pack.
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC_REST, pack.getPackDescription().getPackSource());
		assertEquals(inputPacksSize,
				pack.getPackDescription().getEstimatedPackSize());
	}

	@Test
	public void testEstimateGcPackSizesWithGcAndGcRestPacks() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		git.update("head", commit0);
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("refs/notes/note1", commit1);

		gcNoTtl();

		RevCommit commit2 = commit().message("2").parent(commit1).create();
		git.update("refs/notes/note2", commit2);

		// There will be one INSERT, one GC and one GC_REST packs.
		assertEquals(3, odb.getPacks().length);
		boolean gcPackFound = false;
		boolean gcRestPackFound = false;
		boolean insertPackFound = false;
		long gcPackSize = 0;
		long gcRestPackSize = 0;
		long insertPackSize = 0;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gcPackFound = true;
				gcPackSize = d.getFileSize(PACK);
			} else if (d.getPackSource() == GC_REST) {
				gcRestPackFound = true;
				gcRestPackSize = d.getFileSize(PACK);
			} else if (d.getPackSource() == INSERT) {
				insertPackFound = true;
				insertPackSize = d.getFileSize(PACK);
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
		assertTrue(gcPackFound);
		assertTrue(gcRestPackFound);
		assertTrue(insertPackFound);

		gcNoTtl();

		// In this test INSERT pack would be combined into the GC_REST pack.
		// But, as there is no good heuristic to know whether the new packs will
		// be combined into a GC pack or GC_REST packs, the new pick size is
		// considered while estimating both the GC and GC_REST packs.
		assertEquals(2, odb.getPacks().length);
		gcPackFound = false;
		gcRestPackFound = false;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				gcPackFound = true;
				assertEquals(gcPackSize + insertPackSize - 32,
						pack.getPackDescription().getEstimatedPackSize());
			} else if (d.getPackSource() == GC_REST) {
				gcRestPackFound = true;
				assertEquals(gcRestPackSize + insertPackSize - 32,
						pack.getPackDescription().getEstimatedPackSize());
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
		assertTrue(gcPackFound);
		assertTrue(gcRestPackFound);
	}

	@Test
	public void testEstimateUnreachableGarbagePackSize() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("master", commit0);

		assertTrue("commit0 reachable", isReachable(repo, commit0));
		assertFalse("commit1 garbage", isReachable(repo, commit1));

		// Packs start out as INSERT.
		long packSize0 = 0;
		long packSize1 = 0;
		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			assertEquals(INSERT, d.getPackSource());
			if (isObjectInPack(commit0, pack)) {
				packSize0 = d.getFileSize(PACK);
			} else if (isObjectInPack(commit1, pack)) {
				packSize1 = d.getFileSize(PACK);
			} else {
				fail("expected object not found in the pack");
			}
		}

		gcNoTtl();

		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				// Even though just commit0 will end up in GC pack, because
				// there is no good way to know that up front, both the pack
				// sizes are considered while computing the estimated size of GC
				// pack.
				assertEquals(packSize0 + packSize1 - 32,
						d.getEstimatedPackSize());
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				// commit1 is moved to UNREACHABLE_GARBAGE pack.
				assertEquals(packSize1, d.getEstimatedPackSize());
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
	}

	@Test
	public void testSinglePackForAllRefs() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		git.update("head", commit0);
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update("refs/notes/note1", commit1);

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS);
		gc.getPackConfig().setSinglePack(true);
		run(gc);
		assertEquals(1, odb.getPacks().length);

		gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS);
		gc.getPackConfig().setSinglePack(false);
		run(gc);
		assertEquals(2, odb.getPacks().length);
	}

	@SuppressWarnings("boxing")
	@Test
	public void producesNewReftable() throws Exception {
		String master = "refs/heads/master";
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();

		BatchRefUpdate bru = git.getRepository().getRefDatabase()
				.newBatchUpdate();
		bru.addCommand(new ReceiveCommand(ObjectId.zeroId(), commit1, master));
		for (int i = 1; i <= 5100; i++) {
			bru.addCommand(new ReceiveCommand(ObjectId.zeroId(), commit0,
					String.format("refs/pulls/%04d", i)));
		}
		try (RevWalk rw = new RevWalk(git.getRepository())) {
			bru.execute(rw, NullProgressMonitor.INSTANCE);
		}

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		run(gc);

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();
		assertEquals(GC, desc.getPackSource());
		assertTrue("commit0 in pack", isObjectInPack(commit0, pack));
		assertTrue("commit1 in pack", isObjectInPack(commit1, pack));

		// Sibling REFTABLE is also present.
		assertTrue(desc.hasFileExt(REFTABLE));
		ReftableWriter.Stats stats = desc.getReftableStats();
		assertNotNull(stats);
		assertTrue(stats.totalBytes() > 0);
		assertEquals(5101, stats.refCount());
		assertEquals(1, stats.minUpdateIndex());
		assertEquals(1, stats.maxUpdateIndex());

		DfsReftable table = new DfsReftable(DfsBlockCache.getInstance(), desc);
		try (DfsReader ctx = odb.newReader();
				ReftableReader rr = table.open(ctx);
				RefCursor rc = rr.seekRef("refs/pulls/5100")) {
			assertTrue(rc.next());
			assertEquals(commit0, rc.getRef().getObjectId());
			assertFalse(rc.next());
		}
	}

	@Test
	public void leavesNonGcReftablesIfNotConfigured() throws Exception {
		String master = "refs/heads/master";
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update(master, commit1);

		DfsPackDescription t1 = odb.newPack(INSERT);
		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			new ReftableWriter().begin(out).finish();
			t1.addFileExt(REFTABLE);
		}
		odb.commitPack(Collections.singleton(t1), null);

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(null);
		run(gc);

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();
		assertEquals(GC, desc.getPackSource());
		assertTrue("commit0 in pack", isObjectInPack(commit0, pack));
		assertTrue("commit1 in pack", isObjectInPack(commit1, pack));

		// A GC and the older INSERT REFTABLE above is present.
		DfsReftable[] tables = odb.getReftables();
		assertEquals(2, tables.length);
		assertEquals(t1, tables[0].getPackDescription());
	}

	@Test
	public void prunesNonGcReftables() throws Exception {
		String master = "refs/heads/master";
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update(master, commit1);

		DfsPackDescription t1 = odb.newPack(INSERT);
		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			new ReftableWriter().begin(out).finish();
			t1.addFileExt(REFTABLE);
		}
		odb.commitPack(Collections.singleton(t1), null);
		odb.clearCache();

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		run(gc);

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();
		assertEquals(GC, desc.getPackSource());
		assertTrue("commit0 in pack", isObjectInPack(commit0, pack));
		assertTrue("commit1 in pack", isObjectInPack(commit1, pack));

		// Only sibling GC REFTABLE is present.
		DfsReftable[] tables = odb.getReftables();
		assertEquals(1, tables.length);
		assertEquals(desc, tables[0].getPackDescription());
		assertTrue(desc.hasFileExt(REFTABLE));
	}

	@Test
	public void compactsReftables() throws Exception {
		String master = "refs/heads/master";
		RevCommit commit0 = commit().message("0").create();
		RevCommit commit1 = commit().message("1").parent(commit0).create();
		git.update(master, commit1);

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		run(gc);

		DfsPackDescription t1 = odb.newPack(INSERT);
		Ref next = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE,
				"refs/heads/next", commit0.copy());
		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			ReftableWriter w = new ReftableWriter();
			w.setMinUpdateIndex(42);
			w.setMaxUpdateIndex(42);
			w.begin(out);
			w.sortAndWriteRefs(Collections.singleton(next));
			w.finish();
			t1.addFileExt(REFTABLE);
			t1.setReftableStats(w.getStats());
		}
		odb.commitPack(Collections.singleton(t1), null);

		gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		run(gc);

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();
		assertEquals(GC, desc.getPackSource());
		assertTrue("commit0 in pack", isObjectInPack(commit0, pack));
		assertTrue("commit1 in pack", isObjectInPack(commit1, pack));

		// Only sibling GC REFTABLE is present.
		DfsReftable[] tables = odb.getReftables();
		assertEquals(1, tables.length);
		assertEquals(desc, tables[0].getPackDescription());
		assertTrue(desc.hasFileExt(REFTABLE));

		// GC reftable contains the compaction.
		DfsReftable table = new DfsReftable(DfsBlockCache.getInstance(), desc);
		try (DfsReader ctx = odb.newReader();
				ReftableReader rr = table.open(ctx);
				RefCursor rc = rr.allRefs()) {
			assertEquals(1, rr.minUpdateIndex());
			assertEquals(42, rr.maxUpdateIndex());

			assertTrue(rc.next());
			assertEquals(master, rc.getRef().getName());
			assertEquals(commit1, rc.getRef().getObjectId());

			assertTrue(rc.next());
			assertEquals(next.getName(), rc.getRef().getName());
			assertEquals(commit0, rc.getRef().getObjectId());

			assertFalse(rc.next());
		}
	}

	private TestRepository<InMemoryRepository>.CommitBuilder commit() {
		return git.commit();
	}

	private void gcNoTtl() throws IOException {
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS); // disable TTL
		run(gc);
	}

	private void gcWithTtl() throws IOException {
		// Move the clock forward by 1 minute and use the same as ttl.
		mockSystemReader.tick(60);
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(1, TimeUnit.MINUTES);
		run(gc);
	}

	private void run(DfsGarbageCollector gc) throws IOException {
		// adjust the current time that will be used by the gc operation.
		mockSystemReader.tick(1);
		assertTrue("gc repacked", gc.pack(null));
		odb.clearCache();
	}

	private static boolean isReachable(Repository repo, AnyObjectId id)
			throws IOException {
		try (RevWalk rw = new RevWalk(repo)) {
			for (Ref ref : repo.getAllRefs().values()) {
				rw.markStart(rw.parseCommit(ref.getObjectId()));
			}
			for (RevCommit next; (next = rw.next()) != null;) {
				if (AnyObjectId.equals(next, id)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isObjectInPack(AnyObjectId id, DfsPackFile pack)
			throws IOException {
		try (DfsReader reader = odb.newReader()) {
			return pack.hasObject(reader, id);
		}
	}

	private int countPacks(PackSource source) throws IOException {
		int cnt = 0;
		for (DfsPackFile pack : odb.getPacks()) {
			if (pack.getPackDescription().getPackSource() == source) {
				cnt++;
			}
		}
		return cnt;
	}
}
