/*
 * Copyright (C) 2008-2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.Serializable;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A prefix abbreviation of an {@link org.eclipse.jgit.lib.ObjectId}.
 * <p>
 * Sometimes Git produces abbreviated SHA-1 strings, using sufficient leading
 * digits from the ObjectId name to still be unique within the repository the
 * string was generated from. These ids are likely to be unique for a useful
 * period of time, especially if they contain at least 6-10 hex digits.
 * <p>
 * This class converts the hex string into a binary form, to make it more
 * efficient for matching against an object.
 */
public final class AbbreviatedObjectId implements Serializable {
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
		if (id.length() < 2 || Constants.OBJECT_ID_STRING_LENGTH < id.length())
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
	public static final AbbreviatedObjectId fromString(final byte[] buf,
			final int offset, final int end) {
		if (end - offset > Constants.OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidIdLength,
					Integer.valueOf(end - offset),
					Integer.valueOf(Constants.OBJECT_ID_STRING_LENGTH)));
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
	public static final AbbreviatedObjectId fromObjectId(AnyObjectId id) {
		return new AbbreviatedObjectId(Constants.OBJECT_ID_STRING_LENGTH,
				id.w1, id.w2, id.w3, id.w4, id.w5);
	}

	/**
	 * Convert an AbbreviatedObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be &lt;= 40 characters.
	 * @return the converted object id.
	 */
	public static final AbbreviatedObjectId fromString(String str) {
		if (str.length() > Constants.OBJECT_ID_STRING_LENGTH)
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().invalidId, str));
		final byte[] b = Constants.encodeASCII(str);
		return fromHexString(b, 0, b.length);
	}

	private static final AbbreviatedObjectId fromHexString(final byte[] bs,
			int ptr, final int end) {
		try {
			final int a = hexUInt32(bs, ptr, end);
			final int b = hexUInt32(bs, ptr + 8, end);
			final int c = hexUInt32(bs, ptr + 16, end);
			final int d = hexUInt32(bs, ptr + 24, end);
			final int e = hexUInt32(bs, ptr + 32, end);
			return new AbbreviatedObjectId(end - ptr, a, b, c, d, e);
		} catch (ArrayIndexOutOfBoundsException e1) {
			throw new InvalidObjectIdException(bs, ptr, end - ptr);
		}
	}

	private static final int hexUInt32(final byte[] bs, int p, final int end) {
		if (8 <= end - p)
			return RawParseUtils.parseHexInt32(bs, p);

		int r = 0, n = 0;
		while (n < 8 && p < end) {
			r <<= 4;
			r |= RawParseUtils.parseHexInt4(bs[p++]);
			n++;
		}
		return r << ((8 - n) * 4);
	}

	static int mask(int nibbles, int word, int v) {
		final int b = (word - 1) * 8;
		if (b + 8 <= nibbles) {
			// We have all of the bits required for this word.
			//
			return v;
		}

		if (nibbles <= b) {
			// We have none of the bits required for this word.
			//
			return 0;
		}

		final int s = 32 - (nibbles - b) * 4;
		return (v >>> s) << s;
	}

	/** Number of half-bytes used by this id. */
	final int nibbles;

	final int w1;

	final int w2;

	final int w3;

	final int w4;

	final int w5;

	AbbreviatedObjectId(final int n, final int new_1, final int new_2,
			final int new_3, final int new_4, final int new_5) {
		nibbles = n;
		w1 = new_1;
		w2 = new_2;
		w3 = new_3;
		w4 = new_4;
		w5 = new_5;
	}

	/**
	 * Get number of hex digits appearing in this id.
	 *
	 * @return number of hex digits appearing in this id.
	 */
	public int length() {
		return nibbles;
	}

	/**
	 * Whether this ObjectId is actually a complete id.
	 *
	 * @return true if this ObjectId is actually a complete id.
	 */
	public boolean isComplete() {
		return length() == Constants.OBJECT_ID_STRING_LENGTH;
	}

	/**
	 * A complete ObjectId; null if {@link #isComplete()} is false
	 *
	 * @return a complete ObjectId; null if {@link #isComplete()} is false
	 */
	public ObjectId toObjectId() {
		return isComplete() ? new ObjectId(w1, w2, w3, w4, w5) : null;
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
	public final int prefixCompare(AnyObjectId other) {
		int cmp;

		cmp = NB.compareUInt32(w1, mask(1, other.w1));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, mask(2, other.w2));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, mask(3, other.w3));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, mask(4, other.w4));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, mask(5, other.w5));
	}

	/**
	 * Compare this abbreviation to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            20 bytes, starting at this position are required.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public final int prefixCompare(byte[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt32(w1, mask(1, NB.decodeInt32(bs, p)));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, mask(2, NB.decodeInt32(bs, p + 4)));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, mask(3, NB.decodeInt32(bs, p + 8)));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, mask(4, NB.decodeInt32(bs, p + 12)));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, mask(5, NB.decodeInt32(bs, p + 16)));
	}

	/**
	 * Compare this abbreviation to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least 5
	 *            ints, starting at this position are required.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public final int prefixCompare(int[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt32(w1, mask(1, bs[p]));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, mask(2, bs[p + 1]));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, mask(3, bs[p + 2]));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, mask(4, bs[p + 3]));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, mask(5, bs[p + 4]));
	}

	/**
	 * Get value for a fan-out style map, only valid of length &gt;= 2.
	 *
	 * @return value for a fan-out style map, only valid of length &gt;= 2.
	 */
	public final int getFirstByte() {
		return w1 >>> 24;
	}

	private int mask(int word, int v) {
		return mask(nibbles, word, v);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return w1;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object o) {
		if (o instanceof AbbreviatedObjectId) {
			final AbbreviatedObjectId b = (AbbreviatedObjectId) o;
			return nibbles == b.nibbles && w1 == b.w1 && w2 == b.w2
					&& w3 == b.w3 && w4 == b.w4 && w5 == b.w5;
		}
		return false;
	}

	/**
	 * Get string form of the abbreviation, in lower case hexadecimal.
	 *
	 * @return string form of the abbreviation, in lower case hexadecimal.
	 */
	public final String name() {
		final char[] b = new char[Constants.OBJECT_ID_STRING_LENGTH];

		AnyObjectId.formatHexChar(b, 0, w1);
		if (nibbles <= 8)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 8, w2);
		if (nibbles <= 16)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 16, w3);
		if (nibbles <= 24)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 24, w4);
		if (nibbles <= 32)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 32, w5);
		return new String(b, 0, nibbles);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AbbreviatedObjectId[" + name() + "]"; //$NON-NLS-1$
	}
}
