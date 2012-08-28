/*
 * Copyright (C) 2012, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.file;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.storage.file.BitmapIndexImpl.CompressedBitmap;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.util.BlockList;

/**
 * Helper for constructing {@link PackBitmapIndex}es.
 */
public class PackBitmapIndexBuilder extends BasePackBitmapIndex {
	private static final int MAX_XOR_OFFSET_SEARCH = 10;

	private final EWAHCompressedBitmap commits;

	private final EWAHCompressedBitmap trees;

	private final EWAHCompressedBitmap blobs;

	private final EWAHCompressedBitmap tags;

	private final ObjectToPack[] byOffset;

	private final BlockList<StoredBitmap>
			byAddOrder = new BlockList<StoredBitmap>();

	private final ObjectIdOwnerMap<PositionEntry>
			positionEntries = new ObjectIdOwnerMap<PositionEntry>();

	/**
	 * Creates a PackBitmapIndex used for building the contents of an index
	 * file.
	 *
	 * @param byName
	 *            objects sorted by name.
	 */
	public PackBitmapIndexBuilder(List<ObjectToPack> byName) {
		super(new ObjectIdOwnerMap<StoredBitmap>());
		byOffset = sortByOffset(byName);

		int sizeInWords = Math.max(byOffset.length / 64, 4);
		commits = new EWAHCompressedBitmap(sizeInWords);
		trees = new EWAHCompressedBitmap(sizeInWords);
		blobs = new EWAHCompressedBitmap(sizeInWords);
		tags = new EWAHCompressedBitmap(sizeInWords);
		for (int i = 0; i < byOffset.length; i++) {
			int type = byOffset[i].getType();
			switch (type) {
			case Constants.OBJ_COMMIT:
				commits.set(i);
				break;
			case Constants.OBJ_TREE:
				trees.set(i);
				break;
			case Constants.OBJ_BLOB:
				blobs.set(i);
				break;
			case Constants.OBJ_TAG:
				tags.set(i);
				break;
			default:
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().badObjectType, String.valueOf(type)));
			}
		}
	}

	private ObjectToPack[] sortByOffset(List<ObjectToPack> entries) {
		ObjectToPack[] result = new ObjectToPack[entries.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = entries.get(i);
			positionEntries.add(new PositionEntry(result[i], i));
		}
		Arrays.sort(result, new Comparator<ObjectToPack>() {
			public int compare(ObjectToPack a, ObjectToPack b) {
				return Long.signum(a.getOffset() - b.getOffset());
			}
		});
		for (int i = 0; i < result.length; i++)
			positionEntries.get(result[i]).offsetPosition = i;
		return result;
	}

	/**
	 * Stores the bitmap for the objectId.
	 *
	 * @param objectId
	 *            the object id key for the bitmap.
	 * @param bitmap
	 *            the bitmap
	 */
	public void addBitmap(AnyObjectId objectId, Bitmap bitmap) {
		if (bitmap instanceof BitmapBuilder)
			bitmap = ((BitmapBuilder) bitmap).build();

		EWAHCompressedBitmap compressed;
		if (bitmap instanceof CompressedBitmap)
			compressed = ((CompressedBitmap) bitmap).getEwahCompressedBitmap();
		else
			throw new IllegalArgumentException(bitmap.getClass().toString());

		StoredBitmap result = new StoredBitmap(objectId, compressed, null);
		getBitmaps().add(result);
		byAddOrder.add(result);
	}

	@Override
	public EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type) {
		switch (type) {
		case Constants.OBJ_BLOB:
			return getBlobs().and(bitmap);
		case Constants.OBJ_TREE:
			return getTrees().and(bitmap);
		case Constants.OBJ_COMMIT:
			return getCommits().and(bitmap);
		case Constants.OBJ_TAG:
			return getTags().and(bitmap);
		}
		throw new IllegalArgumentException();
	}

	@Override
	public int findPosition(AnyObjectId objectId) {
		PositionEntry entry = positionEntries.get(objectId);
		if (entry == null)
			throw new IllegalStateException();
		return entry.offsetPosition;
	}

	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		ObjectId objectId = byOffset[position];
		if (objectId == null)
			throw new IllegalArgumentException();
		return objectId;
	}

	/** @return the commit object bitmap. */
	public EWAHCompressedBitmap getCommits() {
		return commits;
	}

	/** @return the tree object bitmap. */
	public EWAHCompressedBitmap getTrees() {
		return trees;
	}

	/** @return the blob object bitmap. */
	public EWAHCompressedBitmap getBlobs() {
		return blobs;
	}

	/** @return the tag object bitmap. */
	public EWAHCompressedBitmap getTags() {
		return tags;
	}

	/** @return the index storage options. */
	public byte getOptions() {
		return PackBitmapIndexV1.OPT_FULL;
	}

	/** @return the number of bitmaps. */
	public int getBitmapCount() {
		return getBitmaps().size();
	}

	/** Removes all the bitmaps entries added. */
	public void clearBitmaps() {
		byAddOrder.clear();
		getBitmaps().clear();
	}

	@Override
	public int getObjectCount() {
		return byOffset.length;
	}

	/** @return an iterator over the xor compressed entries. */
	public Iterable<StoredEntry> getCompressedBitmaps() {
		// Add order is from oldest to newest. The reverse add order is the
		// output order.
		return new Iterable<StoredEntry>() {
			public Iterator<StoredEntry> iterator() {
				return new Iterator<StoredEntry>() {
					private int index = byAddOrder.size() - 1;

					public boolean hasNext() {
						return index >= 0;
					}

					public StoredEntry next() {
						if (!hasNext())
							throw new NoSuchElementException();
						StoredBitmap item = byAddOrder.get(index);
						int bestXorOffset = 0;
						EWAHCompressedBitmap bestBitmap = item.getBitmap();

						// Attempt to compress the bitmap with an XOR of the
						// previously written entries.
						for (int i = 1; i <= MAX_XOR_OFFSET_SEARCH; i++) {
							int curr = i + index;
							if (curr >= byAddOrder.size())
								break;

							StoredBitmap other = byAddOrder.get(curr);
							EWAHCompressedBitmap bitmap = other.getBitmap()
									.xor(item.getBitmap());

							if (bitmap.sizeInBytes()
									< bestBitmap.sizeInBytes()) {
								bestBitmap = bitmap;
								bestXorOffset = i;
							}
						}
						index--;

						PositionEntry entry = positionEntries.get(item);
						if (entry == null)
							throw new IllegalStateException();
						return new StoredEntry(
								entry.namePosition, bestBitmap, bestXorOffset);
					}

					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}

	/** Data object for the on disk representation of a bitmap entry. */
	public static final class StoredEntry {
		private final long objectId;

		private final EWAHCompressedBitmap bitmap;

		private final int xorOffset;

		private StoredEntry(
				long objectId, EWAHCompressedBitmap bitmap, int xorOffset) {
			this.objectId = objectId;
			this.bitmap = bitmap;
			this.xorOffset = xorOffset;
		}

		/** @return the bitmap */
		public EWAHCompressedBitmap getBitmap() {
			return bitmap;
		}

		/** @return the xorOffset */
		public int getXorOffset() {
			return xorOffset;
		}

		/** @return the ObjectId */
		public long getObjectId() {
			return objectId;
		}
	}

	private static final class PositionEntry extends ObjectIdOwnerMap.Entry {
		private final int namePosition;

		private int offsetPosition;

		private PositionEntry(AnyObjectId objectId, int namePosition) {
			super(objectId);
			this.namePosition = namePosition;
		}
	}
}
