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
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.RoaringBitmap;

/**
 * A compressed bitmap representation of the entire object graph.
 */
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
		RoaringBitmap compressed = packIndex.getBitmap(objectId);
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

	private static final class CompressedBitmapBuilder implements BitmapBuilder {
		private RoaringBitmap bitset;
		private final BitmapIndexImpl bitmapIndex;

		CompressedBitmapBuilder(BitmapIndexImpl bitmapIndex) {
			this.bitset = new RoaringBitmap();
			this.bitmapIndex = bitmapIndex;
		}

		@Override
		public boolean contains(AnyObjectId objectId) {
			int position = bitmapIndex.findPosition(objectId);
			return 0 <= position && bitset.contains(position);
		}

		@Override
		public BitmapBuilder addObject(AnyObjectId objectId, int type) {
			bitset.add(bitmapIndex.findOrInsert(objectId, type));
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
			return new CompressedBitmap(bitset.clone(), bitmapIndex);
		}

		@Override
		public Iterator<BitmapObject> iterator() {
			return build().iterator();
		}

		@Override
		public int cardinality() {
			return bitset.getCardinality();
		}

		@Override
		public boolean removeAllOrNone(PackBitmapIndex index) {
			if (!bitmapIndex.packIndex.equals(index))
				return false;

			RoaringBitmap curr = RoaringBitmap.xor(bitset, ones(bitmapIndex.indexObjectCount));

			PeekableIntIterator ii = curr.getIntIterator();
			if (ii.hasNext() && ii.next() < bitmapIndex.indexObjectCount)
				return false;
			bitset = curr;
			return true;
		}

		@Override
		public BitmapIndexImpl getBitmapIndex() {
			return bitmapIndex;
		}

		@Override
		public RoaringBitmap retrieveCompressed() {
			return build().retrieveCompressed();
		}

		private RoaringBitmap ewahBitmap(Bitmap other) {
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
				return b.bitset;
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
		final RoaringBitmap bitmap;
		final BitmapIndexImpl bitmapIndex;

		/**
		 * Construct compressed bitmap for given bitmap and bitmap index
		 *
		 * @param bitmap
		 *            the bitmap
		 * @param bitmapIndex
		 *            the bitmap index
		 */
		public CompressedBitmap(RoaringBitmap bitmap, BitmapIndexImpl bitmapIndex) {
			this.bitmap = bitmap;
			this.bitmapIndex = bitmapIndex;
		}

		@Override
		public CompressedBitmap or(Bitmap other) {
			return new CompressedBitmap(RoaringBitmap.or(bitmap, ewahBitmap(other)), bitmapIndex);
		}

		@Override
		public CompressedBitmap andNot(Bitmap other) {
			return new CompressedBitmap(RoaringBitmap.andNot(bitmap,ewahBitmap(other)), bitmapIndex);
		}

		@Override
		public CompressedBitmap xor(Bitmap other) {
			return new CompressedBitmap(RoaringBitmap.xor(bitmap,ewahBitmap(other)), bitmapIndex);
		}

		private final PeekableIntIterator ofObjectType(int type) {
			return bitmapIndex.packIndex.ofObjectType(bitmap, type).getIntIterator();
		}

		@Override
		public Iterator<BitmapObject> iterator() {
			final PeekableIntIterator dynamic = RoaringBitmap.andNot(bitmap, ones(bitmapIndex.indexObjectCount))
					.getIntIterator();
			final PeekableIntIterator commits = ofObjectType(Constants.OBJ_COMMIT);
			final PeekableIntIterator trees = ofObjectType(Constants.OBJ_TREE);
			final PeekableIntIterator blobs = ofObjectType(Constants.OBJ_BLOB);
			final PeekableIntIterator tags = ofObjectType(Constants.OBJ_TAG);
			return new Iterator<>() {
				private final BitmapObjectImpl out = new BitmapObjectImpl();
				private int type;
				private PeekableIntIterator cached = dynamic;

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

		@Override
		public RoaringBitmap retrieveCompressed() {
			return bitmap;
		}

		private RoaringBitmap ewahBitmap(Bitmap other) {
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
				return b.bitset.clone();
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

	static final RoaringBitmap ones(int sizeInBits) {
		RoaringBitmap b = new RoaringBitmap();
		b.add((long)0, (long)sizeInBits);
		return b;
	}
}
