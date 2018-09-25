/*
 * Copyright (C) 2013, Google Inc.
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

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.internal.storage.file.BasePackBitmapIndex.StoredBitmap;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;

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
	private final ObjectIdOwnerMap<StoredBitmap> convertedBitmaps;
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
		this.convertedBitmaps = null;
		this.inflated = null;
		this.prevToNewMapping = null;
	}

	private PackBitmapIndexRemapper(
			BasePackBitmapIndex oldPackIndex, PackBitmapIndex newPackIndex) {
		this.oldPackIndex = oldPackIndex;
		this.newPackIndex = newPackIndex;
		convertedBitmaps = new ObjectIdOwnerMap<>();
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

		StoredBitmap stored = convertedBitmaps.get(objectId);
		if (stored != null)
			return stored.getBitmap();

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
		convertedBitmaps.add(
				new StoredBitmap(objectId, bitmap, null, oldBitmap.getFlags()));
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
