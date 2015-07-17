/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lfs.lib;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A SHA-256 abstraction.
 *
 * Ported to SHA-256 from {@link ObjectId}
 *
 * @since 4.3
 */
public class LongObjectId extends AnyLongObjectId implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final LongObjectId ZEROID;

	private static final String ZEROID_STR;

	static {
		ZEROID = new LongObjectId(0L, 0L, 0L, 0L);
		ZEROID_STR = ZEROID.name();
	}

	/**
	 * Get the special all-zero LongObjectId.
	 *
	 * @return the all-zero LongObjectId, often used to stand-in for no object.
	 */
	public static final LongObjectId zeroId() {
		return ZEROID;
	}

	/**
	 * Test a string of characters to verify that it can be interpreted as
	 * LongObjectId.
	 * <p>
	 * If true the string can be parsed with {@link #fromString(String)}.
	 *
	 * @param id
	 *            the string to test.
	 * @return true if the string can converted into an LongObjectId.
	 */
	public static final boolean isId(final String id) {
		if (id.length() != Constants.LONG_OBJECT_ID_STRING_LENGTH)
			return false;
		try {
			for (int i = 0; i < Constants.LONG_OBJECT_ID_STRING_LENGTH; i++) {
				RawParseUtils.parseHexInt4((byte) id.charAt(i));
			}
			return true;
		} catch (ArrayIndexOutOfBoundsException e) {
			return false;
		}
	}

	/**
	 * Convert a LongObjectId into a hex string representation.
	 *
	 * @param i
	 *            the id to convert. May be null.
	 * @return the hex string conversion of this id's content.
	 */
	public static final String toString(final LongObjectId i) {
		return i != null ? i.name() : ZEROID_STR;
	}

	/**
	 * Compare two object identifier byte sequences for equality.
	 *
	 * @param firstBuffer
	 *            the first buffer to compare against. Must have at least 32
	 *            bytes from position fi through the end of the buffer.
	 * @param fi
	 *            first offset within firstBuffer to begin testing.
	 * @param secondBuffer
	 *            the second buffer to compare against. Must have at least 32
	 *            bytes from position si through the end of the buffer.
	 * @param si
	 *            first offset within secondBuffer to begin testing.
	 * @return true if the two identifiers are the same.
	 */
	public static boolean equals(final byte[] firstBuffer, final int fi,
			final byte[] secondBuffer, final int si) {
		return firstBuffer[fi] == secondBuffer[si]
				&& firstBuffer[fi + 1] == secondBuffer[si + 1]
				&& firstBuffer[fi + 2] == secondBuffer[si + 2]
				&& firstBuffer[fi + 3] == secondBuffer[si + 3]
				&& firstBuffer[fi + 4] == secondBuffer[si + 4]
				&& firstBuffer[fi + 5] == secondBuffer[si + 5]
				&& firstBuffer[fi + 6] == secondBuffer[si + 6]
				&& firstBuffer[fi + 7] == secondBuffer[si + 7]
				&& firstBuffer[fi + 8] == secondBuffer[si + 8]
				&& firstBuffer[fi + 9] == secondBuffer[si + 9]
				&& firstBuffer[fi + 10] == secondBuffer[si + 10]
				&& firstBuffer[fi + 11] == secondBuffer[si + 11]
				&& firstBuffer[fi + 12] == secondBuffer[si + 12]
				&& firstBuffer[fi + 13] == secondBuffer[si + 13]
				&& firstBuffer[fi + 14] == secondBuffer[si + 14]
				&& firstBuffer[fi + 15] == secondBuffer[si + 15]
				&& firstBuffer[fi + 16] == secondBuffer[si + 16]
				&& firstBuffer[fi + 17] == secondBuffer[si + 17]
				&& firstBuffer[fi + 18] == secondBuffer[si + 18]
				&& firstBuffer[fi + 19] == secondBuffer[si + 19]
				&& firstBuffer[fi + 20] == secondBuffer[si + 20]
				&& firstBuffer[fi + 21] == secondBuffer[si + 21]
				&& firstBuffer[fi + 22] == secondBuffer[si + 22]
				&& firstBuffer[fi + 23] == secondBuffer[si + 23]
				&& firstBuffer[fi + 24] == secondBuffer[si + 24]
				&& firstBuffer[fi + 25] == secondBuffer[si + 25]
				&& firstBuffer[fi + 26] == secondBuffer[si + 26]
				&& firstBuffer[fi + 27] == secondBuffer[si + 27]
				&& firstBuffer[fi + 28] == secondBuffer[si + 28]
				&& firstBuffer[fi + 29] == secondBuffer[si + 29]
				&& firstBuffer[fi + 30] == secondBuffer[si + 30]
				&& firstBuffer[fi + 31] == secondBuffer[si + 31];
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes must be
	 *            available within this byte array.
	 * @return the converted object id.
	 */
	public static final LongObjectId fromRaw(final byte[] bs) {
		return fromRaw(bs, 0);
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 * @return the converted object id.
	 */
	public static final LongObjectId fromRaw(final byte[] bs, final int p) {
		final long a = NB.decodeInt64(bs, p);
		final long b = NB.decodeInt64(bs, p + 8);
		final long c = NB.decodeInt64(bs, p + 16);
		final long d = NB.decodeInt64(bs, p + 24);
		return new LongObjectId(a, b, c, d);
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param is
	 *            the raw long buffer to read from. At least 4 longs must be
	 *            available within this long array.
	 * @return the converted object id.
	 */
	public static final LongObjectId fromRaw(final long[] is) {
		return fromRaw(is, 0);
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param is
	 *            the raw long buffer to read from. At least 4 longs after p
	 *            must be available within this long array.
	 * @param p
	 *            position to read the first long of data from.
	 * @return the converted object id.
	 */
	public static final LongObjectId fromRaw(final long[] is, final int p) {
		return new LongObjectId(is[p], is[p + 1], is[p + 2], is[p + 3]);
	}

	/**
	 * Convert a LongObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 64 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 * @return the converted object id.
	 */
	public static final LongObjectId fromString(final byte[] buf, final int offset) {
		return fromHexString(buf, offset);
	}

	/**
	 * Convert a LongObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be 64 characters long.
	 * @return the converted object id.
	 */
	public static LongObjectId fromString(final String str) {
		if (str.length() != Constants.LONG_OBJECT_ID_STRING_LENGTH)
			throw new InvalidLongObjectIdException(str);
		return fromHexString(org.eclipse.jgit.lib.Constants.encodeASCII(str),
				0);
	}

	private static final LongObjectId fromHexString(final byte[] bs, int p) {
		try {
			final long a = RawParseUtils.parseHexInt64(bs, p);
			final long b = RawParseUtils.parseHexInt64(bs, p + 16);
			final long c = RawParseUtils.parseHexInt64(bs, p + 32);
			final long d = RawParseUtils.parseHexInt64(bs, p + 48);
			return new LongObjectId(a, b, c, d);
		} catch (ArrayIndexOutOfBoundsException e1) {
			throw new InvalidLongObjectIdException(bs, p,
					Constants.LONG_OBJECT_ID_STRING_LENGTH);
		}
	}

	LongObjectId(final long new_1, final long new_2, final long new_3,
			final long new_4) {
		w1 = new_1;
		w2 = new_2;
		w3 = new_3;
		w4 = new_4;
	}

	/**
	 * Initialize this instance by copying another existing LongObjectId.
	 * <p>
	 * This constructor is mostly useful for subclasses which want to extend a
	 * LongObjectId with more properties, but initialize from an existing
	 * LongObjectId instance acquired by other means.
	 *
	 * @param src
	 *            another already parsed LongObjectId to copy the value out of.
	 */
	protected LongObjectId(final AnyLongObjectId src) {
		w1 = src.w1;
		w2 = src.w2;
		w3 = src.w3;
		w4 = src.w4;
	}

	@Override
	public LongObjectId toObjectId() {
		return this;
	}

	private void writeObject(ObjectOutputStream os) throws IOException {
		os.writeLong(w1);
		os.writeLong(w2);
		os.writeLong(w3);
		os.writeLong(w4);
	}

	private void readObject(ObjectInputStream ois) throws IOException {
		w1 = ois.readLong();
		w2 = ois.readLong();
		w3 = ois.readLong();
		w4 = ois.readLong();
	}
}
