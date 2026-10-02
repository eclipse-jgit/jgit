/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A mutable object id abstraction.
 */
public class MutableObjectId extends AnyObjectId {
	/**
	 * Empty constructor. Initialize object with default (zeros) value.
	 */
	public MutableObjectId() {
		super();
		w = new int[Constants.OBJECT_ID_LENGTH / 4];
	}

	/**
	 * Copying constructor.
	 *
	 * @param src
	 *            original entry, to copy id from
	 */
	MutableObjectId(MutableObjectId src) {
		fromObjectId(src);
	}

	/**
	 * Set any byte in the id.
	 *
	 * @param index
	 *            index of the byte to set in the raw form of the ObjectId. Must
	 *            be in range [0, {@link #getLength()}).
	 * @param value
	 *            the value of the specified byte at {@code index}. Values are
	 *            unsigned and thus are in the range [0,255] rather than the
	 *            signed byte range of [-128, 127].
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to {@link #getLength()},
	 *             or greater than {@link #getLength()}.
	 */
	public void setByte(int index, int value) {
		final int word = index >> 2;
		if (word >= w.length) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		w[word] = set(w[word], index & 3, value);
	}

	private static int set(int w, int index, int value) {
		value &= 0xff;

		switch (index) {
		case 0:
			return (w & 0x00ffffff) | (value << 24);
		case 1:
			return (w & 0xff00ffff) | (value << 16);
		case 2:
			return (w & 0xffff00ff) | (value << 8);
		case 3:
			return (w & 0xffffff00) | value;
		default:
			throw new ArrayIndexOutOfBoundsException();
		}
	}

	/**
	 * Make this id match {@link org.eclipse.jgit.lib.ObjectId#zeroId()}.
	 */
	public void clear() {
		java.util.Arrays.fill(w, 0);
	}

	/**
	 * Copy an ObjectId into this mutable buffer.
	 *
	 * @param src
	 *            the source id to copy from.
	 */
	public void fromObjectId(AnyObjectId src) {
		w = src.w.clone();
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes must be
	 *            available within this byte array.
	 */
	public void fromRaw(byte[] bs) {
		fromRaw(bs, 0);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 */
	public void fromRaw(byte[] bs, int p) {
		ensureCapacity(Constants.OBJECT_ID_LENGTH);
		w[0] = NB.decodeInt32(bs, p);
		w[1] = NB.decodeInt32(bs, p + 4);
		w[2] = NB.decodeInt32(bs, p + 8);
		w[3] = NB.decodeInt32(bs, p + 12);
		w[4] = NB.decodeInt32(bs, p + 16);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least {@code len} bytes
	 *            after p must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 * @param len
	 *            number of bytes to read, i.e. the length of the object id:
	 *            {@code 20} for SHA-1, {@code 32} for SHA-256.
	 * @since 7.9
	 */
	public void fromRaw(byte[] bs, int p, int len) {
		if (len == Constants.OBJECT_ID_LENGTH) {
			fromRaw(bs, p);
			return;
		}
		if (len % 4 != 0 || len <= 0) {
			throw new IllegalArgumentException(
					"Invalid object id length: " + len); //$NON-NLS-1$
		}
		ensureCapacity(len);
		for (int i = 0; i < w.length; i++) {
			w[i] = NB.decodeInt32(bs, p + 4 * i);
		}
	}

	/**
	 * Convert an ObjectId from binary representation expressed in integers.
	 *
	 * @param ints
	 *            the raw int buffer to read from. At least 5 integers must be
	 *            available within this integers array.
	 */
	public void fromRaw(int[] ints) {
		fromRaw(ints, 0);
	}

	/**
	 * Convert an ObjectId from binary representation expressed in integers.
	 *
	 * @param ints
	 *            the raw int buffer to read from. At least 5 integers after p
	 *            must be available within this integers array.
	 * @param p
	 *            position to read the first integer of data from.
	 */
	public void fromRaw(int[] ints, int p) {
		ensureCapacity(Constants.OBJECT_ID_LENGTH);
		w[0] = ints[p];
		w[1] = ints[p + 1];
		w[2] = ints[p + 2];
		w[3] = ints[p + 3];
		w[4] = ints[p + 4];
	}

	/**
	 * Convert an ObjectId from binary representation expressed in integers.
	 *
	 * @param a
	 *            an int.
	 * @param b
	 *            an int.
	 * @param c
	 *            an int.
	 * @param d
	 *            an int.
	 * @param e
	 *            an int.
	 * @since 4.7
	 */
	public void set(int a, int b, int c, int d, int e) {
		ensureCapacity(Constants.OBJECT_ID_LENGTH);
		w[0] = a;
		w[1] = b;
		w[2] = c;
		w[3] = d;
		w[4] = e;
	}

	/**
	 * Convert an ObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 40 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 */
	public void fromString(byte[] buf, int offset) {
		fromHexString(buf, offset);
	}

	/**
	 * Convert an ObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least {@code len} bytes
	 *            after {@code offset} must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 * @param len
	 *            number of hex characters to read: {@code 40} for a SHA-1
	 *            object id, {@code 64} for SHA-256.
	 * @since 7.9
	 */
	public void fromString(byte[] buf, int offset, int len) {
		if (len == Constants.OBJECT_ID_STRING_LENGTH) {
			fromHexString(buf, offset);
			return;
		}
		if (len % 8 != 0 || len <= 0) {
			throw new IllegalArgumentException(
					"Invalid object id hex length: " + len); //$NON-NLS-1$
		}
		ensureCapacity(len / 2);
		try {
			for (int i = 0; i < w.length; i++) {
				w[i] = RawParseUtils.parseHexInt32(buf, offset + 8 * i);
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			InvalidObjectIdException e1 = new InvalidObjectIdException(buf,
					offset, len);
			e1.initCause(e);
			throw e1;
		}
	}

	/**
	 * Convert an ObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be 40 characters long for
	 *            SHA-1, or 64 characters long for SHA-256.
	 */
	public void fromString(String str) {
		final int len = str.length();
		if (len != Constants.OBJECT_ID_STRING_LENGTH
				&& len != ObjectFormat.SHA_256.getHexLength()) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidId, str));
		}
		fromString(Constants.encodeASCII(str), 0, len);
	}

	private void fromHexString(byte[] bs, int p) {
		ensureCapacity(Constants.OBJECT_ID_LENGTH);
		try {
			w[0] = RawParseUtils.parseHexInt32(bs, p);
			w[1] = RawParseUtils.parseHexInt32(bs, p + 8);
			w[2] = RawParseUtils.parseHexInt32(bs, p + 16);
			w[3] = RawParseUtils.parseHexInt32(bs, p + 24);
			w[4] = RawParseUtils.parseHexInt32(bs, p + 32);
		} catch (ArrayIndexOutOfBoundsException e) {
			InvalidObjectIdException e1 = new InvalidObjectIdException(bs, p,
					Constants.OBJECT_ID_STRING_LENGTH);
			e1.initCause(e);
			throw e1;
		}
	}

	private void ensureCapacity(int byteLength) {
		final int n = byteLength / 4;
		if (w == null || w.length != n) {
			w = new int[n];
		}
	}

	@Override
	public ObjectId toObjectId() {
		return new ObjectId(this);
	}
}
