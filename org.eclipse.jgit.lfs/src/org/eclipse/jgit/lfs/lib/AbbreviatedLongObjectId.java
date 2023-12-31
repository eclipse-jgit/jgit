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

import java.io.Serializable;
import java.text.MessageFormat;

import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.internal.LfsText;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A prefix abbreviation of an {@link org.eclipse.jgit.lfs.lib.LongObjectId}.
 * <p>
 * Enable abbreviating SHA-256 strings used by Git LFS, using sufficient leading
 * digits from the LongObjectId name to still be unique within the repository
 * the string was generated from. These ids are likely to be unique for a useful
 * period of time, especially if they contain at least 6-10 hex digits.
 * <p>
 * This class converts the hex string into a binary form, to make it more
 * efficient for matching against an object.
 *
 * Ported to SHA-256 from {@link org.eclipse.jgit.lib.AbbreviatedObjectId}
 *
 * @since 4.3
 */
public final class AbbreviatedLongObjectId implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Test a string of characters to verify it is a hex format.
	 * <p>
	 * If true the string can be parsed with {@link #fromString(String)}.
	 *
	 * @param id
	 *            the string to test.
	 * @return true if the string can converted into an AbbreviatedObjectId.
	 */
	public static final boolean isId(String id) {
		if (id.length() < 2
				|| Constants.LONG_OBJECT_ID_STRING_LENGTH < id.length())
			return false;
		try {
			for (int i = 0; i < id.length(); i++)
				RawParseUtils.parseHexInt4((byte) id.charAt(i));
			return true;
		} catch (ArrayIndexOutOfBoundsException e) {
			return false;
		}
	}

	/**
	 * Convert an AbbreviatedObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from.
	 * @param offset
	 *            position to read the first character from.
	 * @param end
	 *            one past the last position to read (<code>end-offset</code> is
	 *            the length of the string).
	 * @return the converted object id.
	 */
	public static final AbbreviatedLongObjectId fromString(final byte[] buf,
			final int offset, final int end) {
		if (end - offset > Constants.LONG_OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(MessageFormat.format(
							LfsText.get().invalidLongIdLength,
					Integer.valueOf(end - offset),
					Integer.valueOf(Constants.LONG_OBJECT_ID_STRING_LENGTH)));
		return fromHexString(buf, offset, end);
	}

	/**
	 * Convert an AbbreviatedObjectId from an
	 * {@link org.eclipse.jgit.lib.AnyObjectId}.
	 * <p>
	 * This method copies over all bits of the Id, and is therefore complete
	 * (see {@link #isComplete()}).
	 *
	 * @param id
	 *            the {@link org.eclipse.jgit.lib.ObjectId} to convert from.
	 * @return the converted object id.
	 */
	public static final AbbreviatedLongObjectId fromLongObjectId(
			AnyLongObjectId id) {
		return new AbbreviatedLongObjectId(
				Constants.LONG_OBJECT_ID_STRING_LENGTH, id.w1, id.w2, id.w3,
				id.w4);
	}

	/**
	 * Convert an AbbreviatedLongObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be &lt;= 64 characters.
	 * @return the converted object id.
	 */
	public static final AbbreviatedLongObjectId fromString(String str) {
		if (str.length() > Constants.LONG_OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(
					MessageFormat.format(LfsText.get().invalidLongId, str));
		final byte[] b = org.eclipse.jgit.lib.Constants.encodeASCII(str);
		return fromHexString(b, 0, b.length);
	}

	private static final AbbreviatedLongObjectId fromHexString(final byte[] bs,
			int ptr, final int end) {
		try {
			final long a = hexUInt64(bs, ptr, end);
			final long b = hexUInt64(bs, ptr + 16, end);
			final long c = hexUInt64(bs, ptr + 32, end);
			final long d = hexUInt64(bs, ptr + 48, end);
			return new AbbreviatedLongObjectId(end - ptr, a, b, c, d);
		} catch (ArrayIndexOutOfBoundsException e) {
			InvalidLongObjectIdException e1 = new InvalidLongObjectIdException(
					bs, ptr, end - ptr);
			e1.initCause(e);
			throw e1;
		}
	}

	private static final long hexUInt64(final byte[] bs, int p, final int end) {
		if (16 <= end - p)
			return RawParseUtils.parseHexInt64(bs, p);

		long r = 0;
		int n = 0;
		while (n < 16 && p < end) {
			r <<= 4;
			r |= RawParseUtils.parseHexInt4(bs[p++]);
			n++;
		}
		return r << ((16 - n) * 4);
	}

	static long mask(int nibbles, long word, long v) {
		final long b = (word - 1) * 16;
		if (b + 16 <= nibbles) {
			// We have all of the bits required for this word.
			//
			return v;
		}

		if (nibbles <= b) {
			// We have none of the bits required for this word.
			//
			return 0;
		}

		final long s = 64 - (nibbles - b) * 4;
		return (v >>> s) << s;
	}

	/** Number of half-bytes used by this id. */
	final int nibbles;

	final long w1;

	final long w2;

	final long w3;

	final long w4;

	AbbreviatedLongObjectId(final int n, final long new_1, final long new_2,
			final long new_3, final long new_4) {
		nibbles = n;
		w1 = new_1;
		w2 = new_2;
		w3 = new_3;
		w4 = new_4;
	}

	/**
	 * Get length
	 *
	 * @return number of hex digits appearing in this id.
	 */
	public int length() {
		return nibbles;
	}

	/**
	 * Check if this id is complete
	 *
	 * @return true if this ObjectId is actually a complete id.
	 */
	public boolean isComplete() {
		return length() == Constants.LONG_OBJECT_ID_STRING_LENGTH;
	}

	/**
	 * Convert to LongObjectId
	 *
	 * @return a complete ObjectId; null if {@link #isComplete()} is false.
	 */
	public LongObjectId toLongObjectId() {
		return isComplete() ? new LongObjectId(w1, w2, w3, w4) : null;
	}

	/**
	 * Compares this abbreviation to a full object id.
	 *
	 * @param other
	 *            the other object id.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public final int prefixCompare(AnyLongObjectId other) {
		int cmp;

		cmp = NB.compareUInt64(w1, mask(1, other.w1));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, mask(2, other.w2));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, mask(3, other.w3));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, mask(4, other.w4));
	}

	/**
	 * Compare this abbreviation to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            32 bytes, starting at this position are required.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public final int prefixCompare(byte[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, mask(1, NB.decodeInt64(bs, p)));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, mask(2, NB.decodeInt64(bs, p + 8)));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, mask(3, NB.decodeInt64(bs, p + 16)));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, mask(4, NB.decodeInt64(bs, p + 24)));
	}

	/**
	 * Compare this abbreviation to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least 4
	 *            longs, starting at this position are required.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public final int prefixCompare(long[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, mask(1, bs[p]));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, mask(2, bs[p + 1]));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, mask(3, bs[p + 2]));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, mask(4, bs[p + 3]));
	}

	/**
	 * Get the first byte of this id
	 *
	 * @return value for a fan-out style map, only valid of length &gt;= 2.
	 */
	public final int getFirstByte() {
		return (int) (w1 >>> 56);
	}

	private long mask(long word, long v) {
		return mask(nibbles, word, v);
	}

	@Override
	public int hashCode() {
		return (int) (w1 >> 32);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof AbbreviatedLongObjectId) {
			final AbbreviatedLongObjectId b = (AbbreviatedLongObjectId) o;
			return nibbles == b.nibbles && w1 == b.w1 && w2 == b.w2
					&& w3 == b.w3 && w4 == b.w4;
		}
		return false;
	}

	/**
	 * <p>name.</p>
	 *
	 * @return string form of the abbreviation, in lower case hexadecimal.
	 */
	public final String name() {
		final char[] b = new char[Constants.LONG_OBJECT_ID_STRING_LENGTH];

		AnyLongObjectId.formatHexChar(b, 0, w1);
		if (nibbles <= 16)
			return new String(b, 0, nibbles);

		AnyLongObjectId.formatHexChar(b, 16, w2);
		if (nibbles <= 32)
			return new String(b, 0, nibbles);

		AnyLongObjectId.formatHexChar(b, 32, w3);
		if (nibbles <= 48)
			return new String(b, 0, nibbles);

		AnyLongObjectId.formatHexChar(b, 48, w4);
		return new String(b, 0, nibbles);
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AbbreviatedLongObjectId[" + name() + "]"; //$NON-NLS-1$
	}
}
