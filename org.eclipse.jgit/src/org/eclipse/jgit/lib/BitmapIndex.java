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

package org.eclipse.jgit.lib;

import java.util.Iterator;

import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;

/**
 * A compressed bitmap representation of the entire object graph.
 *
 * @since 3.0
 */
public interface BitmapIndex {
	/**
	 * Get the bitmap for the id. The returned bitmap is immutable and the
	 * bitwise operations return the result of the operation in a new Bitmap.
	 *
	 * @param objectId
	 *            the object ID
	 * @return the Bitmap for the objectId or null, if one does not exist.
	 */
	Bitmap getBitmap(AnyObjectId objectId);

	/**
	 * Create a new {@code BitmapBuilder} based on the values in the index.
	 *
	 * @return a new {@code BitmapBuilder} based on the values in the index.
	 */
	BitmapBuilder newBitmapBuilder();

	/**
	 * A bitmap representation of ObjectIds that can be iterated to return the
	 * underlying {@code ObjectId}s or operated on with other {@code Bitmap}s.
	 */
	public interface Bitmap extends Iterable<BitmapObject> {
		/**
		 * Bitwise-OR the current bitmap with the value from the other
		 * bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return a bitmap that is the bitwise-OR.
		 */
		Bitmap or(Bitmap other);

		/**
		 * Bitwise-AND-NOT the current bitmap with the value from the other
		 * bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return a bitmap that is the bitwise-AND-NOT.
		 */
		Bitmap andNot(Bitmap other);

		/**
		 * Bitwise-XOR the current bitmap with the value from the other
		 * bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return a bitmap that is the bitwise-XOR.
		 */
		Bitmap xor(Bitmap other);

		/**
		 * Returns an iterator over a set of elements of type BitmapObject. The
		 * BitmapObject instance is reused across calls to
		 * {@link Iterator#next()} for performance reasons.
		 *
		 * @return an Iterator.
		 */
		@Override
		Iterator<BitmapObject> iterator();
	}

	/**
	 * A builder for a bitmap. The bitwise operations update the builder and
	 * return a reference to the current builder.
	 */
	public interface BitmapBuilder extends Bitmap {
		/**
		 * Adds the id and the existing bitmap for the id, if one exists, to the
		 * bitmap.
		 *
		 * @param objectId
		 *            the object ID
		 * @param type
		 *            the Git object type. See {@link Constants}.
		 * @return true if the value was not contained or able to be loaded.
		 * @deprecated use {@link #or} or {@link #addObject} instead.
		 */
		@Deprecated
		boolean add(AnyObjectId objectId, int type);

		/**
		 * Whether the bitmap has the id set.
		 *
		 * @param objectId
		 *            the object ID
		 * @return whether the bitmap currently contains the object ID
		 */
		boolean contains(AnyObjectId objectId);

		/**
		 * Adds the id to the bitmap.
		 *
		 * @param objectId
		 *            the object ID
		 * @param type
		 *            the Git object type. See {@link Constants}.
		 * @return the current builder.
		 * @since 4.2
		 */
		BitmapBuilder addObject(AnyObjectId objectId, int type);

		/**
		 * Remove the id from the bitmap.
		 *
		 * @param objectId
		 *            the object ID
		 */
		void remove(AnyObjectId objectId);

		/**
		 * Bitwise-OR the current bitmap with the value from the other bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return the current builder.
		 */
		@Override
		BitmapBuilder or(Bitmap other);

		/**
		 * Bitwise-AND-NOT the current bitmap with the value from the other
		 * bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return the current builder.
		 */
		@Override
		BitmapBuilder andNot(Bitmap other);

		/**
		 * Bitwise-XOR the current bitmap with the value from the other bitmap.
		 *
		 * @param other
		 *            the other bitmap
		 * @return the current builder.
		 */
		@Override
		BitmapBuilder xor(Bitmap other);

		/** @return the fully built immutable bitmap */
		Bitmap build();

		/**
		 * Determines if the entire bitmap index is contained in the bitmap. If
		 * it is, the matching bits are removed from the bitmap and true is
		 * returned. If the bitmap index is null, false is returned.
		 *
		 * @param bitmapIndex
		 *            the bitmap index to check if it is completely contained
		 *            inside of the current bitmap.
		 * @return {@code true} if the bitmap index was a complete match.
		 */
		boolean removeAllOrNone(PackBitmapIndex bitmapIndex);

		/** @return the number of elements in the bitmap. */
		int cardinality();

		/**
		 * Get the BitmapIndex for this BitmapBuilder.
		 *
		 * @return the BitmapIndex for this BitmapBuilder
		 *
		 * @since 4.2
		 */
		BitmapIndex getBitmapIndex();
	}
}
