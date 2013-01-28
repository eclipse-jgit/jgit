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

package org.eclipse.jgit.storage.file;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.storage.file.BasePackBitmapIndex.StoredBitmap;

/**
 * Support for the pack index vE003 format, which contains experimental support
 * for bitmaps, based on the v2 format.
 *
 * @see PackBitmapIndex
 */
class PackIndexVE003 extends PackIndexV2 {
	static final int OPT_FULL = 1;

	private static final int MAX_XOR_OFFSET = 126;

	private final EWAHCompressedBitmap commits;

	private final EWAHCompressedBitmap trees;

	private final EWAHCompressedBitmap blobs;

	private final EWAHCompressedBitmap tags;

	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	PackIndexVE003(final InputStream fd) throws IOException {
		super(fd);

		// Build up the index by offset
		if (getObjectCount() > Integer.MAX_VALUE)
			throw new IOException(JGitText.get().indexFileIsTooLargeForJgit);

		// Read the options (1 byte)
		int options = fd.read();
		switch (options) {
		case OPT_FULL:
			// Bitmaps are self contained within this file.
			break;
		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().expectedGot, Integer.valueOf(OPT_FULL),
					Integer.valueOf(options)));
		}

		// Read the bitmaps for the Git types
		SimpleDataInput dataInput = new SimpleDataInput(fd);
		this.commits = readBitmap(dataInput);
		this.trees = readBitmap(dataInput);
		this.blobs = readBitmap(dataInput);
		this.tags = readBitmap(dataInput);

		// Read the number of entries (1 int32)
		long numEntries = dataInput.readUnsignedInt();
		if (numEntries > Integer.MAX_VALUE)
			throw new IOException(JGitText.get().indexFileIsTooLargeForJgit);

		// An entry is object id, xor offset, and a length encoded bitmap.
		// the object id is an int32 of the nth position sorted by name.
		// the xor offset is a single byte offset back in the list of entries.
		bitmaps = new ObjectIdOwnerMap<StoredBitmap>();
		StoredBitmap[] recentBitmaps = new StoredBitmap[MAX_XOR_OFFSET];
		for (int i = 0; i < (int) numEntries; i++) {
			int nthObjectId = dataInput.readInt();
			int xorOffset = fd.read();
			EWAHCompressedBitmap bitmap = readBitmap(dataInput);

			if (nthObjectId < 0)
				throw new IOException(MessageFormat.format(
						JGitText.get().invalidId, String.valueOf(nthObjectId)));
			if (xorOffset < 0)
				throw new IOException(MessageFormat.format(
						JGitText.get().invalidId, String.valueOf(xorOffset)));
			if (xorOffset > MAX_XOR_OFFSET)
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedLessThanGot,
						String.valueOf(MAX_XOR_OFFSET),
						String.valueOf(xorOffset)));
			if (xorOffset > i)
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedLessThanGot, String.valueOf(i),
						String.valueOf(xorOffset)));

			ObjectId objectId = getObjectId(nthObjectId);
			StoredBitmap xorBitmap = null;
			if (xorOffset > 0) {
				int index = (i - xorOffset);
				xorBitmap = recentBitmaps[index % recentBitmaps.length];
				if (xorBitmap == null)
					throw new IOException(MessageFormat.format(
							JGitText.get().invalidId,
							String.valueOf(xorOffset)));
			}

			StoredBitmap sb = new StoredBitmap(objectId, bitmap, xorBitmap);
			bitmaps.add(sb);
			recentBitmaps[i % recentBitmaps.length] = sb;
		}
	}

	@Override
	public boolean hasBitmapIndex() {
		return true;
	}

	@Override
	public PackBitmapIndex getBitmapIndex(PackReverseIndex reverseIndex) {
		return new PackBitmapIndexImpl(reverseIndex);
	}

	private static EWAHCompressedBitmap readBitmap(DataInput dataInput)
			throws IOException {
		EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
		bitmap.deserialize(dataInput);
		return bitmap;
	}

	private final class PackBitmapIndexImpl extends BasePackBitmapIndex {
		private final PackReverseIndex reverseIndex;

		private PackBitmapIndexImpl(PackReverseIndex reverseIndex) {
			super(bitmaps);
			this.reverseIndex = reverseIndex;
		}

		@Override
		public int findPosition(AnyObjectId objectId) {
			long offset = findOffset(objectId);
			if (offset == -1)
				return -1;
			return reverseIndex.findPostion(offset);
		}

		@Override
		public ObjectId getObject(int position)
				throws IllegalArgumentException {
			ObjectId objectId = reverseIndex.findObjectByPosition(position);
			if (objectId == null)
				throw new IllegalArgumentException();
			return objectId;
		}

		@Override
		public int getObjectCount() {
			return (int) PackIndexVE003.this.getObjectCount();
		}

		@Override
		public EWAHCompressedBitmap ofObjectType(
				EWAHCompressedBitmap bitmap, int type) {
			switch (type) {
			case Constants.OBJ_BLOB:
				return blobs.and(bitmap);
			case Constants.OBJ_TREE:
				return trees.and(bitmap);
			case Constants.OBJ_COMMIT:
				return commits.and(bitmap);
			case Constants.OBJ_TAG:
				return tags.and(bitmap);
			}
			throw new IllegalArgumentException();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof PackBitmapIndexImpl)
				return getPackIndex()
						== ((PackBitmapIndexImpl) o).getPackIndex();
			return false;
		}

		@Override
		public int hashCode() {
			return getPackIndex().hashCode();
		}

		PackIndex getPackIndex() {
			return PackIndexVE003.this;
		}
	}
}
