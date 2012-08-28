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
import java.util.Arrays;

import javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.storage.file.BasePackBitmapIndex.StoredBitmap;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;

/**
 * Support for the pack bitmpa index v1 format, which contains experimental
 * support for bitmaps.
 *
 * @see PackBitmapIndex
 */
class PackBitmapIndexV1 extends BasePackBitmapIndex {
	static final byte[] MAGIC = { 'B', 'I', 'T', 'M' };
	static final int OPT_FULL = 1;

	private static final int MAX_XOR_OFFSET = 126;

	private final PackIndex packIndex;
	private final PackReverseIndex reverseIndex;
	private final EWAHCompressedBitmap commits;
	private final EWAHCompressedBitmap trees;
	private final EWAHCompressedBitmap blobs;
	private final EWAHCompressedBitmap tags;

	private final ObjectIdOwnerMap<StoredBitmap> bitmaps;

	PackBitmapIndexV1(final InputStream fd, PackIndex packIndex,
			PackReverseIndex reverseIndex) throws IOException {
		super(new ObjectIdOwnerMap<StoredBitmap>());
		this.packIndex = packIndex;
		this.reverseIndex = reverseIndex;
		this.bitmaps = getBitmaps();

		final byte[] hdr = new byte[32];
		IO.readFully(fd, hdr, 0, hdr.length);

		// Check the magic bytes
		for (int i = 0; i < MAGIC.length; i++) {
			if (hdr[i] != MAGIC[i]) {
				byte[] actual = new byte[MAGIC.length];
				System.arraycopy(hdr, 0, actual, 0, MAGIC.length);
				throw new IOException(MessageFormat.format(
						JGitText.get().expectedGot, Arrays.toString(MAGIC),
						Arrays.toString(actual)));
			}
		}

		// Read the version (2 bytes)
		final int version = NB.decodeUInt16(hdr, 4);
		if (version != 1)
			throw new IOException(MessageFormat.format(
					JGitText.get().unsupportedPackIndexVersion,
					Integer.valueOf(version)));

		// Read the options (2 bytes)
		final int opts = NB.decodeUInt16(hdr, 6);
		switch (opts) {
		case OPT_FULL:
			// Bitmaps are self contained within this file.
			break;
		default:
			throw new IOException(MessageFormat.format(
					JGitText.get().expectedGot, Integer.valueOf(OPT_FULL),
					Integer.valueOf(opts)));
		}

		// Read the number of entries (1 int32)
		long numEntries = NB.decodeUInt32(hdr, 8);
		if (numEntries > Integer.MAX_VALUE)
			throw new IOException(JGitText.get().indexFileIsTooLargeForJgit);

		// Checksum applied on the bottom of the corresponding pack file.
		this.packChecksum = new byte[20];
		System.arraycopy(hdr, 12, packChecksum, 0, packChecksum.length);

		// Read the bitmaps for the Git types
		SimpleDataInput dataInput = new SimpleDataInput(fd);
		this.commits = readBitmap(dataInput);
		this.trees = readBitmap(dataInput);
		this.blobs = readBitmap(dataInput);
		this.tags = readBitmap(dataInput);

		// An entry is object id, xor offset, and a length encoded bitmap.
		// the object id is an int32 of the nth position sorted by name.
		// the xor offset is a single byte offset back in the list of entries.
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

			ObjectId objectId = packIndex.getObjectId(nthObjectId);
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
	public int findPosition(AnyObjectId objectId) {
		long offset = packIndex.findOffset(objectId);
		if (offset == -1)
			return -1;
		return reverseIndex.findPostion(offset);
	}

	@Override
	public ObjectId getObject(int position) throws IllegalArgumentException {
		ObjectId objectId = reverseIndex.findObjectByPosition(position);
		if (objectId == null)
			throw new IllegalArgumentException();
		return objectId;
	}

	@Override
	public int getObjectCount() {
		return (int) packIndex.getObjectCount();
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
		// TODO(cranger): compare the pack checksum?
		if (o instanceof PackBitmapIndexV1)
			return getPackIndex() == ((PackBitmapIndexV1) o).getPackIndex();
		return false;
	}

	@Override
	public int hashCode() {
		return getPackIndex().hashCode();
	}

	PackIndex getPackIndex() {
		return packIndex;
	}

	private static EWAHCompressedBitmap readBitmap(DataInput dataInput)
			throws IOException {
		EWAHCompressedBitmap bitmap = new EWAHCompressedBitmap();
		bitmap.deserialize(dataInput);
		return bitmap;
	}
}
