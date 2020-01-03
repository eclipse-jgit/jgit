/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A SHA-1 abstraction.
 */
public class ObjectId extends AnyObjectId implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final ObjectId ZEROID;

	private static final String ZEROID_STR;

	static {
		ZEROID = new ObjectId(0, 0, 0, 0, 0);
		ZEROID_STR = ZEROID.name();
	}

	/**
	 * Get the special all-null ObjectId.
	 *
	 * @return the all-null ObjectId, often used to stand-in for no object.
	 */
	public static final ObjectId zeroId() {
		return ZEROID;
	}

	/**
	 * Test a string of characters to verify it is a hex format.
	 * <p>
	 * If true the string can be parsed with {@link #fromString(String)}.
	 *
	 * @param id
	 *            the string to test.
	 * @return true if the string can converted into an ObjectId.
	 */
	public static final boolean isId(@Nullable String id) {
		if (id == null) {
			return false;
		}
		if (id.length() != Constants.OBJECT_ID_STRING_LENGTH)
			return false;
		try {
			for (int i = 0; i < Constants.OBJECT_ID_STRING_LENGTH; i++) {
				RawParseUtils.parseHexInt4((byte) id.charAt(i));
			}
			return true;
		} catch (ArrayIndexOutOfBoundsException e) {
			return false;
		}
	}

	/**
	 * Convert an ObjectId into a hex string representation.
	 *
	 * @param i
	 *            the id to convert. May be null.
	 * @return the hex string conversion of this id's content.
	 */
	public static final String toString(ObjectId i) {
		return i != null ? i.name() : ZEROID_STR;
	}

	/**
	 * Compare two object identifier byte sequences for equality.
	 *
	 * @param firstBuffer
	 *            the first buffer to compare against. Must have at least 20
	 *            bytes from position fi through the end of the buffer.
	 * @param fi
	 *            first offset within firstBuffer to begin testing.
	 * @param secondBuffer
	 *            the second buffer to compare against. Must have at least 20
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
				&& firstBuffer[fi + 19] == secondBuffer[si + 19];
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes must be
	 *            available within this byte array.
	 * @return the converted object id.
	 */
	public static final ObjectId fromRaw(byte[] bs) {
		return fromRaw(bs, 0);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 * @return the converted object id.
	 */
	public static final ObjectId fromRaw(byte[] bs, int p) {
		final int a = NB.decodeInt32(bs, p);
		final int b = NB.decodeInt32(bs, p + 4);
		final int c = NB.decodeInt32(bs, p + 8);
		final int d = NB.decodeInt32(bs, p + 12);
		final int e = NB.decodeInt32(bs, p + 16);
		return new ObjectId(a, b, c, d, e);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param is
	 *            the raw integers buffer to read from. At least 5 integers must
	 *            be available within this int array.
	 * @return the converted object id.
	 */
	public static final ObjectId fromRaw(int[] is) {
		return fromRaw(is, 0);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param is
	 *            the raw integers buffer to read from. At least 5 integers
	 *            after p must be available within this int array.
	 * @param p
	 *            position to read the first integer of data from.
	 * @return the converted object id.
	 */
	public static final ObjectId fromRaw(int[] is, int p) {
		return new ObjectId(is[p], is[p + 1], is[p + 2], is[p + 3], is[p + 4]);
	}

	/**
	 * Convert an ObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 40 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 * @return the converted object id.
	 */
	public static final ObjectId fromString(byte[] buf, int offset) {
		return fromHexString(buf, offset);
	}

	/**
	 * Convert an ObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be 40 characters long.
	 * @return the converted object id.
	 */
	public static ObjectId fromString(String str) {
		if (str.length() != Constants.OBJECT_ID_STRING_LENGTH) {
			throw new InvalidObjectIdException(str);
		}
		return fromHexString(Constants.encodeASCII(str), 0);
	}

	private static final ObjectId fromHexString(byte[] bs, int p) {
		try {
			final int a = RawParseUtils.parseHexInt32(bs, p);
			final int b = RawParseUtils.parseHexInt32(bs, p + 8);
			final int c = RawParseUtils.parseHexInt32(bs, p + 16);
			final int d = RawParseUtils.parseHexInt32(bs, p + 24);
			final int e = RawParseUtils.parseHexInt32(bs, p + 32);
			return new ObjectId(a, b, c, d, e);
		} catch (ArrayIndexOutOfBoundsException e1) {
			throw new InvalidObjectIdException(bs, p,
					Constants.OBJECT_ID_STRING_LENGTH);
		}
	}

	/**
	 * Construct an ObjectId from 160 bits provided in 5 words.
	 *
	 * @param new_1
	 *            an int
	 * @param new_2
	 *            an int
	 * @param new_3
	 *            an int
	 * @param new_4
	 *            an int
	 * @param new_5
	 *            an int
	 * @since 4.7
	 */
	public ObjectId(int new_1, int new_2, int new_3, int new_4, int new_5) {
		w1 = new_1;
		w2 = new_2;
		w3 = new_3;
		w4 = new_4;
		w5 = new_5;
	}

	/**
	 * Initialize this instance by copying another existing ObjectId.
	 * <p>
	 * This constructor is mostly useful for subclasses who want to extend an
	 * ObjectId with more properties, but initialize from an existing ObjectId
	 * instance acquired by other means.
	 *
	 * @param src
	 *            another already parsed ObjectId to copy the value out of.
	 */
	protected ObjectId(AnyObjectId src) {
		w1 = src.w1;
		w2 = src.w2;
		w3 = src.w3;
		w4 = src.w4;
		w5 = src.w5;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId toObjectId() {
		return this;
	}

	private void writeObject(ObjectOutputStream os) throws IOException {
		os.writeInt(w1);
		os.writeInt(w2);
		os.writeInt(w3);
		os.writeInt(w4);
		os.writeInt(w5);
	}

	private void readObject(ObjectInputStream ois) throws IOException {
		w1 = ois.readInt();
		w2 = ois.readInt();
		w3 = ois.readInt();
		w4 = ois.readInt();
		w5 = ois.readInt();
	}
}
