/*
 * Copyright (C) 2019, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A commit object for which a bitmap index should be built.
 */
public final class BitmapCommit extends ObjectId {
	private final boolean reuseWalker;

	private final int flags;

	private final Bitmap bitmap;

	private final boolean addToIndex;

	BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags) {
		super(objectId);
		this.reuseWalker = reuseWalker;
		this.flags = flags;
		this.bitmap = null;
		this.addToIndex = false;
	}

	private BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags,
			Bitmap bitmap, boolean addToIndex) {
		super(objectId);
		this.reuseWalker = reuseWalker;
		this.flags = flags;
		this.bitmap = bitmap;
		this.addToIndex = addToIndex;
	}

	boolean isReuseWalker() {
		return reuseWalker;
	}

	int getFlags() {
		return flags;
	}

	/**
	 * Whether corresponding bitmap should be added to PackBitmapIndexBuilder.
	 *
	 * @return true if the corresponding bitmap should be added to
	 *         PackBitmapIndexBuilder.
	 */
	public boolean isAddToIndex() {
		return addToIndex;
	}

	/**
	 * Get a builder of BitmapCommit whose object id is {@code objId}.
	 *
	 * @param objId
	 *            the object id of the BitmapCommit
	 * @return a BitmapCommit builder with object id set.
	 */
	public static Builder newBuilder(AnyObjectId objId) {
		return new Builder().setId(objId);
	}

	/**
	 * Get a builder of BitmapCommit whose fields are copied from {@code cmit}.
	 *
	 * @param cmit
	 *            the bitmap commit the builder is copying from
	 * @return a BitmapCommit build with fields copied from an existing bitmap
	 *         commit.
	 */
	public static Builder copyFrom(BitmapCommit cmit) {
		return new Builder().setId(cmit).setReuseWalker(cmit.isReuseWalker())
				.setFlags(cmit.getFlags()).setBitmap(cmit.getBitmap())
				.setAddToIndex(cmit.isAddToIndex());
	}

	/**
	 * Get the bitmap associated with the commit.
	 *
	 * @return the bitmap of the commit.
	 */
	public Bitmap getBitmap() {
		return bitmap;
	}

	/**
	 * Builder of BitmapCommit.
	 *
	 */
	public static class Builder {
		private AnyObjectId objId;

		private boolean reuseWalker;

		private int flags;

		private Bitmap bitmap;

		private boolean addToIndex;

		private Builder() {
		}

		/**
		 * Set objectId of the builder.
		 *
		 * @param objId
		 *            the object id of the BitmapCommit
		 * @return the builder itself
		 */
		public Builder setId(AnyObjectId objId) {
			this.objId = objId;
			return this;
		}

		/**
		 * Set reuseWalker of the builder.
		 *
		 * @param reuseWalker
		 *            whether the BitmapCommit should reuse bitmap walker when
		 *            walking objects
		 * @return the builder itself
		 */
		public Builder setReuseWalker(boolean reuseWalker) {
			this.reuseWalker = reuseWalker;
			return this;
		}

		/**
		 * Set flags of the builder.
		 *
		 * @param flags
		 *            the flags of the BitmapCommit
		 * @return the builder itself
		 */
		public Builder setFlags(int flags) {
			this.flags = flags;
			return this;
		}

		/**
		 * Set bitmap of the builder.
		 *
		 * @param bitmap
		 *            the bitmap of the BitmapCommit
		 * @return the builder itself
		 */
		public Builder setBitmap(Bitmap bitmap) {
			this.bitmap = bitmap;
			return this;
		}

		/**
		 * Set addToIndex of the builder.
		 *
		 * @param addToIndex
		 *            whether the bitmap of the BitmapCommit should be added to
		 *            PackBitmapIndexBuilder when building bitmap index file
		 * @return the builder itself
		 */
		public Builder setAddToIndex(boolean addToIndex) {
			this.addToIndex = addToIndex;
			return this;
		}

		/**
		 * Builds BitmapCommit from the builder.
		 *
		 * @return the BitmapCommit built.
		 */
		public BitmapCommit build() {
			return new BitmapCommit(objId, reuseWalker, flags, bitmap,
					addToIndex);
		}
	}
}
