/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapObject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.NB;
import org.junit.Before;
import org.junit.Test;

public class PackMidxTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

	private final static ObjectId UNKNOWN_OBJ = ObjectId
			.fromString("678668bdd3a609c6e1d7fea516e9fc1ed4f50ad2");

	private FileRepository repo;

	private TestRepository<Repository> tr;

	private WindowCursor wc;

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
		assertFalse(midx.hasObject(UNKNOWN_OBJ));
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
		assertEquals(getObjectSizeFromObjectDb(objs.contentsOfB),
				midx.getObjectSize(wc, objs.contentsOfB));
		assertEquals(-1, midx.getObjectSize(wc, UNKNOWN_OBJ));
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

		assertNull(midx.get(wc, UNKNOWN_OBJ));
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
		assertFalse(index.hasObject(UNKNOWN_OBJ));
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
		assertEquals(-1, index.findPosition(UNKNOWN_OBJ));
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
		assertEquals(-1, index.findOffset(UNKNOWN_OBJ));
	}

	@Test
	public void midx_reversePackIndex_findObject() throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		List<ObjectOffset> offsetOrdered = inRidxOrder(objs.midxOffsets);
		PackReverseIndex ridx = midx.getReverseIdx();

		assertFalse(offsetOrdered.isEmpty());
		for (int i = 0; i < offsetOrdered.size(); i++) {
			ObjectOffset entry = offsetOrdered.get(i);
			assertEquals(entry.obj().getId(), ridx.findObject(entry.offset()));
		}
	}

	@Test
	public void midx_reversePackIndex_findPosition_findObjectByPosition()
			throws Exception {
		TestRepoObjects objs = createThreePacks();
		PackMidx midx = writeAndOpenMidxAllPacks();

		List<ObjectOffset> offsetOrdered = inRidxOrder(objs.midxOffsets);
		PackReverseIndex ridx = midx.getReverseIdx();

		assertFalse(offsetOrdered.isEmpty());
		for (int i = 0; i < offsetOrdered.size(); i++) {
			ObjectOffset entry = offsetOrdered.get(i);
			assertEquals(entry.obj(), ridx
					.findObjectByPosition(ridx.findPosition(entry.offset())));
		}
	}

	/**
	 * A missing BTMP chunk must not cause a bitmap-covered object to be packed
	 * again when an uncovered tip requires a walk.
	 */
	@Test
	public void midxBitmapWithoutBtmpChunkDoesNotDuplicate() throws Exception {
		assertMidxBitmapIsUsed(false);
	}

	@Test
	public void midxBitmapWithBtmpChunkDoesNotDuplicate() throws Exception {
		assertMidxBitmapIsUsed(true);
	}

	private void assertMidxBitmapIsUsed(boolean withBtmp) throws Exception {
		TestRepository<Repository>.BranchBuilder branch = tr
				.branch("refs/heads/main");
		branch.commit().add("shared", "shared").create();
		tr.packAndPrune();
		RevCommit covered = branch.commit().add("b", "b").create();
		tr.packAndPrune();

		File midxFile = new File(
				repo.getObjectDatabase().getPackDirectory(),
				Constants.MIDX_FILE);
		NullProgressMonitor pm = NullProgressMonitor.INSTANCE;
		MidxWriter.writeMidx(pm, repo, repo.getObjectDatabase().getPacks(),
				midxFile, new PackConfig(repo));
		if (!withBtmp) {
			stripBtmpChunk(midxFile);
		}

		// BranchBuilder keeps the shared blob in the uncovered tip's tree.
		RevCommit tip = branch.commit().add("d", "d").create();
		try (FileRepository fresh = new FileRepository(repo.getDirectory());
				ObjectReader reader = fresh.newObjectReader();
				PackWriter pw = new PackWriter(new PackConfig(fresh), reader)) {
			BitmapIndex bitmap = reader.getBitmapIndex();
			assertNotNull("midx bitmap must be loaded", bitmap);
			assertNull("tip must require a walk", bitmap.getBitmap(tip));
			BitmapIndex.Bitmap parentBitmap = bitmap.getBitmap(covered);
			assertNotNull("parent must have a precomputed bitmap", parentBitmap);
			ObjectId shared = fresh.resolve("refs/heads/main:shared");
			boolean sharedCovered = false;
			for (BitmapObject object : parentBitmap) {
				sharedCovered |= object.getObjectId().equals(shared);
			}
			assertTrue("shared blob must be in the parent bitmap", sharedCovered);

			Set<ObjectId> expected = reachableObjects(fresh, tip);
			ByteArrayOutputStream idxOut = new ByteArrayOutputStream();
			pw.setUseBitmaps(true);
			pw.preparePack(pm, Set.of(tip), Set.of());
			pw.writePack(pm, pm, OutputStream.nullOutputStream());
			pw.writeIndex(idxOut);
			// Only the tip needs walking. Without parent bitmap reuse the
			// walk would also count misses for its ancestors.
			assertEquals(1, pw.getStatistics().getBitmapIndexMisses());
			assertPackedObjects(expected, pw, idxOut);
		}
	}

	private static Set<ObjectId> reachableObjects(Repository repository,
			ObjectId tip) throws IOException {
		Set<ObjectId> objects = new HashSet<>();
		try (ObjectWalk walk = new ObjectWalk(repository)) {
			walk.markStart(walk.parseCommit(tip));
			for (RevCommit commit : walk) {
				objects.add(commit.copy());
			}
			RevObject object;
			while ((object = walk.nextObject()) != null) {
				objects.add(object.copy());
			}
		}
		return objects;
	}

	private static void assertPackedObjects(Set<ObjectId> expected,
			PackWriter writer, ByteArrayOutputStream index) throws IOException {
		PackIndex packed = PackIndex
				.read(new ByteArrayInputStream(index.toByteArray()));
		Set<ObjectId> actual = new HashSet<>();
		for (PackIndex.MutableEntry entry : packed) {
			assertTrue("duplicate object: " + entry.toObjectId().name(),
					actual.add(entry.toObjectId()));
		}
		assertEquals(expected.size(), writer.getObjectCount());
		assertEquals(expected.size(), packed.getObjectCount());
		assertEquals(expected, actual);
	}

	// Mirror MultiPackIndexConstants chunk ids, not visible from this package.
	private static final int MIDX_CHUNKID_BITMAPPEDPACKS = 0x42544D50;

	private static final int MIDX_HEADER_SIZE = 12;

	private static final int CHUNK_LOOKUP_WIDTH = 12;

	private static final int NUM_CHUNKS_OFFSET = 6;

	private static final int BITMAP_CHECKSUM_OFFSET = 12;

	private static void stripBtmpChunk(File midxFile) throws IOException {
		stripChunk(midxFile, MIDX_CHUNKID_BITMAPPEDPACKS);
	}

	/** Remove a chunk and update both checksums and the bitmap's MIDX binding. */
	private static void stripChunk(File midxFile, int chunkId)
			throws IOException {
		byte[] in = Files.readAllBytes(midxFile.toPath());
		int checksumOffset = in.length - Constants.OBJECT_ID_LENGTH;
		byte[] oldChecksum = Arrays.copyOfRange(in, checksumOffset, in.length);
		assertArrayEquals(oldChecksum, Constants.newMessageDigest()
				.digest(Arrays.copyOf(in, checksumOffset)));
		int numChunks = in[NUM_CHUNKS_OFFSET] & 0xff;
		byte[] header = Arrays.copyOf(in, MIDX_HEADER_SIZE);
		header[NUM_CHUNKS_OFFSET] = (byte) (numChunks - 1);
		// There is one terminator after the remaining chunk entries.
		byte[] table = new byte[numChunks * CHUNK_LOOKUP_WIDTH];
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int entry = 0;
		int removed = 0;
		int offset = header.length + table.length;
		for (int i = 0; i < numChunks; i++) {
			int lookup = MIDX_HEADER_SIZE + i * CHUNK_LOOKUP_WIDTH;
			int id = NB.decodeInt32(in, lookup);
			int start = Math.toIntExact(NB.decodeInt64(in, lookup + 4));
			int end = Math.toIntExact(NB.decodeInt64(in,
					lookup + CHUNK_LOOKUP_WIDTH + 4));
			if (id == chunkId) {
				removed++;
				continue;
			}
			NB.encodeInt32(table, entry, id);
			NB.encodeInt64(table, entry + 4, offset);
			entry += CHUNK_LOOKUP_WIDTH;
			data.write(in, start, end - start);
			offset += end - start;
		}
		assertEquals("chunk to remove must occur exactly once", 1, removed);
		NB.encodeInt64(table, entry + 4, offset);
		ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
		rewritten.write(header);
		rewritten.write(table);
		data.writeTo(rewritten);
		byte[] checksum = Constants.newMessageDigest()
				.digest(rewritten.toByteArray());
		rewritten.write(checksum);

		File oldBitmap = new File(midxFile.getParentFile(), String.format(
				"%s-%s.bitmap", Constants.MIDX_FILE,
				ObjectId.fromRaw(oldChecksum).name()));
		byte[] bitmap = Files.readAllBytes(oldBitmap.toPath());
		int bitmapChecksumOffset = bitmap.length - Constants.OBJECT_ID_LENGTH;
		assertArrayEquals(Arrays.copyOfRange(bitmap, bitmapChecksumOffset,
				bitmap.length), Constants.newMessageDigest()
						.digest(Arrays.copyOf(bitmap, bitmapChecksumOffset)));
		assertArrayEquals(oldChecksum, Arrays.copyOfRange(bitmap,
				BITMAP_CHECKSUM_OFFSET,
				BITMAP_CHECKSUM_OFFSET + Constants.OBJECT_ID_LENGTH));
		System.arraycopy(checksum, 0, bitmap, BITMAP_CHECKSUM_OFFSET,
				checksum.length);
		byte[] bitmapChecksum = Constants.newMessageDigest()
				.digest(Arrays.copyOf(bitmap, bitmapChecksumOffset));
		System.arraycopy(bitmapChecksum, 0, bitmap, bitmapChecksumOffset,
				bitmapChecksum.length);
		File newBitmap = new File(midxFile.getParentFile(), String.format(
				"%s-%s.bitmap", Constants.MIDX_FILE,
				ObjectId.fromRaw(checksum).name()));
		Files.write(newBitmap.toPath(), bitmap);
		Files.write(midxFile.toPath(), rewritten.toByteArray());
		Files.delete(oldBitmap.toPath());
	}

	private PackMidx writeAndOpenMidxAllPacks() throws IOException {
		Collection<Pack> packs = ((ObjectDirectory) tr.getRepository()
				.getObjectDatabase()).getPacks();
		assertEquals(3, packs.size());

		File midxDest = new File(repo.getObjectDatabase().getPackDirectory(),
				Constants.MIDX_FILE);
		MidxWriter.writeMidx(new TextProgressMonitor(), repo, packs, midxDest,
				new PackConfig(repo));

		return new PackMidx(new Config(), midxDest, packs.stream().toList());

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

	private List<ObjectOffset> inRidxOrder(Map<RevObject, Integer> objs) {
		return objs.entrySet().stream()
				.map(e -> new ObjectOffset(e.getKey(), e.getValue()))
				.sorted(Comparator.comparing(ObjectOffset::offset)).toList();
	}

	private record ObjectOffset(RevObject obj, Integer offset) {
	}
}
