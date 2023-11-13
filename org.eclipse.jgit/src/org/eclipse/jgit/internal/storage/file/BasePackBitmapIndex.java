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
abstract class BasePackBitmapIndex extends PackBitmapIndex {
	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	BasePackBitmapIndex(ObjectIdOwnerMap<StoredBitmap> bitmaps) {
		this.bitmaps = bitmaps;
	}

	@Override
	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
		StoredBitmap sb = bitmaps.get(objectId);
		if (sb != null) {
			stats.bitmapLookupHit += 1;
			int xorDepth = sb.getChainDepth();
			if (xorDepth > 0) {
				stats.xoredBitmap += xorDepth;
			} else {
				stats.readyBitmap += 1;
			}
			long sizeBefore = sb.getCurrentBitmapSize();
			EWAHCompressedBitmap bitmap = sb.getBitmap();
			stats.xorSizeDiff += bitmap.sizeInBytes() - sizeBefore;
			return bitmap;
		}
		return null;
	}

	protected ObjectIdOwnerMap<StoredBitmap> getBitmaps() {
		return bitmaps;
	}

	@Override
	public long getSize() {
		long size = 0;
		for (StoredBitmap sb : getBitmaps()) {
			size += sb.getCurrentBitmapSize();
		}
		return size;
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
		 * Number of bitmaps in the xor chain
		 *
		 * Including this one, so a base bitmap has a chain depth of 0, and xor
		 * over the base has depth 1 and so on.
		 *
		 * @return number of xor bitmaps to the base (including this one).
		 */
		int getChainDepth() {
			int depth = 0;
			Object r = bitmapContainer;
			if (r instanceof EWAHCompressedBitmap) {
				return depth;
			}
			for (;;) {
				if (r instanceof  EWAHCompressedBitmap) {
					return depth;
				}
				r = ((XorCompressedBitmap)r).xorBitmap.bitmapContainer;
				depth++;
			}
		}

		/**
		 * Size in bytes of this bitmap in its current representation
		 *
		 * If this is a XOR'ed bitmap, size is different before/after
		 * {@link #getBitmap()}. Before is the bitmap size of the base plus all
		 * xor bitmaps in the chain. Afterwards is just a single bitmap size.
		 *
		 * @return size in bytes of the bitmap in its current representation
		 */
		long getCurrentBitmapSize() {
			Object r = bitmapContainer;
			long bitmapSizeInBytes = 0;
			for (;;) {
				if (r instanceof EWAHCompressedBitmap) {
					return bitmapSizeInBytes + ((EWAHCompressedBitmap) r).sizeInBytes();
				}
				XorCompressedBitmap xor = ((XorCompressedBitmap) r);
				bitmapSizeInBytes += xor.bitmap.sizeInBytes();
				r = xor.xorBitmap.bitmapContainer;
			}
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
