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

/**
 * Base implementation of the PackBitmapIndex.
 */
abstract class BasePackBitmapIndex implements PackBitmapIndex {
	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	BasePackBitmapIndex(ObjectIdOwnerMap<StoredBitmap> bitmaps) {
		this.bitmaps = bitmaps;
	}

	@Override
	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
		StoredBitmap sb = bitmaps.get(objectId);
		return sb != null ? sb.getBitmap() : null;
	}

	ObjectIdOwnerMap<StoredBitmap> getBitmaps() {
		return bitmaps;
	}

	@Override
	public int getBaseBitmapCount() {
		int bases = 0;
		for (StoredBitmap sb : getBitmaps()) {
			if (sb.isBase()) {
				bases += 1;
			}
		}
		return bases;
	}

	@Override
	public long getBaseBitmapSizeInBytes() {
		long baseSize = 0;
		for (StoredBitmap sb : getBitmaps()) {
			if (sb.isBase()) {
				baseSize += sb.getCurrentSizeInBytes();
			}
		}
		return baseSize;
	}

	@Override
	public int getXorBitmapCount() {
		int xored = 0;
		for (StoredBitmap sb : getBitmaps()) {
			if (!sb.isBase()) {
				xored += 1;
			}
		}
		return xored;
	}

	@Override
	public long getXorBitmapSizeInBytes() {
		long xorSize = 0;
		for (StoredBitmap sb : getBitmaps()) {
			if (!sb.isBase()) {
				xorSize += sb.getCurrentSizeInBytes();
			}
		}
		return xorSize;
	}

	/**
	 * Data representation of the bitmap entry restored from a pack index. The
	 * commit of the bitmap is the map key.
	 */
	static final class StoredBitmap extends ObjectIdOwnerMap.Entry {
		private volatile Object bitmapContainer;
		private final int flags;

		StoredBitmap(AnyObjectId objectId, EWAHCompressedBitmap bitmap,
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
		EWAHCompressedBitmap getBitmap() {
			EWAHCompressedBitmap bitmap = getBitmapWithoutCaching();
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
		EWAHCompressedBitmap getBitmapWithoutCaching() {
			// Fast path to immediately return the expanded result.
			Object r = bitmapContainer;
			if (r instanceof EWAHCompressedBitmap) {
				return (EWAHCompressedBitmap) r;
			}

			// Expand the bitmap but not cache the result.
			XorCompressedBitmap xb = (XorCompressedBitmap) r;
			EWAHCompressedBitmap out = xb.bitmap;
			for (;;) {
				r = xb.xorBitmap.bitmapContainer;
				if (r instanceof EWAHCompressedBitmap) {
					out = out.xor((EWAHCompressedBitmap) r);
					out.trim();
					return out;
				}
				xb = (XorCompressedBitmap) r;
				out = out.xor(xb.bitmap);
			}
		}

		/**
		 * Get flags
		 *
		 * @return the flags associated with the bitmap
		 */
		int getFlags() {
			return flags;
		}

		/**
		 * This bitmap is (currently) a base or a XOR mask
		 *
		 * @return true if this bitmap is a base (a ready map).
		 */
		boolean isBase() {
			return bitmapContainer instanceof EWAHCompressedBitmap;
		}

		/**
		 * Size in bytes of this bitmap in its current representation
		 *
		 * If this is a XOR'ed bitmap, size is different before/after
		 * {@link #getBitmap()}. Before is the byte size of the xor mask,
		 * afterwards is the size of the "ready" bitmap
		 *
		 * @return size in bytes of the bitmap in its current representation
		 */
		long getCurrentSizeInBytes() {
			Object r = bitmapContainer;
			if (r instanceof EWAHCompressedBitmap) {
				return ((EWAHCompressedBitmap) r).sizeInBytes();
			}
			XorCompressedBitmap xor = ((XorCompressedBitmap) r);
			return xor.bitmap.sizeInBytes();
		}
	}

	private static final class XorCompressedBitmap {
		final EWAHCompressedBitmap bitmap;

		final StoredBitmap xorBitmap;

		XorCompressedBitmap(EWAHCompressedBitmap b, StoredBitmap xb) {
			bitmap = b;
			xorBitmap = xb;
		}
	}
}
