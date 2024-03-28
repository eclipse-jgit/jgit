/*
 * Copyright (C) 2024, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder.StoredEntry;
import static java.util.stream.Collectors.toList;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.googlecode.javaewah.EWAHCompressedBitmap;

public class PackBitmapIndexBuilderTest {
	@Test
	public void testPackBitmapIndexBuilder_inflateXorCompressedBitmaps() {
		List<EWAHCompressedBitmap> expectedBitmaps = Arrays.asList(
				new RandomBitmap().bitmap, new RandomBitmap().bitmap,
				new RandomBitmap().bitmap, new RandomBitmap().bitmap);

		List<StoredEntry> xorCompressedEntries = Arrays.asList(
				new StoredEntry(/* objectId= */ 0,
						/* bitmap= */ expectedBitmaps.get(0),
						/* xorOffset= */ 0, /* flags= */ 0),
				new StoredEntry(/* objectId= */ 1,
						/* bitmap= */ expectedBitmaps.get(1)
								.xor(expectedBitmaps.get(0)),
						/* xorOffset= */ 1, /* flags= */ 0),
				new StoredEntry(/* objectId= */ 2,
						/* bitmap= */ expectedBitmaps.get(2)
								.xor(expectedBitmaps.get(1)),
						/* xorOffset= */ 1, /* flags= */ 0),
				new StoredEntry(/* objectId= */ 2,
						/* bitmap= */ expectedBitmaps.get(3)
								.xor(expectedBitmaps.get(1)),
						/* xorOffset= */ 2, /* flags= */ 0));

		List<StoredEntry> actualBitmaps = PackBitmapIndexBuilder
				.getEwahCompressedBitmapStream(xorCompressedEntries,
						/* maxXorOffset= */ 2)
				.collect(toList());

		assertEquals(expectedBitmaps, actualBitmaps.stream()
				.map(b -> b.getBitmap()).collect(toList()));

		assertEquals("correct number of bitmaps are returned",
				xorCompressedEntries.size(), actualBitmaps.size());
		for (int i = 0; i < actualBitmaps.size(); i++) {
			assertEquals("xor-inflated bitmap equals original bitmap",
					expectedBitmaps.get(i), actualBitmaps.get(i).getBitmap());
		}
	}

	private static class RandomBitmap {
		private static final Random random = new Random();

		private static final int BITMAP_WORDS = 2;

		final long[] raw = new long[BITMAP_WORDS];

		final EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();

		RandomBitmap() {
			for (int i = 0; i < raw.length; i++) {
				long l = random.nextLong();
				raw[i] = l;
				bitmap.addWord(l);
			}
		}
	}
}
