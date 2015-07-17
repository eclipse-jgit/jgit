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

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A mutable SHA-256 abstraction.
 *
 * Ported to SHA-256 from {@link MutableObjectId}
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
	 *            be in range [0, {@link Constants#LONG_OBJECT_ID_LENGTH}).
	 * @param value
	 *            the value of the specified byte at {@code index}. Values are
	 *            unsigned and thus are in the range [0,255] rather than the
	 *            signed byte range of [-128, 127].
	 * @throws ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to
	 *             {@link Constants#LONG_OBJECT_ID_LENGTH}, or greater than
	 *             {@link Constants#LONG_OBJECT_ID_LENGTH}.
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

	/** Make this id match {@link LongObjectId#zeroId()}. */
	public void clear() {
		w1 = 0;
		w2 = 0;
		w3 = 0;
		w4 = 0;
	}

	/**
	 * Copy an LongObjectId into this mutable buffer.
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
	 * Convert an LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes must be
	 *            available within this byte array.
	 */
	public void fromRaw(final byte[] bs) {
		fromRaw(bs, 0);
	}

	/**
	 * Convert an LongObjectId from raw binary representation.
	 *
	 * @param bs
	 *            the raw byte buffer to read from. At least 32 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 */
	public void fromRaw(final byte[] bs, final int p) {
		w1 = NB.decodeInt64(bs, p);
		w2 = NB.decodeInt64(bs, p + 8);
		w3 = NB.decodeInt64(bs, p + 16);
		w4 = NB.decodeInt64(bs, p + 24);
	}

	/**
	 * Convert an LongObjectId from binary representation expressed in integers.
	 *
	 * @param longs
	 *            the raw long buffer to read from. At least 4 longs must be
	 *            available within this longs array.
	 */
	public void fromRaw(final long[] longs) {
		fromRaw(longs, 0);
	}

	/**
	 * Convert an LongObjectId from binary representation expressed in longs.
	 *
	 * @param longs
	 *            the raw int buffer to read from. At least 4 longs after p must
	 *            be available within this longs array.
	 * @param p
	 *            position to read the first integer of data from.
	 *
	 */
	public void fromRaw(final long[] longs, final int p) {
		w1 = longs[p];
		w2 = longs[p + 1];
		w3 = longs[p + 2];
		w4 = longs[p + 3];
	}

	/**
	 * Convert an LongObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 32 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 */
	public void fromString(final byte[] buf, final int offset) {
		fromHexString(buf, offset);
	}

	/**
	 * Convert an LongObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be 64 characters long.
	 */
	public void fromString(final String str) {
		if (str.length() != Constants.LONG_OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(
					MessageFormat.format(LfsText.get().invalidLongId, str));
		fromHexString(org.eclipse.jgit.lib.Constants.encodeASCII(str), 0);
	}

	private void fromHexString(final byte[] bs, int p) {
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

	@Override
	public LongObjectId toObjectId() {
		return new LongObjectId(this);
	}
}
