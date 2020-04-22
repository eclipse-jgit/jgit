/*
 * Copyright (c) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.pack;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A commit object for which a bitmap index should be built.
 */
public final class BitmapCommit extends ObjectId {

	private final boolean reuseWalker;

	private final int flags;

	private final boolean addToIndex;

	BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags) {
		super(objectId);
		this.reuseWalker = reuseWalker;
		this.flags = flags;
		this.addToIndex = false;
	}

	BitmapCommit(AnyObjectId objectId, boolean reuseWalker, int flags,
				 boolean addToIndex) {
		super(objectId);
		this.reuseWalker = reuseWalker;
		this.flags = flags;
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
	 * Get a builder of BitmapCommit whose fields are copied from
	 * {@code commit}.
	 *
	 * @param commit
	 *            the bitmap commit the builder is copying from
	 * @return a BitmapCommit build with fields copied from an existing bitmap
	 *         commit.
	 */
	public static Builder copyFrom(BitmapCommit commit) {
		return new Builder().setId(commit)
				.setReuseWalker(commit.isReuseWalker())
				.setFlags(commit.getFlags())
				.setAddToIndex(commit.isAddToIndex());
	}

	/**
	 * Builder of BitmapCommit.
	 */
	public static class Builder {
		private AnyObjectId objectId;

		private boolean reuseWalker;

		private int flags;

		private boolean addToIndex;

		// Prevent default constructor.
		private Builder() {
		}

		/**
		 * Set objectId of the builder.
		 *
		 * @param objectId
		 *            the object id of the BitmapCommit
		 * @return the builder itself
		 */
		public Builder setId(AnyObjectId objectId) {
			this.objectId = objectId;
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
		 * Set whether whether the bitmap of the BitmapCommit should be added to
		 * PackBitmapIndexBuilder when building bitmap index file.
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
		 * @return the new BitmapCommit.
		 */
		public BitmapCommit build() {
			return new BitmapCommit(objectId, reuseWalker, flags,
					addToIndex);
		}
	}
}