package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

public class PackMidxTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

//	private TestRng rng;

	private FileRepository repo;

	private TestRepository<Repository> tr;

	private WindowCursor wc;

//	private TestRng getRng() {
//		if (rng == null)
//			rng = new TestRng(JGitTestUtil.getName());
//		return rng;
//	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setStreamFileThreshold(streamThreshold);
		cfg.install();

		repo = createBareRepository();
		tr = new TestRepository<>(repo);
		wc = (WindowCursor) repo.newObjectReader();
	}

	@Test
	public void midx_hasObject() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		assertTrue(midx.hasObject(objs.commitA));
		assertTrue(midx.hasObject(objs.commitB));
		assertTrue(midx.hasObject(objs.commitC));
		assertTrue(midx.hasObject(objs.contentsOfB));
		assertTrue(midx.hasObject(objs.contentsOfA));
		assertFalse(midx.hasObject(ObjectId
				.fromString("678668bdd3a609c6e1d7fea516e9fc1ed4f50ad2")));
	}

	@Test
	public void midx_getObjectCount() throws Exception {
		createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();
		assertEquals(9, midx.getObjectCount());
	}

	@Test
	public void midx_getObjectSize_byId() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		assertEquals(getObjectSizeFromObjectDb(objs.commitA),
				midx.getObjectSize(wc, objs.commitA));
		assertEquals(getObjectSizeFromObjectDb(objs.commitB),
				midx.getObjectSize(wc, objs.commitB));
		assertEquals(getObjectSizeFromObjectDb(objs.commitC),
				midx.getObjectSize(wc, objs.commitC));
		assertEquals(getObjectSizeFromObjectDb(objs.contentsOfA),
				midx.getObjectSize(wc, objs.contentsOfA));
		assertEquals(midx.getObjectSize(wc, objs.contentsOfB),
				midx.getObjectSize(wc, objs.contentsOfB));
	}

	@Test
	public void midx_getObjectSize_byOffset() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> midxOffsets = objs.midxOffsets;
		Integer offsetCommitA = midxOffsets.get(objs.commitA);
		assertEquals(getObjectSizeFromObjectDb(objs.commitA),
				midx.getObjectSize(wc, offsetCommitA));

		Integer offsetCommitB = midxOffsets.get(objs.commitB);
		assertEquals(getObjectSizeFromObjectDb(objs.commitB),
				midx.getObjectSize(wc, offsetCommitB));

		Integer offsetCommitC = midxOffsets.get(objs.commitC);
		assertEquals(getObjectSizeFromObjectDb(objs.commitC),
				midx.getObjectSize(wc, offsetCommitC));

		Integer offsetContentsOfA = midxOffsets.get(objs.contentsOfA);
		assertEquals(getObjectSizeFromObjectDb(objs.contentsOfA),
				midx.getObjectSize(wc, offsetContentsOfA));

		Integer offsetContentsOfB = midxOffsets.get(objs.contentsOfB);
		assertEquals(midx.getObjectSize(wc, objs.contentsOfB),
				midx.getObjectSize(wc, offsetContentsOfB));
	}

	private long getObjectSizeFromObjectDb(AnyObjectId oid) throws IOException {
		return repo.getObjectDatabase().getObjectSize(wc, oid);
	}

	@Test
	public void midx_get_byOid() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		assertEquals("contents of a", midxGet(midx, objs.contentsOfA));
		assertEquals("contents of b", midxGet(midx, objs.contentsOfB));
		String commitContent = midxGet(midx, objs.commitB);
		assertTrue(commitContent
				.startsWith("tree 4ad3577dd1af1fe281a6e55d48cee2d397d570d5"));
		assertTrue(commitContent
				.contains("author J. Author <jauthor@example.com>"));
	}

	private String midxGet(PackMidx midx, AnyObjectId oid) throws IOException {
		ObjectLoader ol = midx.get(wc, oid);
		return new String(ol.getCachedBytes(), UTF_8);
	}

	@Test
	public void midx_load_byOffset() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> midxOffsets = objs.midxOffsets;
		assertEquals("contents of a",
				midxLoad(midx, midxOffsets.get(objs.contentsOfA)));
		assertEquals("contents of b",
				midxLoad(midx, midxOffsets.get(objs.contentsOfB)));
		String commitContent = midxLoad(midx, midxOffsets.get(objs.commitB));
		assertTrue(commitContent
				.startsWith("tree 4ad3577dd1af1fe281a6e55d48cee2d397d570d5"));
		assertTrue(commitContent
				.contains("author J. Author <jauthor@example.com>"));
	}

	private String midxLoad(PackMidx midx, int pos) throws IOException {
		ObjectLoader ol = midx.load(wc, pos);
		return new String(ol.getCachedBytes(), UTF_8);
	}

	@Test
	public void midx_findObject_byOffset() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> midxOffsets = objs.midxOffsets;
		assertEquals(objs.commitA,
				midx.findObjectForOffset(midxOffsets.get(objs.commitA)));
		assertEquals(objs.commitB,
				midx.findObjectForOffset(midxOffsets.get(objs.commitB)));
		assertEquals(objs.commitC,
				midx.findObjectForOffset(midxOffsets.get(objs.commitC)));
		assertEquals(objs.contentsOfA,
				midx.findObjectForOffset(midxOffsets.get(objs.contentsOfA)));
		assertEquals(objs.contentsOfB,
				midx.findObjectForOffset(midxOffsets.get(objs.contentsOfB)));
	}

	@Test
	public void midx_getObjectType() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> midxOffsets = objs.midxOffsets;
		assertEquals(Constants.OBJ_COMMIT,
				midx.getObjectType(wc, midxOffsets.get(objs.commitA)));
		assertEquals(Constants.OBJ_COMMIT,
				midx.getObjectType(wc, midxOffsets.get(objs.commitB)));
		assertEquals(Constants.OBJ_COMMIT,
				midx.getObjectType(wc, midxOffsets.get(objs.commitC)));
		assertEquals(Constants.OBJ_BLOB,
				midx.getObjectType(wc, midxOffsets.get(objs.contentsOfA)));
		assertEquals(Constants.OBJ_BLOB,
				midx.getObjectType(wc, midxOffsets.get(objs.contentsOfB)));
	}

	@Test
	public void midx_iterator() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> midxOffsets = objs.midxOffsets;
		Iterator<PackIndex.MutableEntry> it = midx.iterator();
		assertIteratorEntry(it, objs.contentsOfB,
				midxOffsets.get(objs.contentsOfB));
		it.next();
		it.next();
		assertIteratorEntry(it, objs.commitB, midxOffsets.get(objs.commitB));
		assertIteratorEntry(it, objs.commitA, midxOffsets.get(objs.commitA));
		it.next();
		it.next();
		assertIteratorEntry(it, objs.contentsOfA,
				midxOffsets.get(objs.contentsOfA));
		assertIteratorEntry(it, objs.commitC, midxOffsets.get(objs.commitC));
		assertFalse(it.hasNext());
	}

	private void assertIteratorEntry(Iterator<PackIndex.MutableEntry> it,
			ObjectId oid, int offset) {
		assertTrue(it.hasNext());
		PackIndex.MutableEntry next = it.next();
		assertEquals(oid, next.toObjectId());
		assertEquals(offset, next.getOffset());
	}

	@Test
	public void midx_packIndex_hasObject() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		PackIndex index = midx.getIndex();
		assertTrue(index.hasObject(objs.commitA));
		assertTrue(index.hasObject(objs.commitB));
		assertTrue(index.hasObject(objs.commitC));
		assertTrue(index.hasObject(objs.contentsOfA));
		assertTrue(index.hasObject(objs.contentsOfB));
	}

	@Test
	public void midx_packIndex_findPosition_getByPosition() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		PackIndex index = midx.getIndex();
		assertEquals(objs.commitA,
				index.getObjectId(index.findPosition(objs.commitA)));
		assertEquals(objs.commitB,
				index.getObjectId(index.findPosition(objs.commitB)));
		assertEquals(objs.commitC,
				index.getObjectId(index.findPosition(objs.commitC)));
		assertEquals(objs.contentsOfA,
				index.getObjectId(index.findPosition(objs.contentsOfA)));
		assertEquals(objs.contentsOfB,
				index.getObjectId(index.findPosition(objs.contentsOfB)));
	}

	@Test
	public void midx_packIndex_findOffset() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		Map<RevObject, Integer> offsets = objs.midxOffsets;
		PackIndex index = midx.getIndex();
		assertEquals((long) offsets.get(objs.commitA),
				index.findOffset(objs.commitA));
		assertEquals((long) offsets.get(objs.commitB),
				index.findOffset(objs.commitB));
		assertEquals((long) offsets.get(objs.commitC),
				index.findOffset(objs.commitC));
		assertEquals((long) offsets.get(objs.contentsOfA),
				index.findOffset(objs.contentsOfA));
		assertEquals((long) offsets.get(objs.contentsOfB),
				index.findOffset(objs.contentsOfB));
	}

	private PackMidx writeAndOpenMidxAllPacks() throws IOException {
		Collection<Pack> packs = ((ObjectDirectory) tr.getRepository()
				.getObjectDatabase()).getPacks();
		assertEquals(3, packs.size());

		File midxDest = new File(repo.getObjectDatabase().getPackDirectory(),
				"multi-pack-index");
		MidxWriter.writeMidx(new TextProgressMonitor(), repo, packs, midxDest,
				new PackConfig(repo));

		return new PackMidx(new Config(), midxDest, null,
				packs.stream().toList());

	}

	private TestRepoObjects createThreePacks() throws Exception {
		TestRepository<Repository>.BranchBuilder branch = tr
				.branch("refs/heads/main");
		RevBlob contentsOfA = tr.blob("contents of a");
		RevCommit commitA = branch.commit().add("a.txt", contentsOfA).create();
		tr.packAndPrune();

		RevBlob contentsOfB = tr.blob("contents of b");
		RevCommit commitB = branch.commit().add("b.txt", contentsOfB).create();
		tr.packAndPrune();

		RevCommit commitC = branch.commit().add("c.txt", "contents of c commit")
				.create();
		tr.packAndPrune();

		Map<RevObject, Integer> midxOffsets = Map.of(commitA, 163, commitB, 12,
				commitC, 694, contentsOfA, 399, contentsOfB, 421);

		return new TestRepoObjects(commitA, commitB, commitC, contentsOfA,
				contentsOfB, midxOffsets);
	}

	private record TestRepoObjects(RevCommit commitA, RevCommit commitB,
			RevCommit commitC, RevBlob contentsOfA, RevBlob contentsOfB,
			Map<RevObject, Integer> midxOffsets) {
	}
}
