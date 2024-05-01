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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
	public void testGetSizeWithoutXor() {
		EWAHCompressedBitmap base = bitmapOf(100);
		StoredBitmap sb = newStoredBitmap(base);
		assertEquals(base.sizeInBytes(), sb.getCurrentSizeInBytes());
		sb.getBitmap();
		assertEquals(base.sizeInBytes(), sb.getCurrentSizeInBytes());
	}

	@Test
	public void testGetSizeWithOneXor() {
		EWAHCompressedBitmap base = bitmapOf(100, 101);
		EWAHCompressedBitmap xor = bitmapOf(100);
		StoredBitmap sb = newStoredBitmap(base, xor);
		assertEquals(xor.sizeInBytes(), sb.getCurrentSizeInBytes());
	}

	@Test
	public void testIsBase() {
		EWAHCompressedBitmap one = bitmapOf(100, 101);
		EWAHCompressedBitmap two = bitmapOf(100);
		StoredBitmap baseBitmap = newStoredBitmap(one);
		StoredBitmap xoredBitmap = newStoredBitmap(one, two);
		assertTrue(baseBitmap.isBase());
		assertFalse(xoredBitmap.isBase());
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
