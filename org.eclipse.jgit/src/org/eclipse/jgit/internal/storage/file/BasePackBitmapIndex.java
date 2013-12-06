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

import com.googlecode.javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;

/**
 * Base implementation of the PackBitmapIndex.
 */
abstract class BasePackBitmapIndex extends PackBitmapIndex {
	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	BasePackBitmapIndex(ObjectIdOwnerMap<StoredBitmap> bitmaps) {
		this.bitmaps = bitmaps;
	}

	public EWAHCompressedBitmap getBitmap(AnyObjectId objectId) {
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
			// Fast path to immediately return the expanded result.
			Object r = bitmapContainer;
			if (r instanceof EWAHCompressedBitmap)
				return (EWAHCompressedBitmap) r;

			// Expand the bitmap and cache the result.
			XorCompressedBitmap xb = (XorCompressedBitmap) r;
			EWAHCompressedBitmap out = xb.bitmap;
			for (;;) {
				r = xb.xorBitmap.bitmapContainer;
				if (r instanceof EWAHCompressedBitmap) {
					out = out.xor((EWAHCompressedBitmap) r);
					bitmapContainer = out;
					return out;
				}
				xb = (XorCompressedBitmap) r;
				out = out.xor(xb.bitmap);
			}
		}

		/** @return the flags associated with the bitmap */
		int getFlags() {
			return flags;
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
