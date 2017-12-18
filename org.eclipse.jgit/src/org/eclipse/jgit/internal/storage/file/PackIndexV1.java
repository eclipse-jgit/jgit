/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;

class PackIndexV1 extends PackIndex {
	private static final int IDX_HDR_LEN = 256 * 4;

	private final long[] idxHeader;

	byte[][] idxdata;

	private long objectCnt;

	PackIndexV1(final InputStream fd, final byte[] hdr)
			throws CorruptObjectException, IOException {
		final byte[] fanoutTable = new byte[IDX_HDR_LEN];
		System.arraycopy(hdr, 0, fanoutTable, 0, hdr.length);
		IO.readFully(fd, fanoutTable, hdr.length, IDX_HDR_LEN - hdr.length);

		idxHeader = new long[256]; // really unsigned 32-bit...
		for (int k = 0; k < idxHeader.length; k++)
			idxHeader[k] = NB.decodeUInt32(fanoutTable, k * 4);
		idxdata = new byte[idxHeader.length][];
		for (int k = 0; k < idxHeader.length; k++) {
			int n;
			if (k == 0) {
				n = (int) (idxHeader[k]);
			} else {
				n = (int) (idxHeader[k] - idxHeader[k - 1]);
			}
			if (n > 0) {
				final long len = n * (Constants.OBJECT_ID_LENGTH + 4);
				if (len > Integer.MAX_VALUE - 8) // http://stackoverflow.com/a/8381338
					throw new IOException(JGitText.get().indexFileIsTooLargeForJgit);

				idxdata[k] = new byte[(int) len];
				IO.readFully(fd, idxdata[k], 0, idxdata[k].length);
			}
		}
		objectCnt = idxHeader[255];

		packChecksum = new byte[20];
		IO.readFully(fd, packChecksum, 0, packChecksum.length);
	}

	/** {@inheritDoc} */
	@Override
	public long getObjectCount() {
		return objectCnt;
	}

	/** {@inheritDoc} */
	@Override
	public long getOffset64Count() {
		long n64 = 0;
		for (final MutableEntry e : this) {
			if (e.getOffset() >= Integer.MAX_VALUE)
				n64++;
		}
		return n64;
	}

	private int findLevelOne(final long nthPosition) {
		int levelOne = Arrays.binarySearch(idxHeader, nthPosition + 1);
		if (levelOne >= 0) {
			// If we hit the bucket exactly the item is in the bucket, or
			// any bucket before it which has the same object count.
			//
			long base = idxHeader[levelOne];
			while (levelOne > 0 && base == idxHeader[levelOne - 1])
				levelOne--;
		} else {
			// The item is in the bucket we would insert it into.
			//
			levelOne = -(levelOne + 1);
		}
		return levelOne;
	}

	private int getLevelTwo(final long nthPosition, final int levelOne) {
		final long base = levelOne > 0 ? idxHeader[levelOne - 1] : 0;
		return (int) (nthPosition - base);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(final long nthPosition) {
		final int levelOne = findLevelOne(nthPosition);
		final int p = getLevelTwo(nthPosition, levelOne);
		final int dataIdx = idOffset(p);
		return ObjectId.fromRaw(idxdata[levelOne], dataIdx);
	}

	@Override
	long getOffset(long nthPosition) {
		final int levelOne = findLevelOne(nthPosition);
		final int levelTwo = getLevelTwo(nthPosition, levelOne);
		final int p = (4 + Constants.OBJECT_ID_LENGTH) * levelTwo;
		return NB.decodeUInt32(idxdata[levelOne], p);
	}

	/** {@inheritDoc} */
	@Override
	public long findOffset(final AnyObjectId objId) {
		final int levelOne = objId.getFirstByte();
		byte[] data = idxdata[levelOne];
		if (data == null)
			return -1;
		int high = data.length / (4 + Constants.OBJECT_ID_LENGTH);
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final int pos = idOffset(mid);
			final int cmp = objId.compareTo(data, pos);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0) {
				int b0 = data[pos - 4] & 0xff;
				int b1 = data[pos - 3] & 0xff;
				int b2 = data[pos - 2] & 0xff;
				int b3 = data[pos - 1] & 0xff;
				return (((long) b0) << 24) | (b1 << 16) | (b2 << 8) | (b3);
			} else
				low = mid + 1;
		} while (low < high);
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public long findCRC32(AnyObjectId objId) {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasCRC32Support() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public Iterator<MutableEntry> iterator() {
		return new IndexV1Iterator();
	}

	/** {@inheritDoc} */
	@Override
	public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException {
		byte[] data = idxdata[id.getFirstByte()];
		if (data == null)
			return;
		int max = data.length / (4 + Constants.OBJECT_ID_LENGTH);
		int high = max;
		int low = 0;
		do {
			int p = (low + high) >>> 1;
			final int cmp = id.prefixCompare(data, idOffset(p));
			if (cmp < 0)
				high = p;
			else if (cmp == 0) {
				// We may have landed in the middle of the matches.  Move
				// backwards to the start of matches, then walk forwards.
				//
				while (0 < p && id.prefixCompare(data, idOffset(p - 1)) == 0)
					p--;
				for (; p < max && id.prefixCompare(data, idOffset(p)) == 0; p++) {
					matches.add(ObjectId.fromRaw(data, idOffset(p)));
					if (matches.size() > matchLimit)
						break;
				}
				return;
			} else
				low = p + 1;
		} while (low < high);
	}

	private static int idOffset(int mid) {
		return ((4 + Constants.OBJECT_ID_LENGTH) * mid) + 4;
	}

	private class IndexV1Iterator extends EntriesIterator {
		int levelOne;

		int levelTwo;

		@Override
		protected MutableEntry initEntry() {
			return new MutableEntry() {
				@Override
				protected void ensureId() {
					idBuffer.fromRaw(idxdata[levelOne], levelTwo
							- Constants.OBJECT_ID_LENGTH);
				}
			};
		}

		@Override
		public MutableEntry next() {
			for (; levelOne < idxdata.length; levelOne++) {
				if (idxdata[levelOne] == null)
					continue;
				if (levelTwo < idxdata[levelOne].length) {
					entry.offset = NB.decodeUInt32(idxdata[levelOne], levelTwo);
					levelTwo += Constants.OBJECT_ID_LENGTH + 4;
					returnedNumber++;
					return entry;
				}
				levelTwo = 0;
			}
			throw new NoSuchElementException();
		}
	}
}
