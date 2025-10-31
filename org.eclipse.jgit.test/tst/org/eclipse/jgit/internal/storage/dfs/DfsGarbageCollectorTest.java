package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphWriter;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.reftable.LogCursor;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.GitTimeParser;
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

	private static final ProgressMonitor NULL_PM = NullProgressMonitor.INSTANCE;

	@Before
	public void setUp() throws IOException {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		git = new TestRepository<>(new InMemoryRepository(desc));
		repo = git.getRepository();
		odb = repo.getObjectDatabase();
		odb.setUseMultipackIndex(true);
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
			switch (d.getPackSource()) {
			case GC:
				gc = pack;
				break;
			case UNREACHABLE_GARBAGE:
				garbage = pack;
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				gcPackFound = true;
				assertTrue("has commit0", isObjectInPack(commit0, pack));
				assertFalse("no commit1", isObjectInPack(commit1, pack));
				break;
			case UNREACHABLE_GARBAGE:
				garbagePackFound = true;
				assertFalse("no commit0", isObjectInPack(commit0, pack));
				assertTrue("has commit1", isObjectInPack(commit1, pack));
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				gcPackFound = true;
				assertTrue("has commit0", isObjectInPack(commit0, pack));
				assertFalse("no commit1", isObjectInPack(commit1, pack));
				break;
			case UNREACHABLE_GARBAGE:
				garbagePackFound = true;
				assertFalse("no commit0", isObjectInPack(commit0, pack));
				assertTrue("has commit1", isObjectInPack(commit1, pack));
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				gcPackFound = true;
				break;
			case INSERT:
				insertPackFound = true;
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC_REST:
				gcRestPackFound = true;
				break;
			case INSERT:
				insertPackFound = true;
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				gcPackFound = true;
				gcPackSize = d.getFileSize(PACK);
				break;
			case GC_REST:
				gcRestPackFound = true;
				gcRestPackSize = d.getFileSize(PACK);
				break;
			case INSERT:
				insertPackFound = true;
				insertPackSize = d.getFileSize(PACK);
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				gcPackFound = true;
				assertEquals(gcPackSize + insertPackSize - 32,
						pack.getPackDescription().getEstimatedPackSize());
				break;
			case GC_REST:
				gcRestPackFound = true;
				assertEquals(gcRestPackSize + insertPackSize - 32,
						pack.getPackDescription().getEstimatedPackSize());
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			switch (d.getPackSource()) {
			case GC:
				// Even though just commit0 will end up in GC pack, because
				// there is no good way to know that up front, both the pack
				// sizes are considered while computing the estimated size of GC
				// pack.
				assertEquals(packSize0 + packSize1 - 32,
						d.getEstimatedPackSize());
				break;
			case UNREACHABLE_GARBAGE:
				// commit1 is moved to UNREACHABLE_GARBAGE pack.
				assertEquals(packSize1, d.getEstimatedPackSize());
				break;
			default:
				fail("unexpected " + d.getPackSource());
				break;
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
			new ReftableWriter(out).begin().finish();
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
			new ReftableWriter(out).begin().finish();
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
			ReftableWriter w = new ReftableWriter(out);
			w.setMinUpdateIndex(42);
			w.setMaxUpdateIndex(42);
			w.begin();
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

	@Test
	public void reftableWithoutTombstoneResurrected() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		String NEXT = "refs/heads/next";
		DfsRefDatabase refdb = (DfsRefDatabase)repo.getRefDatabase();
		git.update(NEXT, commit0);
		Ref next = refdb.exactRef(NEXT);
		assertNotNull(next);
		assertEquals(commit0, next.getObjectId());

		git.delete(NEXT);
		refdb.clearCache();
		assertNull(refdb.exactRef(NEXT));

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		gc.setIncludeDeletes(false);
		gc.setConvertToReftable(false);
		run(gc);
		assertEquals(1, odb.getReftables().length);
		try (DfsReader ctx = odb.newReader();
			 ReftableReader rr = odb.getReftables()[0].open(ctx)) {
			rr.setIncludeDeletes(true);
			assertEquals(1, rr.minUpdateIndex());
			assertEquals(2, rr.maxUpdateIndex());
			assertNull(rr.exactRef(NEXT));
		}

		RevCommit commit1 = commit().message("1").create();
		DfsPackDescription t1 = odb.newPack(INSERT);
		Ref newNext = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, NEXT,
				commit1);
		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			ReftableWriter w = new ReftableWriter(out)
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(1)
			.begin();
			w.writeRef(newNext, 1);
			w.finish();
			t1.addFileExt(REFTABLE);
			t1.setReftableStats(w.getStats());
		}
		odb.commitPack(Collections.singleton(t1), null);
		assertEquals(2, odb.getReftables().length);
		refdb.clearCache();
		newNext = refdb.exactRef(NEXT);
		assertNotNull(newNext);
		assertEquals(commit1, newNext.getObjectId());
	}

	@Test
	public void reftableWithTombstoneNotResurrected() throws Exception {
		RevCommit commit0 = commit().message("0").create();
		String NEXT = "refs/heads/next";
		DfsRefDatabase refdb = (DfsRefDatabase)repo.getRefDatabase();
		git.update(NEXT, commit0);
		Ref next = refdb.exactRef(NEXT);
		assertNotNull(next);
		assertEquals(commit0, next.getObjectId());

		git.delete(NEXT);
		refdb.clearCache();
		assertNull(refdb.exactRef(NEXT));

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		gc.setIncludeDeletes(true);
		gc.setConvertToReftable(false);
		run(gc);
		assertEquals(1, odb.getReftables().length);
		try (DfsReader ctx = odb.newReader();
			 ReftableReader rr = odb.getReftables()[0].open(ctx)) {
			rr.setIncludeDeletes(true);
			assertEquals(1, rr.minUpdateIndex());
			assertEquals(2, rr.maxUpdateIndex());
			next = rr.exactRef(NEXT);
			assertNotNull(next);
			assertNull(next.getObjectId());
		}

		RevCommit commit1 = commit().message("1").create();
		DfsPackDescription t1 = odb.newPack(INSERT);
		Ref newNext = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, NEXT,
				commit1);
		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			ReftableWriter w = new ReftableWriter(out)
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(1)
			.begin();
			w.writeRef(newNext, 1);
			w.finish();
			t1.addFileExt(REFTABLE);
			t1.setReftableStats(w.getStats());
		}
		odb.commitPack(Collections.singleton(t1), null);
		assertEquals(2, odb.getReftables().length);
		refdb.clearCache();
		assertNull(refdb.exactRef(NEXT));
	}

	@Test
	public void produceCommitGraphOnlyHeadsAndTags() throws Exception {
		String tag = "refs/tags/tag1";
		String head = "refs/heads/head1";
		String nonHead = "refs/something/nonHead";

		RevCommit rootCommitTagged = git.branch(tag).commit().message("0")
				.noParents().create();
		RevCommit headTip = git.branch(head).commit().message("1")
				.parent(rootCommitTagged).create();
		RevCommit nonHeadTip = git.branch(nonHead).commit().message("2")
				.parent(rootCommitTagged).create();

		gcWithCommitGraph();

		assertEquals(2, odb.getPacks().length);
		DfsPackFile gcPack = odb.getPacks()[0];
		assertEquals(GC, gcPack.getPackDescription().getPackSource());

		DfsReader reader = odb.newReader();
		CommitGraph cg = gcPack.getCommitGraph(reader);
		assertNotNull(cg);

		assertTrue("Only heads and tags reachable commits in commit graph",
				cg.getCommitCnt() == 2);
		// GC packed
		assertTrue("tag referenced commit is in graph",
				cg.findGraphPosition(rootCommitTagged) != -1);
		assertTrue("head referenced commit is in graph",
				cg.findGraphPosition(headTip) != -1);
		// GC_REST not in commit graph
		assertEquals("nonHead referenced commit is NOT in graph",
				-1, cg.findGraphPosition(nonHeadTip));
	}

	@Test
	public void produceCommitGraphOnlyHeadsAndTagsIncludedFromCache() throws Exception {
		String tag = "refs/tags/tag1";
		String head = "refs/heads/head1";
		String nonHead = "refs/something/nonHead";

		RevCommit rootCommitTagged = git.branch(tag).commit().message("0")
				.noParents().create();
		RevCommit headTip = git.branch(head).commit().message("1")
				.parent(rootCommitTagged).create();
		RevCommit nonHeadTip = git.branch(nonHead).commit().message("2")
				.parent(rootCommitTagged).create();

		gcWithCommitGraph();

		assertEquals(2, odb.getPacks().length);
		DfsPackFile gcPack = odb.getPacks()[0];
		assertEquals(GC, gcPack.getPackDescription().getPackSource());

		DfsReader reader = odb.newReader();
		gcPack.getCommitGraph(reader);
		// Invoke cache hit
		CommitGraph cachedCG = gcPack.getCommitGraph(reader);
		assertNotNull(cachedCG);
		assertTrue("commit graph have been read from disk once",
				reader.stats.readCommitGraph == 1);
		assertTrue("commit graph read contains content",
				reader.stats.readCommitGraphBytes > 0);
		assertTrue("commit graph read time is recorded",
				reader.stats.readCommitGraphMicros > 0);

		assertTrue("Only heads and tags reachable commits in commit graph",
				cachedCG.getCommitCnt() == 2);
		// GC packed
		assertTrue("tag referenced commit is in graph",
				cachedCG.findGraphPosition(rootCommitTagged) != -1);
		assertTrue("head referenced commit is in graph",
				cachedCG.findGraphPosition(headTip) != -1);
		// GC_REST not in commit graph
		assertEquals("nonHead referenced commit is not in graph",
				-1, cachedCG.findGraphPosition(nonHeadTip));
	}

	@Test
	public void noCommitGraphWithoutGcPack() throws Exception {
		String nonHead = "refs/something/nonHead";
		RevCommit nonHeadCommit = git.branch(nonHead).commit()
				.message("nonhead").noParents().create();
		commit().message("unreachable").parent(nonHeadCommit).create();

		gcWithCommitGraph();

		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			assertNull(pack.getCommitGraph(odb.newReader()));
		}
	}

	@Test
	public void commitGraphWithoutGCrestPack() throws Exception {
		String head = "refs/heads/head1";
		RevCommit headCommit = git.branch(head).commit().message("head")
				.noParents().create();
		RevCommit unreachableCommit = commit().message("unreachable")
				.parent(headCommit).create();

		gcWithCommitGraph();

		assertEquals(2, odb.getPacks().length);
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				CommitGraph cg = pack.getCommitGraph(odb.newReader());
				assertNotNull(cg);
				assertTrue("commit graph only contains 1 commit",
						cg.getCommitCnt() == 1);
				assertTrue("head exists in commit graph",
						cg.findGraphPosition(headCommit) != -1);
				assertTrue("unreachable commit does not exist in commit graph",
						cg.findGraphPosition(unreachableCommit) == -1);
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				CommitGraph cg = pack.getCommitGraph(odb.newReader());
				assertNull(cg);
			} else {
				fail("unexpected " + d.getPackSource());
				break;
			}
		}
	}

	@Test
	public void produceCommitGraphAndBloomFilter() throws Exception {
		String head = "refs/heads/head1";

		git.branch(head).commit().message("0").noParents().create();

		gcWithCommitGraphAndBloomFilter();

		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();
		CommitGraphWriter.Stats stats = desc.getCommitGraphStats();
		assertNotNull(stats);
		assertEquals(1, stats.getChangedPathFiltersComputed());
	}

	@Test
	public void testReadChangedPathConfigAsFalse() throws Exception {
		String head = "refs/heads/head1";
		git.branch(head).commit().message("0").noParents().create();
		gcWithCommitGraphAndBloomFilter();

		Config repoConfig = odb.getRepository().getConfig();
		repoConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, null,
				ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS, false);

		DfsPackFile gcPack = odb.getPacks()[0];
		try (DfsReader reader = odb.newReader()) {
			CommitGraph cg = gcPack.getCommitGraph(reader);
			assertNull(cg.getChangedPathFilter(0));
		}
	}

	@Test
	public void testReadChangedPathConfigAsTrue() throws Exception {
		String head = "refs/heads/head1";
		git.branch(head).commit().message("0").noParents().create();
		gcWithCommitGraphAndBloomFilter();

		Config repoConfig = odb.getRepository().getConfig();
		repoConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, null,
				ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS, true);

		DfsPackFile gcPack = odb.getPacks()[0];
		try (DfsReader reader = odb.newReader()) {
			CommitGraph cg = gcPack.getCommitGraph(reader);
			assertNotNull(cg.getChangedPathFilter(0));
		}
	}

	@Test
	public void objectSizeIdx_reachableBlob_bigEnough_indexed() throws Exception {
		String master = "refs/heads/master";
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		RevBlob headsBlob = git.blob("twelve bytes");
		git.branch(master).commit()
				.message("commit on head")
				.add("file.txt", headsBlob)
				.parent(root)
				.create();

		gcWithObjectSizeIndex(10);

		odb.getReaderOptions().setUseObjectSizeIndex(true);
		DfsReader reader = odb.newReader();
		DfsPackFile gcPack = findFirstBySource(odb.getPacks(), GC);
		assertTrue(gcPack.hasObjectSizeIndex(reader));
		assertEquals(12, gcPack.getIndexedObjectSize(reader,
				gcPack.findIdxPosition(reader, headsBlob)));
	}

	@Test
	public void objectSizeIdx_reachableBlob_tooSmall_notIndexed() throws Exception {
		String master = "refs/heads/master";
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		RevBlob tooSmallBlob = git.blob("small");
		git.branch(master).commit()
				.message("commit on head")
				.add("small.txt", tooSmallBlob)
				.parent(root)
				.create();

		gcWithObjectSizeIndex(10);

		odb.getReaderOptions().setUseObjectSizeIndex(true);
		DfsReader reader = odb.newReader();
		DfsPackFile gcPack = findFirstBySource(odb.getPacks(), GC);
		assertTrue(gcPack.hasObjectSizeIndex(reader));
		assertEquals(-1, gcPack.getIndexedObjectSize(reader,
				gcPack.findIdxPosition(reader, tooSmallBlob)));
	}

	@Test
	public void objectSizeIndex_unreachableGarbage_noIdx() throws Exception {
		String master = "refs/heads/master";
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		git.branch(master).commit()
				.message("commit on head")
				.add("file.txt", git.blob("a blob"))
				.parent(root)
				.create();
		git.update(master, root); // blob is unreachable
		gcWithObjectSizeIndex(0);

		DfsReader reader = odb.newReader();
		DfsPackFile gcRestPack = findFirstBySource(odb.getPacks(), UNREACHABLE_GARBAGE);
		assertFalse(gcRestPack.hasObjectSizeIndex(reader));
	}

	@Test
	public void midx_oneMidx_deleteMidxs_allObjectsOneGC() throws Exception {
		String master = "refs/heads/master";
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		git.branch(master).commit().message("commit on head")
				.add("file.txt", git.blob("a blob")).parent(root).create();
		assertEquals(3, countPacks(INSERT));

		DfsPackDescription midx = DfsMidxWriter.writeMidx(
				NullProgressMonitor.INSTANCE, odb,
				Arrays.asList(odb.getPacks()), null);
		odb.commitPack(List.of(midx), null);

		gcNoTtl();

		// Only one pack, is GC but not multipack index
		assertEquals(1, odb.getPacks().length);
		DfsPackDescription actualDesc = odb.getPacks()[0].getPackDescription();
		assertEquals(GC, actualDesc.getPackSource());
		assertFalse(actualDesc.hasFileExt(MULTI_PACK_INDEX));
		DfsPackFile pack = odb.getPacks()[0];
		assertFalse(pack instanceof DfsPackFileMidx);
		assertEquals(0, odb.getPackList().skippedMidxs.length);
		assertTrue(isObjectInPack(root, pack));
	}

	@Test
	public void midx_chainedMidx_deleteMidxs_allObjsInOneGC() throws Exception {
		String master = "refs/heads/master";
		List<RevCommit> knownCommits = new ArrayList<>(11);
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		knownCommits.add(root);
		RevCommit tip = root;
		for (int i = 0; i < 10; i++) {
			tip = git.branch(master).commit().message("commit on head")
					.add("file.txt", git.blob("a blob " + i)).parent(tip)
					.create();
			knownCommits.add(tip);
			// Each of these creates two packs
		}
		assertEquals(21, countPacks(INSERT));

		List<DfsPackFile> basicPacks = Arrays.stream(odb.getPacks())
				.collect(Collectors.toUnmodifiableList());
		DfsPackDescription midx = DfsMidxWriter.writeMidx(NULL_PM, odb,
				basicPacks.subList(0, 9), null);
		odb.commitPack(List.of(midx), null);

		DfsPackDescription midx2 = DfsMidxWriter.writeMidx(NULL_PM, odb,
				basicPacks.subList(9, 21), midx);
		odb.commitPack(List.of(midx2), null);

		// Verify we got one pack that is an midx
		// This is testing the test code
		assertEquals(1, odb.getPacks().length);
		assertTrue(odb.getPacks()[0] instanceof DfsPackFileMidx);
		DfsPackDescription theDesc = odb.getPacks()[0].getPackDescription();
		assertTrue(theDesc.hasFileExt(MULTI_PACK_INDEX));
		assertEquals(12, theDesc.getCoveredPacks().size());
		assertEquals(theDesc.getMultiPackIndexBase(), midx);
		assertEquals(9,
				theDesc.getMultiPackIndexBase().getCoveredPacks().size());
		gcNoTtl();

		// One pack, GC WITHOUT multipack index, contains ALL objects
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		assertEquals(GC, pack.getPackDescription().getPackSource());
		assertFalse(pack instanceof DfsPackFileMidx);
		assertFalse(pack.getPackDescription().hasFileExt(MULTI_PACK_INDEX));
		assertEquals(0, odb.getPackList().skippedMidxs.length);
		for (RevCommit c : knownCommits) {
			assertTrue(isObjectInPack(c, pack));
		}
	}

	@Test
	public void midx_packAndMidx_deleteMidxs_allObjectsOneGC()
			throws Exception {
		String master = "refs/heads/master";
		RevCommit root = git.branch(master).commit().message("root").noParents()
				.create();
		RevCommit tip = git.branch(master).commit().message("commit on head")
				.add("file.txt", git.blob("a blob")).parent(root).create();
		assertEquals(3, countPacks(INSERT));

		List<DfsPackFile> packs = Arrays.stream(odb.getPacks()).toList();
		DfsPackDescription midx = DfsMidxWriter.writeMidx(NULL_PM, odb, packs,
				null);
		odb.commitPack(List.of(midx), null);

		RevBlob blobOutOfMidx = git.blob("some content");
		RevCommit commitOutOfMidx = git.branch(master).commit()
				.message("an extra commit").add("other.txt", blobOutOfMidx)
				.parent(tip).create();
		assertEquals(3, odb.getPacks().length); // midx + 2 new packs
		gcNoTtl();

		// Only one pack, is GC but not multipack index
		assertEquals(1, odb.getPacks().length);
		DfsPackDescription actualDesc = odb.getPacks()[0].getPackDescription();
		assertEquals(GC, actualDesc.getPackSource());
		assertFalse(actualDesc.hasFileExt(MULTI_PACK_INDEX));
		assertEquals(0, odb.getPackList().skippedMidxs.length);

		DfsPackFile pack = odb.getPacks()[0];
		assertTrue(isObjectInPack(root, pack));
		assertTrue(isObjectInPack(root, pack));
		assertTrue(isObjectInPack(blobOutOfMidx, pack));
		assertTrue(isObjectInPack(commitOutOfMidx, pack));
	}

	@Test
	public void bitmapIndexWrittenDuringGc() throws Exception {
		int numBranches = 2;
		int commitsPerBranch = 50;

		RevCommit commit0 = commit().message("0").create();
		git.update("branch0", commit0);
		RevCommit branch1 = commitChain(commit0, commitsPerBranch);
		git.update("branch1", branch1);
		RevCommit branch2 = commitChain(commit0, commitsPerBranch);
		git.update("branch2", branch2);

		int contiguousCommitCount = 5;
		int recentCommitSpan = 2;
		int recentCommitCount = 10;
		int distantCommitSpan = 5;

		PackConfig packConfig = new PackConfig();
		packConfig.setBitmapContiguousCommitCount(contiguousCommitCount);
		packConfig.setBitmapRecentCommitSpan(recentCommitSpan);
		packConfig.setBitmapRecentCommitCount(recentCommitCount);
		packConfig.setBitmapDistantCommitSpan(distantCommitSpan);

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setPackConfig(packConfig);
		run(gc);

		DfsPackFile pack = odb.getPacks()[0];
		PackBitmapIndex bitmapIndex = pack.getBitmapIndex(odb.newReader());
		assertTrue("pack file has bitmap index extension",
				pack.getPackDescription().hasFileExt(PackExt.BITMAP_INDEX));

		int recentCommitsPerBranch = (recentCommitCount - contiguousCommitCount
				- 1) / recentCommitSpan;
		assertEquals("expected recent commits", 2, recentCommitsPerBranch);

		int distantCommitsPerBranch = (commitsPerBranch - 1 - recentCommitCount)
				/ distantCommitSpan;
		assertEquals("expected distant commits", 7, distantCommitsPerBranch);

		int branchBitmapsCount = contiguousCommitCount
				+ numBranches
						* (recentCommitsPerBranch
						+ distantCommitsPerBranch);
		assertEquals("expected bitmaps count", 23, branchBitmapsCount);
		assertEquals("bitmap index has expected number of bitmaps",
				branchBitmapsCount,
				bitmapIndex.getBitmapCount());

		// The count is just a function of whether any bitmaps happen to
		// compress efficiently against the others in the index. We expect for
		// this test that this there will be at least one like this, but the
		// actual count is situation-specific
		assertTrue("bitmap index has xor-compressed bitmaps",
				bitmapIndex.getXorBitmapCount() > 0);
	}

	@Test
	public void gitGCWithRefLogExpire() throws Exception {
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
		Instant currentDay = Instant.now();
		Instant ten_days_ago = GitTimeParser.parseInstant("10 days ago");
		Instant twenty_days_ago = GitTimeParser.parseInstant("20 days ago");
		Instant thirty_days_ago = GitTimeParser.parseInstant("30 days ago");
		Instant fifty_days_ago = GitTimeParser.parseInstant("50 days ago");
		final ZoneOffset offset = ZoneOffset.ofHours(-8);
		PersonIdent who2 = new PersonIdent("J.Author", "authemail", currentDay,
				offset);
		PersonIdent who3 = new PersonIdent("J.Author", "authemail",
				ten_days_ago, offset);
		PersonIdent who4 = new PersonIdent("J.Author", "authemail",
				twenty_days_ago, offset);
		PersonIdent who5 = new PersonIdent("J.Author", "authemail",
				thirty_days_ago, offset);
		PersonIdent who6 = new PersonIdent("J.Author", "authemail",
				fifty_days_ago, offset);

		try (DfsOutputStream out = odb.writeFile(t1, REFTABLE)) {
			ReftableWriter w = new ReftableWriter(out);
			w.setMinUpdateIndex(42);
			w.setMaxUpdateIndex(42);
			w.begin();
			w.sortAndWriteRefs(Collections.singleton(next));
			w.writeLog("refs/heads/branch", 1, who2, ObjectId.zeroId(),id(2), "Branch Message");
			w.writeLog("refs/heads/branch1", 2, who3, ObjectId.zeroId(),id(3), "Branch Message1");
			w.writeLog("refs/heads/branch2", 2, who4, ObjectId.zeroId(),id(4), "Branch Message2");
			w.writeLog("refs/heads/branch3", 2, who5, ObjectId.zeroId(),id(5), "Branch Message3");
			w.writeLog("refs/heads/branch4", 2, who6, ObjectId.zeroId(),id(6), "Branch Message4");
			w.finish();
			t1.addFileExt(REFTABLE);
			t1.setReftableStats(w.getStats());
		}
		odb.commitPack(Collections.singleton(t1), null);

		gc = new DfsGarbageCollector(repo);
		gc.setReftableConfig(new ReftableConfig());
		// Expire ref log entries older than 30 days
		gc.setRefLogExpire(thirty_days_ago);
		run(gc);

		// Single GC pack present with all objects.
		assertEquals(1, odb.getPacks().length);
		DfsPackFile pack = odb.getPacks()[0];
		DfsPackDescription desc = pack.getPackDescription();

		DfsReftable table = new DfsReftable(DfsBlockCache.getInstance(), desc);
		try (DfsReader ctx = odb.newReader();
			 ReftableReader rr = table.open(ctx);
			 RefCursor rc = rr.allRefs();
			 LogCursor lc = rr.allLogs()) {
			assertTrue(rc.next());
			assertEquals(master, rc.getRef().getName());
			assertEquals(commit1, rc.getRef().getObjectId());
			assertTrue(rc.next());
			assertEquals(next.getName(), rc.getRef().getName());
			assertEquals(commit0, rc.getRef().getObjectId());
			assertFalse(rc.next());
			assertTrue(lc.next());
			assertEquals(lc.getRefName(),"refs/heads/branch");
			assertTrue(lc.next());
			assertEquals(lc.getRefName(),"refs/heads/branch1");
			assertTrue(lc.next());
			assertEquals(lc.getRefName(),"refs/heads/branch2");
			// Old entries are purged
			assertFalse(lc.next());
		}
	}


	private RevCommit commitChain(RevCommit parent, int length)
			throws Exception {
		for (int i = 0; i < length; i++) {
			parent = commit().message("" + i).parent(parent).create();
		}
		return parent;
	}

	private static DfsPackFile findFirstBySource(DfsPackFile[] packs, PackSource source) {
		return Arrays.stream(packs)
				.filter(p -> p.getPackDescription().getPackSource() == source)
				.findFirst().get();
	}

	private TestRepository<InMemoryRepository>.CommitBuilder commit() {
		return git.commit();
	}

	private void gcWithCommitGraph() throws IOException {
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setWriteCommitGraph(true);
		run(gc);
	}

	private void gcWithCommitGraphAndBloomFilter() throws IOException {
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setWriteCommitGraph(true);
		gc.setWriteBloomFilter(true);
		run(gc);
	}

	private void gcWithObjectSizeIndex(int threshold) throws IOException {
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.getPackConfig().setMinBytesForObjSizeIndex(threshold);
		run(gc);
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
			for (Ref ref : repo.getRefDatabase().getRefs()) {
				rw.markStart(rw.parseCommit(ref.getObjectId()));
			}
			for (RevCommit next; (next = rw.next()) != null;) {
				if (AnyObjectId.isEqual(next, id)) {
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
	private static ObjectId id(int i) {
		byte[] buf = new byte[OBJECT_ID_LENGTH];
		buf[0] = (byte) (i & 0xff);
		buf[1] = (byte) ((i >>> 8) & 0xff);
		buf[2] = (byte) ((i >>> 16) & 0xff);
		buf[3] = (byte) (i >>> 24);
		return ObjectId.fromRaw(buf);
	}
}
