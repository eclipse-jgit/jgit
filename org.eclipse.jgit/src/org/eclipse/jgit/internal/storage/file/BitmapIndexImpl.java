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

package org.eclipse.jgit.internal.storage.file;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapObject;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.util.BlockList;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah.IntIterator;

/** A compressed bitmap representation of the entire object graph. */
public class BitmapIndexImpl implements BitmapIndex {
	private static final int EXTRA_BITS = 10 * 1024;

	final PackBitmapIndex packIndex;

	final MutableBitmapIndex mutableIndex;

	final int indexObjectCount;

	/**
	 * Creates a BitmapIndex that is back by Compressed bitmaps.
	 *
	 * @param packIndex
	 *            the bitmap index for the pack.
	 */
	public BitmapIndexImpl(PackBitmapIndex packIndex) {
		this.packIndex = packIndex;
		mutableIndex = new MutableBitmapIndex();
		indexObjectCount = packIndex.getObjectCount();
	}

	PackBitmapIndex getPackBitmapIndex() {
		return packIndex;
	}

	@Override
	public CompressedBitmap getBitmap(AnyObjectId objectId) {
		EWAHCompressedBitmap compressed = packIndex.getBitmap(objectId);
		if (compressed == null)
			return null;
		return new CompressedBitmap(compressed, this);
	}

	@Override
	public CompressedBitmapBuilder newBitmapBuilder() {
		return new CompressedBitmapBuilder(this);
	}

	int findPosition(AnyObjectId objectId) {
		int position = packIndex.findPosition(objectId);
		if (position < 0) {
			position = mutableIndex.findPosition(objectId);
			if (position >= 0)
				position += indexObjectCount;
		}
		return position;
	}

	int findOrInsert(AnyObjectId objectId, int type) {
		int position = findPosition(objectId);
		if (position < 0) {
			position = mutableIndex.findOrInsert(objectId, type);
			position += indexObjectCount;
		}
		return position;
	}

	private static final class ComboBitset {
		private InflatingBitSet inflatingBitmap;

		private BitSet toAdd;

		private BitSet toRemove;

		ComboBitset() {
			this(new EWAHCompressedBitmap());
		}

		ComboBitset(EWAHCompressedBitmap bitmap) {
			this.inflatingBitmap = new InflatingBitSet(bitmap);
		}

		EWAHCompressedBitmap combine() {
			EWAHCompressedBitmap toAddCompressed = null;
			if (toAdd != null) {
				toAddCompressed = toAdd.toEWAHCompressedBitmap();
				toAdd = null;
			}

			EWAHCompressedBitmap toRemoveCompressed = null;
			if (toRemove != null) {
				toRemoveCompressed = toRemove.toEWAHCompressedBitmap();
				toRemove = null;
			}

			if (toAddCompressed != null)
				or(toAddCompressed);
			if (toRemoveCompressed != null)
				andNot(toRemoveCompressed);
			return inflatingBitmap.getBitmap();
		}

		void or(EWAHCompressedBitmap inbits) {
			if (toRemove != null)
				combine();
			inflatingBitmap = inflatingBitmap.or(inbits);
		}

		void andNot(EWAHCompressedBitmap inbits) {
			if (toAdd != null || toRemove != null)
				combine();
			inflatingBitmap = inflatingBitmap.andNot(inbits);
		}

		void xor(EWAHCompressedBitmap inbits) {
			if (toAdd != null || toRemove != null)
				combine();
			inflatingBitmap = inflatingBitmap.xor(inbits);
		}

		boolean contains(int position) {
			if (toRemove != null && toRemove.get(position))
				return false;
			if (toAdd != null && toAdd.get(position))
				return true;
			return inflatingBitmap.contains(position);
		}

		void remove(int position) {
			if (toAdd != null)
				toAdd.clear(position);

			if (inflatingBitmap.maybeContains(position)) {
				if (toRemove == null)
					toRemove = new BitSet(position + EXTRA_BITS);
				toRemove.set(position);
			}
		}

		void set(int position) {
			if (toRemove != null)
				toRemove.clear(position);

			if (toAdd == null)
				toAdd = new BitSet(position + EXTRA_BITS);
			toAdd.set(position);
		}
	}

	private static final class CompressedBitmapBuilder implements BitmapBuilder {
		private ComboBitset bitset;
		private final BitmapIndexImpl bitmapIndex;

		CompressedBitmapBuilder(BitmapIndexImpl bitmapIndex) {
			this.bitset = new ComboBitset();
			this.bitmapIndex = bitmapIndex;
		}

		@Override
		public boolean add(AnyObjectId objectId, int type) {
			int position = bitmapIndex.findOrInsert(objectId, type);
			if (bitset.contains(position))
				return false;

			Bitmap entry = bitmapIndex.getBitmap(objectId);
			if (entry != null) {
				or(entry);
				return false;
			}

			bitset.set(position);
			return true;
		}

		@Override
		public boolean contains(AnyObjectId objectId) {
			int position = bitmapIndex.findPosition(objectId);
			return 0 <= position && bitset.contains(position);
		}

		@Override
		public BitmapBuilder addObject(AnyObjectId objectId, int type) {
			bitset.set(bitmapIndex.findOrInsert(objectId, type));
			return this;
		}

		@Override
		public void remove(AnyObjectId objectId) {
			int position = bitmapIndex.findPosition(objectId);
			if (0 <= position)
				bitset.remove(position);
		}

		@Override
		public CompressedBitmapBuilder or(Bitmap other) {
			bitset.or(ewahBitmap(other));
			return this;
		}

		@Override
		public CompressedBitmapBuilder andNot(Bitmap other) {
			bitset.andNot(ewahBitmap(other));
			return this;
		}

		@Override
		public CompressedBitmapBuilder xor(Bitmap other) {
			bitset.xor(ewahBitmap(other));
			return this;
		}

		/** @return the fully built immutable bitmap */
		@Override
		public CompressedBitmap build() {
			return new CompressedBitmap(bitset.combine(), bitmapIndex);
		}

		@Override
		public Iterator<BitmapObject> iterator() {
			return build().iterator();
		}

		@Override
		public int cardinality() {
			return bitset.combine().cardinality();
		}

		@Override
		public boolean removeAllOrNone(PackBitmapIndex index) {
			if (!bitmapIndex.packIndex.equals(index))
				return false;

			EWAHCompressedBitmap curr = bitset.combine()
					.xor(ones(bitmapIndex.indexObjectCount));

			IntIterator ii = curr.intIterator();
			if (ii.hasNext() && ii.next() < bitmapIndex.indexObjectCount)
				return false;
			bitset = new ComboBitset(curr);
			return true;
		}

		@Override
		public BitmapIndexImpl getBitmapIndex() {
			return bitmapIndex;
		}

		private EWAHCompressedBitmap ewahBitmap(Bitmap other) {
			if (other instanceof CompressedBitmap) {
				CompressedBitmap b = (CompressedBitmap) other;
				if (b.bitmapIndex != bitmapIndex) {
					throw new IllegalArgumentException();
				}
				return b.bitmap;
			}
			if (other instanceof CompressedBitmapBuilder) {
				CompressedBitmapBuilder b = (CompressedBitmapBuilder) other;
				if (b.bitmapIndex != bitmapIndex) {
					throw new IllegalArgumentException();
				}
				return b.bitset.combine();
			}
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Wrapper for a {@link EWAHCompressedBitmap} and {@link PackBitmapIndex}.
	 * <p>
	 * For a EWAHCompressedBitmap {@code bitmap} representing a vector of
	 * bits, {@code new CompressedBitmap(bitmap, bitmapIndex)} represents the
	 * objects at those positions in {@code bitmapIndex.packIndex}.
	 */
	public static final class CompressedBitmap implements Bitmap {
		final EWAHCompressedBitmap bitmap;
		final BitmapIndexImpl bitmapIndex;

		/**
		 * Construct compressed bitmap for given bitmap and bitmap index
		 *
		 * @param bitmap
		 * @param bitmapIndex
		 */
		public CompressedBitmap(EWAHCompressedBitmap bitmap, BitmapIndexImpl bitmapIndex) {
			this.bitmap = bitmap;
			this.bitmapIndex = bitmapIndex;
		}

		@Override
		public CompressedBitmap or(Bitmap other) {
			return new CompressedBitmap(bitmap.or(ewahBitmap(other)), bitmapIndex);
		}

		@Override
		public CompressedBitmap andNot(Bitmap other) {
			return new CompressedBitmap(bitmap.andNot(ewahBitmap(other)), bitmapIndex);
		}

		@Override
		public CompressedBitmap xor(Bitmap other) {
			return new CompressedBitmap(bitmap.xor(ewahBitmap(other)), bitmapIndex);
		}

		private final IntIterator ofObjectType(int type) {
			return bitmapIndex.packIndex.ofObjectType(bitmap, type).intIterator();
		}

		@Override
		public Iterator<BitmapObject> iterator() {
			final IntIterator dynamic = bitmap.andNot(ones(bitmapIndex.indexObjectCount))
					.intIterator();
			final IntIterator commits = ofObjectType(Constants.OBJ_COMMIT);
			final IntIterator trees = ofObjectType(Constants.OBJ_TREE);
			final IntIterator blobs = ofObjectType(Constants.OBJ_BLOB);
			final IntIterator tags = ofObjectType(Constants.OBJ_TAG);
			return new Iterator<BitmapObject>() {
				private final BitmapObjectImpl out = new BitmapObjectImpl();
				private int type;
				private IntIterator cached = dynamic;

				@Override
				public boolean hasNext() {
					if (!cached.hasNext()) {
						if (commits.hasNext()) {
							type = Constants.OBJ_COMMIT;
							cached = commits;
						} else if (trees.hasNext()) {
							type = Constants.OBJ_TREE;
							cached = trees;
						} else if (blobs.hasNext()) {
							type = Constants.OBJ_BLOB;
							cached = blobs;
						} else if (tags.hasNext()) {
							type = Constants.OBJ_TAG;
							cached = tags;
						} else {
							return false;
						}
					}
					return true;
				}

				@Override
				public BitmapObject next() {
					if (!hasNext())
						throw new NoSuchElementException();

					int position = cached.next();
					if (position < bitmapIndex.indexObjectCount) {
						out.type = type;
						out.objectId = bitmapIndex.packIndex.getObject(position);
					} else {
						position -= bitmapIndex.indexObjectCount;
						MutableEntry entry = bitmapIndex.mutableIndex.getObject(position);
						out.type = entry.type;
						out.objectId = entry;
					}
					return out;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}

		EWAHCompressedBitmap getEwahCompressedBitmap() {
			return bitmap;
		}

		private EWAHCompressedBitmap ewahBitmap(Bitmap other) {
			if (other instanceof CompressedBitmap) {
				CompressedBitmap b = (CompressedBitmap) other;
				if (b.bitmapIndex != bitmapIndex) {
					throw new IllegalArgumentException();
				}
				return b.bitmap;
			}
			if (other instanceof CompressedBitmapBuilder) {
				CompressedBitmapBuilder b = (CompressedBitmapBuilder) other;
				if (b.bitmapIndex != bitmapIndex) {
					throw new IllegalArgumentException();
				}
				return b.bitset.combine();
			}
			throw new IllegalArgumentException();
		}
	}

	private static final class MutableBitmapIndex {
		private final ObjectIdOwnerMap<MutableEntry>
				revMap = new ObjectIdOwnerMap<>();

		private final BlockList<MutableEntry>
				revList = new BlockList<>();

		int findPosition(AnyObjectId objectId) {
			MutableEntry entry = revMap.get(objectId);
			if (entry == null)
				return -1;
			return entry.position;
		}

		MutableEntry getObject(int position) {
			try {
				MutableEntry entry = revList.get(position);
				if (entry == null)
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().objectNotFound,
							String.valueOf(position)));
				return entry;
			} catch (IndexOutOfBoundsException ex) {
				throw new IllegalArgumentException(ex);
			}
		}

		int findOrInsert(AnyObjectId objectId, int type) {
			MutableEntry entry = new MutableEntry(
					objectId, type, revList.size());
			revList.add(entry);
			revMap.add(entry);
			return entry.position;
		}
	}

	private static final class MutableEntry extends ObjectIdOwnerMap.Entry {
		final int type;

		final int position;

		MutableEntry(AnyObjectId objectId, int type, int position) {
			super(objectId);
			this.type = type;
			this.position = position;
		}
	}

	private static final class BitmapObjectImpl extends BitmapObject {
		private ObjectId objectId;

		private int type;

		@Override
		public ObjectId getObjectId() {
			return objectId;
		}

		@Override
		public int getType() {
			return type;
		}
	}

	static final EWAHCompressedBitmap ones(int sizeInBits) {
		EWAHCompressedBitmap mask = new EWAHCompressedBitmap();
		mask.addStreamOfEmptyWords(
				true, sizeInBits / EWAHCompressedBitmap.WORD_IN_BITS);
		int remaining = sizeInBits % EWAHCompressedBitmap.WORD_IN_BITS;
		if (remaining > 0)
			mask.addWord((1L << remaining) - 1, remaining);
		return mask;
	}
}
