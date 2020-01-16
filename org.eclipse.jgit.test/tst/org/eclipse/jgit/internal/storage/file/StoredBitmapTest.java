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
