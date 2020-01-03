/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.lib;

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A mutable SHA-256 abstraction.
 *
 * Ported to SHA-256 from {@link org.eclipse.jgit.lib.MutableObjectId}
 *
 * @since 4.3
 */
public class MutableLongObjectId extends AnyLongObjectId {
	/**
	 * Empty constructor. Initialize object with default (zeros) value.
	 */
	public MutableLongObjectId() {
		super();
	}

	/**
	 * Copying constructor.
	 *
	 * @param src
	 *            original entry, to copy id from
	 */
	MutableLongObjectId(MutableLongObjectId src) {
		fromObjectId(src);
	}

	/**
	 * Set any byte in the id.
	 *
	 * @param index
	 *            index of the byte to set in the raw form of the ObjectId. Must
	 *            be in range [0,
	 *            {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH}).
	 * @param value
	 *            the value of the specified byte at {@code index}. Values are
	 *            unsigned and thus are in the range [0,255] rather than the
	 *            signed byte range of [-128, 127].
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to
	 *             {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH},
	 *             or greater than
	 *             {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH}.
	 */
	public void setByte(int index, int value) {
		switch (index >> 3) {
		case 0:
			w1 = set(w1, index & 7, value);
			break;
		case 1:
			w2 = set(w2, index & 7, value);
			break;
		case 2:
			w3 = set(w3, index & 7, value);
			break;
		case 3:
			w4 = set(w4, index & 7, value);
			break;
		default:
			throw new ArrayIndexOutOfBoundsException(index);
		}
	}

	private static long set(long w, int index, long value) {
		value &= 0xff;

		switch (index) {
		case 0:
			return (w & 0x00ffffffffffffffL) | (value << 56);
		case 1:
			return (w & 0xff00ffffffffffffL) | (value << 48);
		case 2:
			return (w & 0xffff00ffffffffffL) | (value << 40);
		case 3:
			return (w & 0xffffff00ffffffffL) | (value << 32);
		case 4:
			return (w & 0xffffffff00ffffffL) | (value << 24);
		case 5:
			return (w & 0xffffffffff00ffffL) | (value << 16);
		case 6:
			return (w & 0xffffffffffff00ffL) | (value << 8);
		case 7:
			return (w & 0xffffffffffffff00L) | value;
		default:
			throw new ArrayIndexOutOfBoundsException();
		}
	}

	/**
	 * Make this id match
	 * {@link org.eclipse.jgit.lfs.lib.LongObjectId#zeroId()}.
	 */
	public void clear() {
		w1 = 0;
		w2 = 0;
		w3 = 0;
		w4 = 0;
	}

	/**
	 * Copy a LongObjectId into this mutable buffer.
	 *
	 * @param src
	 *            the source id to copy from.
	 */
	public void fromObjectId(AnyLongObjectId src) {
		this.w1 = src.w1;
		this.w2 = src.w2;
		this.w3 = src.w3;
		this.w4 = src.w4;
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes must be
	 *            available within this byte array.
	 */
	public void fromRaw(byte[] bs) {
		fromRaw(bs, 0);
	}

	/**
	 * Convert a LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 */
	public void fromRaw(byte[] bs, int p) {
		w1 = NB.decodeInt64(bs, p);
		w2 = NB.decodeInt64(bs, p + 8);
		w3 = NB.decodeInt64(bs, p + 16);
		w4 = NB.decodeInt64(bs, p + 24);
	}

	/**
	 * Convert a LongObjectId from binary representation expressed in integers.
	 *
	 * @param longs
	 *            the raw long buffer to read from. At least 4 longs must be
	 *            available within this longs array.
	 */
	public void fromRaw(long[] longs) {
		fromRaw(longs, 0);
	}

	/**
	 * Convert a LongObjectId from binary representation expressed in longs.
	 *
	 * @param longs
	 *            the raw int buffer to read from. At least 4 longs after p must
	 *            be available within this longs array.
	 * @param p
	 *            position to read the first integer of data from.
	 */
	public void fromRaw(long[] longs, int p) {
		w1 = longs[p];
		w2 = longs[p + 1];
		w3 = longs[p + 2];
		w4 = longs[p + 3];
	}

	/**
	 * Convert a LongObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 32 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 */
	public void fromString(byte[] buf, int offset) {
		fromHexString(buf, offset);
	}

	/**
	 * Convert a LongObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be 64 characters long.
	 */
	public void fromString(String str) {
		if (str.length() != Constants.LONG_OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(
					MessageFormat.format(LfsText.get().invalidLongId, str));
		fromHexString(org.eclipse.jgit.lib.Constants.encodeASCII(str), 0);
	}

	private void fromHexString(byte[] bs, int p) {
		try {
			w1 = RawParseUtils.parseHexInt64(bs, p);
			w2 = RawParseUtils.parseHexInt64(bs, p + 16);
			w3 = RawParseUtils.parseHexInt64(bs, p + 32);
			w4 = RawParseUtils.parseHexInt64(bs, p + 48);
		} catch (ArrayIndexOutOfBoundsException e1) {
			throw new InvalidLongObjectIdException(bs, p,
					Constants.LONG_OBJECT_ID_STRING_LENGTH);
		}
	}

	/** {@inheritDoc} */
	@Override
	public LongObjectId toObjectId() {
		return new LongObjectId(this);
	}
}
