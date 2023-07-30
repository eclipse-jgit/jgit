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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import org.roaringbitmap.RoaringBitmap;

/**
 * Base implementation of the PackBitmapIndex.
 */
abstract class BasePackBitmapIndex extends PackBitmapIndex {
	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	BasePackBitmapIndex(ObjectIdOwnerMap<StoredBitmap> bitmaps) {
		this.bitmaps = bitmaps;
	}

	@Override
	public RoaringBitmap getBitmap(AnyObjectId objectId) {
		StoredBitmap sb = bitmaps.get(objectId);
		return sb != null ? sb.getBitmap() : null;
	}

	ObjectIdOwnerMap<StoredBitmap> getBitmaps() {
		return bitmaps;
	}

	/**
	 * Data representation of the bitmap entry restored from a pack index. The
	 * commit of the bitmap is the map key.
	 */
	static final class StoredBitmap extends ObjectIdOwnerMap.Entry {
		private volatile Object bitmapContainer;
		private final int flags;

		StoredBitmap(AnyObjectId objectId, RoaringBitmap bitmap,
				StoredBitmap xorBitmap, int flags) {
			super(objectId);
			this.bitmapContainer = xorBitmap == null
					? bitmap
					: new XorCompressedBitmap(bitmap, xorBitmap);
			this.flags = flags;
		}

		/**
		 * Computes and returns the full bitmap.
		 *
		 * @return the full bitmap
		 */
		RoaringBitmap getBitmap() {
			RoaringBitmap bitmap = getBitmapWithoutCaching();
			// Cache the result.
			bitmapContainer = bitmap;
			return bitmap;
		}

		/**
		 * Compute and return the full bitmap, do NOT cache the expanded bitmap,
		 * which saves memory and should only be used during bitmap creation in
		 * garbage collection.
		 *
		 * @return the full bitmap
		 */
		RoaringBitmap getBitmapWithoutCaching() {
			// Fast path to immediately return the expanded result.
			Object r = bitmapContainer;
			if (r instanceof RoaringBitmap)
				return (RoaringBitmap) r;

			// Expand the bitmap but not cache the result.
			XorCompressedBitmap xb = (XorCompressedBitmap) r;
			RoaringBitmap out = xb.bitmap.clone();
			for (;;) {
				r = xb.xorBitmap.bitmapContainer;
				if (r instanceof EWAHCompressedBitmap) {
					throw new IllegalStateException();
				}
				if (r instanceof RoaringBitmap) {
					out.xor((RoaringBitmap) r);
					out.trim();
					return out;
				}

				xb = (XorCompressedBitmap) r;
				out.xor(xb.bitmap);
			}
		}

		/** @return the flags associated with the bitmap */
		int getFlags() {
			return flags;
		}
	}

	private static final class XorCompressedBitmap {
		final RoaringBitmap bitmap;
		final StoredBitmap xorBitmap;

		XorCompressedBitmap(RoaringBitmap b, StoredBitmap xb) {
			bitmap = b;
			xorBitmap = xb;
		}
	}
}
