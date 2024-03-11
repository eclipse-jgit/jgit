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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.BitmapCommit;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.util.BlockList;

import com.googlecode.javaewah.EWAHCompressedBitmap;

/**
 * Implementation of {@link BasePackBitmapIndexBuilder} that performs
 * xor-compression on the bitmaps for writing.
 */
public class XorCompressedPackBitmapIndexBuilder
		extends BasePackBitmapIndexBuilder {
	private static final int MAX_XOR_OFFSET_SEARCH = 10;

	final BlockList<PositionEntry> byOffset;

	final ObjectIdOwnerMap<PositionEntry> positionEntries = new ObjectIdOwnerMap<>();

	private final LinkedList<StoredBitmap>
			bitmapsToWriteXorBuffer = new LinkedList<>();

	private List<StoredEntry> bitmapsToWrite = new ArrayList<>();

	/**
	 * Creates a PackBitmapIndex used for building the contents of an index
	 * file.
	 *
	 * @param objects
	 *            objects sorted by name. The list must be initially sorted by
	 *            ObjectId (name); it will be resorted in place.
	 */
	public XorCompressedPackBitmapIndexBuilder(List<ObjectToPack> objects) {
		super(objects);
		byOffset = new BlockList<>(objects.size());
		sortByOffsetAndIndex(byOffset, positionEntries, objects);
	}

	/**
	 * Processes a commit and prepares its bitmap to write to the bitmap index
	 * file.
	 *
	 * @param c
	 *            the commit corresponds to the bitmap.
	 * @param bitmap
	 *            the bitmap to be written.
	 * @param flags
	 *            the flags of the commit.
	 */
	@Override
	public void processBitmapForWrite(BitmapCommit c, Bitmap bitmap,
			int flags) {
		EWAHCompressedBitmap compressed = bitmap.retrieveCompressed();
		compressed.trim();
		StoredBitmap newest = new StoredBitmap(c, compressed, null, flags);

		bitmapsToWriteXorBuffer.add(newest);
		if (bitmapsToWriteXorBuffer.size() > MAX_XOR_OFFSET_SEARCH) {
			bitmapsToWrite.add(
					generateStoredEntry(bitmapsToWriteXorBuffer.pollFirst()));
		}

		if (c.isAddToIndex()) {
			// The Bitmap map in the base class is used to make revwalks
			// efficient, so only add bitmaps that keep it efficient without
			// bloating memory.
			addBitmap(c, bitmap, flags);
		}
	}

	private StoredEntry generateStoredEntry(StoredBitmap bitmapToWrite) {
		int bestXorOffset = 0;
		EWAHCompressedBitmap bestBitmap = bitmapToWrite.getBitmap();

		int offset = 1;
		for (StoredBitmap curr : bitmapsToWriteXorBuffer) {
			EWAHCompressedBitmap bitmap = curr.getBitmap()
					.xor(bitmapToWrite.getBitmap());
			if (bitmap.sizeInBytes() < bestBitmap.sizeInBytes()) {
				bestBitmap = bitmap;
				bestXorOffset = offset;
			}
			offset++;
		}

		PositionEntry entry = positionEntries.get(bitmapToWrite);
		if (entry == null) {
			throw new IllegalStateException();
		}
		bestBitmap.trim();
		StoredEntry result = new StoredEntry(entry.namePosition, bestBitmap,
				bestXorOffset, bitmapToWrite.getFlags());

		return result;
	}

	/**
	 * Remove all the bitmaps entries added.
	 *
	 * @param size
	 *            the expected number of bitmap entries to be written.
	 */
	@Override
	public void resetBitmaps(int size) {
		getBitmaps().clear();
		bitmapsToWrite = new ArrayList<>(size);
	}

	@Override
	public int getBitmapCount() {
		return bitmapsToWriteXorBuffer.size() + bitmapsToWrite.size();
	}

	/**
	 * Get list of xor compressed entries that need to be written.
	 *
	 * @return a list of the xor compressed entries.
	 */
	@Override
	public List<StoredEntry> getCompressedBitmaps() {
		while (!bitmapsToWriteXorBuffer.isEmpty()) {
			bitmapsToWrite.add(
					generateStoredEntry(bitmapsToWriteXorBuffer.pollFirst()));
		}

		Collections.reverse(bitmapsToWrite);
		return bitmapsToWrite;
	}

	@Override
	public int findPosition(AnyObjectId objectId) {
		PositionEntry entry = positionEntries.get(objectId);
		if (entry == null)
			return -1;
		return entry.offsetPosition;
	}

	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		ObjectId objectId = byOffset.get(position);
		if (objectId == null)
			throw new IllegalArgumentException();
		return objectId;
	}

	@Override
	public ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> getObjectSet() {
		ObjectIdOwnerMap<ObjectIdOwnerMap.Entry> r = new ObjectIdOwnerMap<>();
		for (PositionEntry e : byOffset) {
			r.add(new ObjectIdOwnerMap.Entry(e) {
				// A new entry that copies the ObjectId
			});
		}
		return r;
	}

	private static void sortByOffsetAndIndex(BlockList<PositionEntry> byOffset,
			ObjectIdOwnerMap<PositionEntry> positionEntries,
			List<ObjectToPack> entries) {
		for (int i = 0; i < entries.size(); i++) {
			positionEntries.add(new PositionEntry(entries.get(i), i));
		}
		Collections.sort(entries, (ObjectToPack a, ObjectToPack b) -> Long
				.signum(a.getOffset() - b.getOffset()));
		for (int i = 0; i < entries.size(); i++) {
			PositionEntry e = positionEntries.get(entries.get(i));
			e.offsetPosition = i;
			byOffset.add(e);
		}
	}

	private static final class PositionEntry extends ObjectIdOwnerMap.Entry {
		final int namePosition;

		int offsetPosition;

		PositionEntry(AnyObjectId objectId, int namePosition) {
			super(objectId);
			this.namePosition = namePosition;
		}
	}
}
