package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class DfsGarbageCollectorTest {
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

		gcNoTtl();
		gcWithTtl();

		// The repository has an UNREACHABLE_GARBAGE pack that could have
		// expired, but since we never purge the most recent UNREACHABLE_GARBAGE
		// pack, it must have survived the GC.
		boolean commit1Found = false;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == GC) {
				assertTrue("has commit0", isObjectInPack(commit0, pack));
				assertFalse("no commit1", isObjectInPack(commit1, pack));
			} else if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				commit1Found |= isObjectInPack(commit1, pack);
			} else {
				fail("unexpected " + d.getPackSource());
			}
		}
		assertTrue("garbage commit1 still readable", commit1Found);

		// Find oldest UNREACHABLE_GARBAGE; it will be pruned by next GC.
		DfsPackDescription oldestGarbagePack = null;
		for (DfsPackFile pack : odb.getPacks()) {
			DfsPackDescription d = pack.getPackDescription();
			if (d.getPackSource() == UNREACHABLE_GARBAGE) {
				oldestGarbagePack = oldestPack(oldestGarbagePack, d);
			}
		}
		assertNotNull("has UNREACHABLE_GARBAGE", oldestGarbagePack);

		gcWithTtl();
		assertTrue("has packs", odb.getPacks().length > 0);
		for (DfsPackFile pack : odb.getPacks()) {
			assertNotEquals(oldestGarbagePack, pack.getPackDescription());
		}
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

	private TestRepository<InMemoryRepository>.CommitBuilder commit() {
		return git.commit();
	}

	private void gcNoTtl() throws IOException {
		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS); // disable TTL
		run(gc);
	}

	private void gcWithTtl() throws InterruptedException, IOException {
		// Wait for the system clock to move by at least 1 millisecond.
		// This allows the DfsGarbageCollector to recognize the boundary.
		long start = System.currentTimeMillis();
		do {
			Thread.sleep(10);
		} while (System.currentTimeMillis() <= start);

		DfsGarbageCollector gc = new DfsGarbageCollector(repo);
		gc.setGarbageTtl(1, TimeUnit.MILLISECONDS);
		run(gc);
	}

	private void run(DfsGarbageCollector gc) throws IOException {
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
		try (DfsReader reader = new DfsReader(odb)) {
			return pack.hasObject(reader, id);
		}
	}

	private static DfsPackDescription oldestPack(DfsPackDescription a,
			DfsPackDescription b) {
		if (a != null && a.getLastModified() < b.getLastModified()) {
			return a;
		}
		return b;
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
