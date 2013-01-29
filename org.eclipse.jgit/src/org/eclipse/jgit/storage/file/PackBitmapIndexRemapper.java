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

package org.eclipse.jgit.storage.file;

import javaewah.EWAHCompressedBitmap;
import javaewah.IntIterator;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.storage.file.BasePackBitmapIndex.StoredBitmap;

/**
 * A PackBitmapIndex that remaps the bitmaps in the previous index to the
 * positions in the new pack index. Note, unlike typical PackBitmapIndex
 * implementations this implementation is not thread safe, as it is intended to
 * be used with a PackBitmapIndexBuilder, which is also not thread safe.
 */
public class PackBitmapIndexRemapper extends PackBitmapIndex {

	private final PackBitmapIndex oldPackIndex;
	private final PackBitmapIndex newPackIndex;
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
	public static PackBitmapIndex newPackBitmapIndex(
			BitmapIndex prevBitmapIndex, PackBitmapIndex newIndex) {
		if (!(prevBitmapIndex instanceof BitmapIndexImpl))
			return newIndex;

		return new PackBitmapIndexRemapper(
				((BitmapIndexImpl) prevBitmapIndex).getPackBitmapIndex(),
				newIndex);
	}

	private PackBitmapIndexRemapper(
			PackBitmapIndex oldPackIndex, PackBitmapIndex newPackIndex) {
		this.oldPackIndex = oldPackIndex;
		this.newPackIndex = newPackIndex;
		convertedBitmaps = new ObjectIdOwnerMap<StoredBitmap>();
		inflated = new BitSet(newPackIndex.getObjectCount());

		prevToNewMapping = new int[oldPackIndex.getObjectCount()];
		for (int pos = 0; pos < prevToNewMapping.length; pos++)
			prevToNewMapping[pos] = newPackIndex.findPosition(
					oldPackIndex.getObject(pos));
	}

	@Override
	public int findPosition(AnyObjectId objectId) {
		return newPackIndex.findPosition(objectId);
	}

	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		return newPackIndex.getObject(position);
	}

	@Override
	public int getObjectCount() {
		return newPackIndex.getObjectCount();
	}

	@Override
	public EWAHCompressedBitmap ofObjectType(
			EWAHCompressedBitmap bitmap, int type) {
		return newPackIndex.ofObjectType(bitmap, type);
	}

	@Override
	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
		EWAHCompressedBitmap bitmap = newPackIndex.getBitmap(objectId);
		if (bitmap != null)
			return bitmap;

		StoredBitmap stored = convertedBitmaps.get(objectId);
		if (stored != null)
			return stored.getBitmap();

		EWAHCompressedBitmap oldBitmap = oldPackIndex.getBitmap(objectId);
		if (oldBitmap == null)
			return null;

		inflated.clear();
		for (IntIterator ii = oldBitmap.intIterator(); ii.hasNext();)
			inflated.set(prevToNewMapping[ii.next()]);
		bitmap = inflated.toEWAHCompressedBitmap();
		convertedBitmaps.add(new StoredBitmap(objectId, bitmap, null));
		return bitmap;
	}
}
