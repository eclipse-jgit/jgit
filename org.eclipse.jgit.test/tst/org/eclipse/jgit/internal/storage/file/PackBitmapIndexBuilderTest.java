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

import org.eclipse.jgit.internal.storage.file.BitmapIndexImpl.CompressedBitmap;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder.StoredEntry;
import org.eclipse.jgit.internal.storage.pack.BitmapCommit;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.Test;

import com.googlecode.javaewah.EWAHCompressedBitmap;

public class PackBitmapIndexBuilderTest {
	private static final Random random = new Random();

	private static int COMMIT_COUNT = 10
			* PackBitmapIndexBuilder.MAX_XOR_OFFSET_SEARCH;

	private static HashMap<ObjectId, Integer> OBJECTS = new HashMap<>();
	static {
		for (int i = 0; i < COMMIT_COUNT; i++) {
			OBJECTS.put(
					ObjectId.fromString(String.format("%04d", i)
							+ "000000000000000000000000000000000000"),
					Constants.OBJ_COMMIT);
			OBJECTS.put(
					ObjectId.fromString(String.format("%04d", i)
							+ "000000000000000000000000000000000001"),
					Constants.OBJ_BLOB);
			OBJECTS.put(
					ObjectId.fromString(String.format("%04d", i)
							+ "000000000000000000000000000000000002"),
					Constants.OBJ_TREE);
			OBJECTS.put(
					ObjectId.fromString(String.format("%04d", i)
							+ "000000000000000000000000000000000003"),
					Constants.OBJ_TAG);
		}
	}

	private static final Map<ObjectId, RandomBitmap> COMMIT_BITMAPS = OBJECTS
			.keySet().stream()
			.filter(objectId -> OBJECTS.get(objectId) == Constants.OBJ_COMMIT)
			.collect(toMap(id -> id, id -> new RandomBitmap()));

	private static final List<ObjectToPack> OBJECTS_TO_PACK = OBJECTS.keySet()
			.stream()
			.map(objectId -> new ObjectToPack(objectId, OBJECTS.get(objectId)))
			.collect(toCollection(ArrayList::new));

	private List<StoredEntry> createXorCompressedBitmaps(
			PackBitmapIndexBuilder builder) {
		List<StoredEntry> builderEntries = COMMIT_BITMAPS.keySet().stream()
				.map(objectId -> {
					return new StoredEntry(builder.findPosition(objectId),
							COMMIT_BITMAPS.get(objectId).bitmap,
							0, 0);
				}).collect(toList());

		List<StoredEntry> xorCompressedEntries = new ArrayList<>();
		for (int i = 0; i < builderEntries.size(); i++) {
			StoredEntry entry = builderEntries.get(i);
			EWAHCompressedBitmap bitmap = entry.getBitmap();
			int xorOffset = 0;
			if (i >= PackBitmapIndexBuilder.MAX_XOR_OFFSET_SEARCH) {
				xorOffset = random.nextInt(
						PackBitmapIndexBuilder.MAX_XOR_OFFSET_SEARCH - 1) + 1;
				bitmap = entry.getBitmap()
						.xor(builderEntries.get(i - xorOffset).getBitmap());
			}
			xorCompressedEntries.add(new StoredEntry(entry.getObjectId(),
					bitmap, xorOffset, entry.getFlags()));
		}

		return xorCompressedEntries;
	}

	private PackBitmapIndexBuilder getPopulatedBitmapsBuilder() {
		PackBitmapIndexBuilder bitmapsBuilder = new PackBitmapIndexBuilder(
				OBJECTS_TO_PACK);
		BitmapIndexImpl bitmapIndex = new BitmapIndexImpl(bitmapsBuilder);
		COMMIT_BITMAPS.forEach((objectId, bitmap) -> {
			bitmapsBuilder.processBitmapForWrite(
					BitmapCommit.newBuilder(objectId).build(),
					new CompressedBitmap(bitmap.bitmap, bitmapIndex),
					/* flags= */ 0);
		});
		return bitmapsBuilder;
	}

	@Test
	public void testPackBitmapIndexBuilder_inflateXorCompressedBitmaps() {
		PackBitmapIndexBuilder builder = getPopulatedBitmapsBuilder();
		List<StoredEntry> xorCompressedEntries = createXorCompressedBitmaps(
				builder);

		Stream<StoredEntry> actualBitmaps = PackBitmapIndexBuilder
				.getEwahCompressedBitmapStream(xorCompressedEntries);

		actualBitmaps.forEach(actualBitmap -> {
			ObjectId objectId = builder
					.getObject((int) actualBitmap.getObjectId());
			EWAHCompressedBitmap expectedBitmap = COMMIT_BITMAPS
					.get(objectId).bitmap;
			assertEquals("xor-inflated bitmap equals original bitmap",
					expectedBitmap, actualBitmap.getBitmap());
		});
		assertEquals("correct number of bitmaps are returned",
				COMMIT_BITMAPS.size(),
				PackBitmapIndexBuilder
						.getEwahCompressedBitmapStream(xorCompressedEntries)
						.collect(toList()).size());
	}

	private static class RandomBitmap {
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
