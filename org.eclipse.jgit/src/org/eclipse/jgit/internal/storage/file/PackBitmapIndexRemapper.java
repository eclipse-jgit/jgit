/*
 * Copyright (C) 2013, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah.IntIterator;

/**
 * A PackBitmapIndex that remaps the bitmaps in the previous index to the
 * positions in the new pack index. Note, unlike typical PackBitmapIndex
 * implementations this implementation is not thread safe, as it is intended to
 * be used with a PackBitmapIndexBuilder, which is also not thread safe.
 */
public class PackBitmapIndexRemapper extends PackBitmapIndex
		implements Iterable<PackBitmapIndexRemapper.Entry> {

	private final BasePackBitmapIndex oldPackIndex;
	final PackBitmapIndex newPackIndex;
	private final BitSet inflated;
	private final int[] prevToNewMapping;

	/**
	 * A PackBitmapIndex that maps the positions in the prevBitmapIndex to the
	 * ones in the newIndex.
	 *
	 * @param prevBitmapIndex
	 *            the bitmap index with the old mapping.
	 * @param newIndex
	 *            the bitmap index with the new mapping.
	 * @return a bitmap index that attempts to do the mapping between the two.
	 */
	public static PackBitmapIndexRemapper newPackBitmapIndex(
			BitmapIndex prevBitmapIndex, PackBitmapIndex newIndex) {
		if (!(prevBitmapIndex instanceof BitmapIndexImpl))
			return new PackBitmapIndexRemapper(newIndex);

		PackBitmapIndex prevIndex = ((BitmapIndexImpl) prevBitmapIndex)
				.getPackBitmapIndex();
		if (!(prevIndex instanceof BasePackBitmapIndex))
			return new PackBitmapIndexRemapper(newIndex);

		return new PackBitmapIndexRemapper(
				(BasePackBitmapIndex) prevIndex, newIndex);
	}

	private PackBitmapIndexRemapper(PackBitmapIndex newPackIndex) {
		this.oldPackIndex = null;
		this.newPackIndex = newPackIndex;
		this.inflated = null;
		this.prevToNewMapping = null;
	}

	private PackBitmapIndexRemapper(
			BasePackBitmapIndex oldPackIndex, PackBitmapIndex newPackIndex) {
		this.oldPackIndex = oldPackIndex;
		this.newPackIndex = newPackIndex;
		inflated = new BitSet(newPackIndex.getObjectCount());

		prevToNewMapping = new int[oldPackIndex.getObjectCount()];
		for (int pos = 0; pos < prevToNewMapping.length; pos++)
			prevToNewMapping[pos] = newPackIndex.findPosition(
					oldPackIndex.getObject(pos));
	}

	/** {@inheritDoc} */
	@Override
	public int findPosition(AnyObjectId objectId) {
		return newPackIndex.findPosition(objectId);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		return newPackIndex.getObject(position);
	}

	/** {@inheritDoc} */
	@Override
	public int getObjectCount() {
		return newPackIndex.getObjectCount();
	}

	/** {@inheritDoc} */
	@Override
	public EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type) {
		return newPackIndex.ofObjectType(bitmap, type);
	}

	/** {@inheritDoc} */
	@Override
	public Iterator<Entry> iterator() {
		if (oldPackIndex == null)
			return Collections.<Entry> emptyList().iterator();

		final Iterator<StoredBitmap> it = oldPackIndex.getBitmaps().iterator();
		return new Iterator<Entry>() {
			private Entry entry;

			@Override
			public boolean hasNext() {
				while (entry == null && it.hasNext()) {
					StoredBitmap sb = it.next();
					if (newPackIndex.findPosition(sb) != -1)
						entry = new Entry(sb, sb.getFlags());
				}
				return entry != null;
			}

			@Override
			public Entry next() {
				if (!hasNext())
					throw new NoSuchElementException();

				Entry res = entry;
				entry = null;
				return res;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/** {@inheritDoc} */
	@Override
	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
		EWAHCompressedBitmap bitmap = newPackIndex.getBitmap(objectId);
		if (bitmap != null || oldPackIndex == null)
			return bitmap;

		StoredBitmap oldBitmap = oldPackIndex.getBitmaps().get(objectId);
		if (oldBitmap == null)
			return null;

		if (newPackIndex.findPosition(objectId) == -1)
			return null;

		inflated.clear();
		for (IntIterator i = oldBitmap.getBitmap().intIterator(); i.hasNext();)
			inflated.set(prevToNewMapping[i.next()]);
		bitmap = inflated.toEWAHCompressedBitmap();
		bitmap.trim();
		return bitmap;
	}

	/** An entry in the old PackBitmapIndex. */
	public static final class Entry extends ObjectId {
		private final int flags;

		Entry(AnyObjectId src, int flags) {
			super(src);
			this.flags = flags;
		}

		/** @return the flags associated with the bitmap. */
		public int getFlags() {
			return flags;
		}
	}

	/** {@inheritDoc} */
	@Override
	public int getBitmapCount() {
		// The count is only useful for the end index, not the remapper.
		return 0;
	}
}
