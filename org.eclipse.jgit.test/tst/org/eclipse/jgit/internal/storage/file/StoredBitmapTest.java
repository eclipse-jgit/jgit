/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import com.googlecode.javaewah.EWAHCompressedBitmap;

public class StoredBitmapTest {

	@Test
	public void testGetBitmapWithoutXor() {
		EWAHCompressedBitmap b = bitmapOf(100);
		StoredBitmap sb = newStoredBitmap(bitmapOf(100));
		assertEquals(b, sb.getBitmap());
	}

	@Test
	public void testGetBitmapWithOneXor() {
		StoredBitmap sb = newStoredBitmap(bitmapOf(100), bitmapOf(100, 101));
		assertEquals(bitmapOf(101), sb.getBitmap());
	}

	@Test
	public void testGetBitmapWithThreeXor() {
		StoredBitmap sb = newStoredBitmap(
				bitmapOf(100),
				bitmapOf(90, 101),
				bitmapOf(100, 101),
				bitmapOf(50));
		assertEquals(bitmapOf(50, 90), sb.getBitmap());
		assertEquals(bitmapOf(50, 90), sb.getBitmap());
	}

	@Test
	public void testGetChainLengthWithoutXor() {
		StoredBitmap sb = newStoredBitmap(bitmapOf(100));
		assertEquals(0, sb.getChainDepth());
	}

	@Test
	public void testGetChainLengthOneXor() {
		StoredBitmap sb = newStoredBitmap(bitmapOf(100), bitmapOf(100, 101));
		assertEquals(1, sb.getChainDepth());
	}

	@Test
	public void testGetChainLenghtWithThreeXor() {
		StoredBitmap sb = newStoredBitmap(bitmapOf(100), bitmapOf(90, 101),
				bitmapOf(100, 101), bitmapOf(50));
		assertEquals(3, sb.getChainDepth());
	}

	@Test
	public void testGetSizeWithoutXor() {
		EWAHCompressedBitmap base = bitmapOf(100);
		StoredBitmap sb = newStoredBitmap(base);
		assertEquals(base.sizeInBytes(), sb.getCurrentBitmapSize());
		sb.getBitmap();
		assertEquals(base.sizeInBytes(), sb.getCurrentBitmapSize());
	}

	@Test
	public void testGetSizeWithOneXor() {
		EWAHCompressedBitmap base = bitmapOf(100, 101);
		EWAHCompressedBitmap xor = bitmapOf(100);
		StoredBitmap sb = newStoredBitmap(xor, base);
		assertEquals(base.sizeInBytes() + xor.sizeInBytes(),
				sb.getCurrentBitmapSize());
		EWAHCompressedBitmap resultBitmap = sb.getBitmap();
		assertEquals(resultBitmap.sizeInBytes(), sb.getCurrentBitmapSize());
	}

	@Test
	public void testGetSizeWithThreeXor() {
		EWAHCompressedBitmap base = bitmapOf(100);
		EWAHCompressedBitmap xor = bitmapOf(90, 101);
		EWAHCompressedBitmap xor2 = bitmapOf(100, 101);
		EWAHCompressedBitmap xor3 = bitmapOf(50);
		StoredBitmap sb = newStoredBitmap(xor, xor2, xor3, base);
		long rawSize = base.sizeInBytes() + xor.sizeInBytes()
				+ xor2.sizeInBytes() + xor3.sizeInBytes();
		assertEquals(rawSize, sb.getCurrentBitmapSize());
		EWAHCompressedBitmap resultBitmap = sb.getBitmap();
		// StoredBitmap has only the final bitmap (breaking the chain)
		assertEquals(resultBitmap.sizeInBytes(), sb.getCurrentBitmapSize());
	}

	private static final StoredBitmap newStoredBitmap(
			EWAHCompressedBitmap... bitmaps) {
		StoredBitmap sb = null;
		for (EWAHCompressedBitmap bitmap : bitmaps)
			sb = new StoredBitmap(ObjectId.zeroId(), bitmap, sb, 0);
		return sb;
	}

	private static final EWAHCompressedBitmap bitmapOf(int... bits) {
		EWAHCompressedBitmap b = new EWAHCompressedBitmap();
		for (int bit : bits)
			b.set(bit);
		return b;
	}
}
