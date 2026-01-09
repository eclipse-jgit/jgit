package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.pack.BitmapCommit;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.googlecode.javaewah.EWAHCompressedBitmap;

@RunWith(Parameterized.class)
public class PackBitmapIndexBuilderTest {

	/**
	 * Numbers are oids
	 *
	 * <pre>
	 *     tip
	 *     10 (commit) -> 9 (tree) -> 8 (blob)
	 *      |
	 *      7 (commit) -> 6 (tree) -> 5,4  (blobs)
	 *      |
	 *      3 (commit) -> 2 (tree) ->  1 (blob)
	 * </pre>
	 */
	private static final List<ObjectToPack> opts = List.of(
			otp(10, 400, Constants.OBJ_COMMIT), otp(9, 900, Constants.OBJ_TREE),
			otp(8, 280, Constants.OBJ_BLOB), otp(7, 200, Constants.OBJ_COMMIT),
			otp(6, 410, Constants.OBJ_TREE), otp(5, 500, Constants.OBJ_BLOB),
			otp(4, 450, Constants.OBJ_BLOB), otp(3, 470, Constants.OBJ_COMMIT),
			otp(2, 700, Constants.OBJ_TREE), otp(1, 240, Constants.OBJ_BLOB));

	public enum TestSetup {
		SINGLE_BUILDER
	}

	@Parameterized.Parameters(name = "{0}")
	public static Iterable<TestSetup> data() throws IOException {
		return Arrays.stream(TestSetup.values()).toList();
	}

	private final TestSetup currentSetup;

	public PackBitmapIndexBuilderTest(TestSetup ts) {
		this.currentSetup = ts;
	}

	@Test
	public void getObjectSet() {
		ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> objectSet = createBuilder()
				.getObjectSet();
		for (int i = 1; i <= 10; i++) {
			assertTrue(objectSet.contains(oid(i)));
		}
	}

	@Test
	public void addBitmap_getBitmap() {
		EWAHCompressedBitmap added = EWAHCompressedBitmap.bitmapOf(1, 3, 5);
		PackBitmapIndexBuilder pbi = createBuilder();
		pbi.addBitmap(oid(11), added, 10);

		EWAHCompressedBitmap retrieved = pbi.getBitmap(oid(11));
		assertEquals(added, retrieved);
	}

	@Test
	public void ofObjectType() {
		PackBitmapIndexBuilder pbi = createBuilder();
		BitmapIndexImpl bii = new BitmapIndexImpl(pbi);
		BitmapIndex.BitmapBuilder bb = bii.newBitmapBuilder();
		bb.addObject(oid(10), Constants.OBJ_COMMIT);
		bb.addObject(oid(9), Constants.OBJ_TREE);
		bb.addObject(oid(8), Constants.OBJ_BLOB);
		BitmapIndex.Bitmap b = bb.build();

		EWAHCompressedBitmap commits = pbi.ofObjectType(b.retrieveCompressed(),
				Constants.OBJ_COMMIT);
		assertInBitmap(pbi, commits, 10);
		assertNotInBitmap(pbi, commits, 9);
		assertNotInBitmap(pbi, commits, 8);

		EWAHCompressedBitmap trees = pbi.ofObjectType(b.retrieveCompressed(),
				Constants.OBJ_TREE);
		assertNotInBitmap(pbi, trees, 10);
		assertInBitmap(pbi, trees, 9);
		assertNotInBitmap(pbi, trees, 8);

		EWAHCompressedBitmap blobs = pbi.ofObjectType(b.retrieveCompressed(),
				Constants.OBJ_BLOB);
		assertNotInBitmap(pbi, blobs, 10);
		assertNotInBitmap(pbi, blobs, 9);
		assertInBitmap(pbi, blobs, 8);
	}

	@Test
	public void findPosition() {
		PackBitmapIndexBuilder pbi = createBuilder();
		// All valid, non-repeating positions
		Set<Integer> seen = new HashSet<>();
		for (int oidAsInt = 1; oidAsInt <= 10; oidAsInt++) {
			int p = pbi.findPosition(oid(oidAsInt));
			assertTrue(p >= 0);
			assertTrue(p < 10);
			assertFalse(seen.contains(p));
			seen.add(p);
		}
	}

	@Test
	public void getObject() {
		PackBitmapIndexBuilder pbi = createBuilder();
		for (int oidAsInt = 1; oidAsInt <= 10; oidAsInt++) {
			ObjectId expected = oid(oidAsInt);
			int p = pbi.findPosition(expected);
			ObjectId actual = pbi.getObject(p);
			assertEquals(expected, actual);
		}
	}

	@Test
	public void commits() {
		PackBitmapIndexBuilder pbi = createBuilder();
		EWAHCompressedBitmap commits = pbi.getCommits();
		assertInBitmap(pbi, commits, 10);
		assertInBitmap(pbi, commits, 7);
		assertInBitmap(pbi, commits, 3);

		assertNotInBitmap(pbi, commits, 9);
		assertNotInBitmap(pbi, commits, 8);
	}

	@Test
	public void trees() {
		PackBitmapIndexBuilder pbi = createBuilder();
		EWAHCompressedBitmap trees = pbi.getTrees();
		assertInBitmap(pbi, trees, 9);
		assertInBitmap(pbi, trees, 6);
		assertInBitmap(pbi, trees, 2);

		assertNotInBitmap(pbi, trees, 10);
		assertNotInBitmap(pbi, trees, 8);
	}

	@Test
	public void blobs() {
		PackBitmapIndexBuilder pbi = createBuilder();
		EWAHCompressedBitmap blobs = pbi.getBlobs();
		assertInBitmap(pbi, blobs, 8);
		assertInBitmap(pbi, blobs, 5);
		assertInBitmap(pbi, blobs, 4);
		assertInBitmap(pbi, blobs, 1);

		assertNotInBitmap(pbi, blobs, 10);
		assertNotInBitmap(pbi, blobs, 9);
	}

	@Test
	public void getBitmapCount() {
		// The builder only counts bitmaps "in progress" (in xor queue or ready
		// to write)
		assertEquals(0, createBuilder().getBitmapCount());
	}

	@Test
	public void getBitmapCount_newBitmaps() {

	}

	@Test
	public void reachableObjects_setInBitmap() {
		PackBitmapIndexBuilder builder = createBuilder();
		EWAHCompressedBitmap bitmapTen = builder.getBitmap(oid(10));
		assertNotNull(bitmapTen);
		for (int oidAsInt = 10; oidAsInt > 0; oidAsInt--) {
			assertInBitmap(builder, bitmapTen, oidAsInt);
		}

		EWAHCompressedBitmap bitmapSeven = builder.getBitmap(oid(7));
		assertNotNull(bitmapSeven);
		assertNotInBitmap(builder, bitmapSeven, 10);
		assertNotInBitmap(builder, bitmapSeven, 9);
		assertNotInBitmap(builder, bitmapSeven, 8);
		for (int i = 7; i > 0; i--) {
			assertInBitmap(builder, bitmapSeven, i);
		}
	}

	@Test
	public void processBitmapForWrite() {
		// processBitmapForWrite keeps the bitmaps in the builder instead of in
		// the superclass, to calculate the XORs
		PackBitmapIndexBuilder builder = createBuilder();
		BitmapIndex bb = new BitmapIndexImpl(builder);
		BitmapIndex.BitmapBuilder oneBitmap = bb.newBitmapBuilder();
		oneBitmap.addObject(oid(10), Constants.OBJ_COMMIT);
		oneBitmap.addObject(oid(9), Constants.OBJ_TREE);
		oneBitmap.addObject(oid(8), Constants.OBJ_BLOB);
		oneBitmap.addObject(oid(7), Constants.OBJ_COMMIT);
		oneBitmap.addObject(oid(6), Constants.OBJ_TREE);
		oneBitmap.addObject(oid(5), Constants.OBJ_BLOB);

		BitmapIndex.BitmapBuilder anotherBitmap = bb.newBitmapBuilder();
		anotherBitmap.addObject(oid(7), Constants.OBJ_COMMIT);
		anotherBitmap.addObject(oid(6), Constants.OBJ_TREE);
		anotherBitmap.addObject(oid(5), Constants.OBJ_BLOB);

		builder.processBitmapForWrite(
				new BitmapCommit(oid(10), false, 0, false), oneBitmap, 0);
		builder.processBitmapForWrite(new BitmapCommit(oid(7), false, 0, false),
				oneBitmap, 0);
		assertEquals(2, builder.getBitmapCount());

		List<PackBitmapIndexBuilder.StoredEntry> ses = builder
				.getCompressedBitmaps();
		assertEquals(2, ses.size());
		assertEquals(oid(7), ses.get(0).getObjectId());
		assertEquals(6, ses.get(0).getIdxPosition());

		assertEquals(oid(10), ses.get(1).getObjectId());
		assertEquals(9, ses.get(1).getIdxPosition());
	}

	@Test
	public void getObjectCount() {
		assertEquals(10, createBuilder().getObjectCount());
	}

	private static void assertInBitmap(PackBitmapIndexBuilder builder,
			EWAHCompressedBitmap bitmap, int oidAsInt) {
		ObjectId oid = oid(oidAsInt);
		int pos = builder.findPosition(oid);
		assertTrue(pos >= 0);
		assertTrue(bitmap.get(pos));
	}

	private static void assertNotInBitmap(PackBitmapIndexBuilder builder,
			EWAHCompressedBitmap bitmap, int oidAsInt) {
		ObjectId oid = oid(oidAsInt);
		int pos = builder.findPosition(oid);
		assertTrue(pos >= 0);
		assertFalse(bitmap.get(pos));
	}

	private PackBitmapIndexBuilder createBuilder() {
		ArrayList<ObjectToPack> objectsToPack = new ArrayList<>(opts);
		Collections.reverse(objectsToPack);
		return switch (currentSetup) {
		case SINGLE_BUILDER -> setupSinglePackBitmapIndex(objectsToPack);
		};
	}

	static PackBitmapIndexBuilder setupSinglePackBitmapIndex(
			List<ObjectToPack> objectsToPack) {
		PackBitmapIndexBuilder b = new PackBitmapIndexBuilder(objectsToPack,
				null);

		BitmapIndex bi = new BitmapIndexImpl(b);
		// Base commit (3, 2, 1)
		BitmapIndex.BitmapBuilder bitmapOne = bi.newBitmapBuilder();
		bitmapOne.addObject(oid(3), Constants.OBJ_COMMIT);
		bitmapOne.addObject(oid(2), Constants.OBJ_TREE);
		bitmapOne.addObject(oid(1), Constants.OBJ_BLOB);
		b.addBitmap(oid(3), bitmapOne.build(), 0);

		// Middle commit (7, 6, 5, 4) with base commit as parent
		BitmapIndex.BitmapBuilder middleBitmap = bi.newBitmapBuilder();
		middleBitmap.addObject(oid(7), Constants.OBJ_COMMIT);
		middleBitmap.addObject(oid(6), Constants.OBJ_TREE);
		middleBitmap.addObject(oid(5), Constants.OBJ_BLOB);
		middleBitmap.addObject(oid(4), Constants.OBJ_BLOB);
		middleBitmap.or(bitmapOne);
		b.addBitmap(oid(7), middleBitmap, 0);

		BitmapIndex.BitmapBuilder tipBitmap = bi.newBitmapBuilder();
		tipBitmap.addObject(oid(10), Constants.OBJ_COMMIT);
		tipBitmap.addObject(oid(9), Constants.OBJ_TREE);
		tipBitmap.addObject(oid(8), Constants.OBJ_BLOB);
		tipBitmap.or(middleBitmap);
		b.addBitmap(oid(10), tipBitmap, 0);

		return b;
	}

//	static TestInput setupChainedPackBitmapIndex() {
//		List<ObjectToPack> optOne = new ArrayList<>(
//				List.of(otp(4, 200, Constants.OBJ_COMMIT),
//						otp(5, 240, Constants.OBJ_TREE),
//						otp(9, 280, Constants.OBJ_BLOB),
//						otp(10, 400, Constants.OBJ_COMMIT),
//						otp(2, 410, Constants.OBJ_TREE)));
//		List<ObjectToPack> optTwo = new ArrayList<>(
//				List.of(otp(6, 450, Constants.OBJ_BLOB),
//						otp(1, 500, Constants.OBJ_COMMIT),
//						otp(3, 700, Constants.OBJ_BLOB),
//						otp(7, 900, Constants.OBJ_COMMIT),
//						otp(8, 910, Constants.OBJ_TREE)));
//
//		PackBitmapIndexBuilder b = new PackBitmapIndexBuilder(opts, null);
//
//		BitmapIndex bi = new BitmapIndexImpl(b);
//
//		// Base commit (4, 5, 9)
//		BitmapIndex.BitmapBuilder bitmapOne = bi.newBitmapBuilder();
//		bitmapOne.addObject(oid(4), Constants.OBJ_COMMIT);
//		bitmapOne.addObject(oid(5), Constants.OBJ_TREE);
//		bitmapOne.addObject(oid(9), Constants.OBJ_BLOB);
//		b.addBitmap(oid(4), bitmapOne.build(), 0);
//
//		// Commit (10, 2, 6) with base commit as parent
//		BitmapIndex.BitmapBuilder middleBitmap = bi.newBitmapBuilder();
//		middleBitmap.addObject(oid(10), Constants.OBJ_COMMIT);
//		middleBitmap.addObject(oid(2), Constants.OBJ_TREE);
//		middleBitmap.addObject(oid(6), Constants.OBJ_BLOB);
//		middleBitmap.or(bitmapOne);
//		b.addBitmap(oid(10), middleBitmap, 0);
//
//		return new TestInput("single", b);
//	}

	private static ObjectToPack otp(int oid, long offset, int type) {
		ObjectToPack otp = new ObjectToPack(oid(oid), type);
		otp.setOffset(offset);
		return otp;
	}

	private static ObjectId oid(int n) {
		return ObjectId.fromRaw(new int[] { 0, 0, 0, 0, n });
	}
}
