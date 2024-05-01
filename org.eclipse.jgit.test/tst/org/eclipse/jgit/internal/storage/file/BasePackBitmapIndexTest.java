/*
 * Copyright (c) 2023, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.javaewah.EWAHCompressedBitmap;

public class BasePackBitmapIndexTest {

	private ObjectId baseOid;

	private StoredBitmap baseBitmap;

	private ObjectId xorOid;

	private StoredBitmap xorBitmap;

	private ObjectIdOwnerMap<StoredBitmap> bitmaps;

	@Before
	public void setUp() {
		baseOid = ObjectId
				.fromString("c46f36f2bfc96d6d6f75bd71ee33625293aee690");
		baseBitmap = newBaseStoredBitmap(baseOid, bitmapOf(100));
		xorOid = ObjectId
				.fromString("52c18ae15f8fa3787f920e68791367dae2e1af2d");
		xorBitmap = newXorStoredBitmap(xorOid, bitmapOf(200, 300), baseBitmap);
		bitmaps = new ObjectIdOwnerMap<>();
		bitmaps.add(baseBitmap);
		bitmaps.add(xorBitmap);
	}

	@Test
	public void testBitmapCounts() {
		TestPackBitmapIndex index = new TestPackBitmapIndex(bitmaps);

		assertEquals(1, index.getBaseBitmapCount());
		assertEquals(1, index.getXorBitmapCount());
		assertEquals(2, index.getBitmapCount());
	}

	@Test
	public void testBitmapCounts_xorResolved() {
		TestPackBitmapIndex index = new TestPackBitmapIndex(bitmaps);
		index.getBitmap(xorOid);

		assertEquals(2, index.getBaseBitmapCount());
		assertEquals(0, index.getXorBitmapCount());
		assertEquals(2, index.getBitmapCount());
	}

	@Test
	public void testBitmapSizes() {
		TestPackBitmapIndex index = new TestPackBitmapIndex(bitmaps);

		assertEquals(baseBitmap.getCurrentSizeInBytes(),
				index.getBaseBitmapSizeInBytes());
		assertEquals(xorBitmap.getCurrentSizeInBytes(),
				index.getXorBitmapSizeInBytes());
	}

	@Test
	public void testBitmapSizes_xorResolved() {
		TestPackBitmapIndex index = new TestPackBitmapIndex(bitmaps);
		index.getBitmap(xorOid);

		assertTrue(baseBitmap.getCurrentSizeInBytes() < index
				.getBaseBitmapSizeInBytes());
		assertEquals(0, index.getXorBitmapSizeInBytes());
	}

	private static final StoredBitmap newBaseStoredBitmap(ObjectId oid,
			EWAHCompressedBitmap base) {
		return new StoredBitmap(oid, base, null, 0);
	}

	private static StoredBitmap newXorStoredBitmap(ObjectId oid,
			EWAHCompressedBitmap xorMask, StoredBitmap base) {
		return new StoredBitmap(oid, xorMask, base, 0);
	}

	private static final EWAHCompressedBitmap bitmapOf(int... bits) {
		EWAHCompressedBitmap b = new EWAHCompressedBitmap();
		for (int bit : bits)
			b.set(bit);
		return b;
	}

	private static class TestPackBitmapIndex extends BasePackBitmapIndex {
		TestPackBitmapIndex(ObjectIdOwnerMap<StoredBitmap> bitmaps) {
			super(bitmaps);
		}

		@Override
		public int findPosition(AnyObjectId objectId) {
			throw new IllegalStateException();
		}

		@Override
		public ObjectId getObject(int position)
				throws IllegalArgumentException {
			throw new IllegalStateException();
		}

		@Override
		public EWAHCompressedBitmap ofObjectType(EWAHCompressedBitmap bitmap,
				int type) {
			throw new IllegalStateException();
		}

		@Override
		public int getObjectCount() {
			throw new IllegalStateException();
		}

		@Override
		public int getBitmapCount() {
			return getBitmaps().size();
		}
	}
}
