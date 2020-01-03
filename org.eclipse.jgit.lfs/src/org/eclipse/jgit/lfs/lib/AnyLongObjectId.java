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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.References;

/**
 * A (possibly mutable) SHA-256 abstraction.
 * <p>
 * If this is an instance of
 * {@link org.eclipse.jgit.lfs.lib.MutableLongObjectId} the concept of equality
 * with this instance can alter at any time, if this instance is modified to
 * represent a different object name.
 *
 * Ported to SHA-256 from {@link org.eclipse.jgit.lib.AnyObjectId}
 *
 * @since 4.3
 */
public abstract class AnyLongObjectId implements Comparable<AnyLongObjectId> {

	/**
	 * Compare two object identifier byte sequences for equality.
	 *
	 * @param firstObjectId
	 *            the first identifier to compare. Must not be null.
	 * @param secondObjectId
	 *            the second identifier to compare. Must not be null.
	 * @return true if the two identifiers are the same.
	 * @deprecated use {@link #isEqual(AnyLongObjectId, AnyLongObjectId)}
	 *             instead.
	 */
	@Deprecated
	@SuppressWarnings("AmbiguousMethodReference")
	public static boolean equals(final AnyLongObjectId firstObjectId,
			final AnyLongObjectId secondObjectId) {
		return isEqual(firstObjectId, secondObjectId);
	}

	/**
	 * Compare two object identifier byte sequences for equality.
	 *
	 * @param firstObjectId
	 *            the first identifier to compare. Must not be null.
	 * @param secondObjectId
	 *            the second identifier to compare. Must not be null.
	 * @return true if the two identifiers are the same.
	 * @since 5.4
	 */
	public static boolean isEqual(final AnyLongObjectId firstObjectId,
			final AnyLongObjectId secondObjectId) {
		if (References.isSameObject(firstObjectId, secondObjectId)) {
			return true;
		}

		// We test word 2 first as odds are someone already used our
		// word 1 as a hash code, and applying that came up with these
		// two instances we are comparing for equality. Therefore the
		// first two words are very likely to be identical. We want to
		// break away from collisions as quickly as possible.
		//
		return firstObjectId.w2 == secondObjectId.w2
				&& firstObjectId.w3 == secondObjectId.w3
				&& firstObjectId.w4 == secondObjectId.w4
				&& firstObjectId.w1 == secondObjectId.w1;
	}

	long w1;

	long w2;

	long w3;

	long w4;

	/**
	 * Get the first 8 bits of the LongObjectId.
	 *
	 * This is a faster version of {@code getByte(0)}.
	 *
	 * @return a discriminator usable for a fan-out style map. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 */
	public final int getFirstByte() {
		return (int) (w1 >>> 56);
	}

	/**
	 * Get the second 8 bits of the LongObjectId.
	 *
	 * @return a discriminator usable for a fan-out style map. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 */
	public final int getSecondByte() {
		return (int) ((w1 >>> 48) & 0xff);
	}

	/**
	 * Get any byte from the LongObjectId.
	 *
	 * Callers hard-coding {@code getByte(0)} should instead use the much faster
	 * special case variant {@link #getFirstByte()}.
	 *
	 * @param index
	 *            index of the byte to obtain from the raw form of the
	 *            LongObjectId. Must be in range [0,
	 *            {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH}).
	 * @return the value of the requested byte at {@code index}. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to
	 *             {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH},
	 *             or greater than
	 *             {@link org.eclipse.jgit.lfs.lib.Constants#LONG_OBJECT_ID_LENGTH}.
	 */
	public final int getByte(int index) {
		long w;
		switch (index >> 3) {
		case 0:
			w = w1;
			break;
		case 1:
			w = w2;
			break;
		case 2:
			w = w3;
			break;
		case 3:
			w = w4;
			break;
		default:
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return (int) ((w >>> (8 * (15 - (index & 15)))) & 0xff);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Compare this LongObjectId to another and obtain a sort ordering.
	 */
	@Override
	public final int compareTo(AnyLongObjectId other) {
		if (this == other)
			return 0;

		int cmp;

		cmp = NB.compareUInt64(w1, other.w1);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, other.w2);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, other.w3);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, other.w4);
	}

	/**
	 * Compare this LongObjectId to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            32 bytes, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(byte[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, NB.decodeInt64(bs, p));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, NB.decodeInt64(bs, p + 8));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, NB.decodeInt64(bs, p + 16));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, NB.decodeInt64(bs, p + 24));
	}

	/**
	 * Compare this LongObjectId to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least 4
	 *            longs, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(long[] bs, int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, bs[p]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, bs[p + 1]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, bs[p + 2]);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, bs[p + 3]);
	}

	/**
	 * Tests if this LongObjectId starts with the given abbreviation.
	 *
	 * @param abbr
	 *            the abbreviation.
	 * @return true if this LongObjectId begins with the abbreviation; else
	 *         false.
	 */
	public boolean startsWith(AbbreviatedLongObjectId abbr) {
		return abbr.prefixCompare(this) == 0;
	}

	/** {@inheritDoc} */
	@Override
	public final int hashCode() {
		return (int) (w1 >> 32);
	}

	/**
	 * Determine if this LongObjectId has exactly the same value as another.
	 *
	 * @param other
	 *            the other id to compare to. May be null.
	 * @return true only if both LongObjectIds have identical bits.
	 */
	@SuppressWarnings({ "NonOverridingEquals", "AmbiguousMethodReference" })
	public final boolean equals(AnyLongObjectId other) {
		return other != null ? equals(this, other) : false;
	}

	/** {@inheritDoc} */
	@Override
	public final boolean equals(Object o) {
		if (o instanceof AnyLongObjectId) {
			return equals((AnyLongObjectId) o);
		}
		return false;
	}

	/**
	 * Copy this LongObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the buffer to copy to. Must be in big endian order.
	 */
	public void copyRawTo(ByteBuffer w) {
		w.putLong(w1);
		w.putLong(w2);
		w.putLong(w3);
		w.putLong(w4);
	}

	/**
	 * Copy this LongObjectId to a byte array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(byte[] b, int o) {
		NB.encodeInt64(b, o, w1);
		NB.encodeInt64(b, o + 8, w2);
		NB.encodeInt64(b, o + 16, w3);
		NB.encodeInt64(b, o + 24, w4);
	}

	/**
	 * Copy this LongObjectId to an long array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(long[] b, int o) {
		b[o] = w1;
		b[o + 1] = w2;
		b[o + 2] = w3;
		b[o + 3] = w4;
	}

	/**
	 * Copy this LongObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the stream to write to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyRawTo(OutputStream w) throws IOException {
		writeRawLong(w, w1);
		writeRawLong(w, w2);
		writeRawLong(w, w3);
		writeRawLong(w, w4);
	}

	private static void writeRawLong(OutputStream w, long v)
			throws IOException {
		w.write((int) (v >>> 56));
		w.write((int) (v >>> 48));
		w.write((int) (v >>> 40));
		w.write((int) (v >>> 32));
		w.write((int) (v >>> 24));
		w.write((int) (v >>> 16));
		w.write((int) (v >>> 8));
		w.write((int) v);
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(OutputStream w) throws IOException {
		w.write(toHexByteArray());
	}

	/**
	 * Copy this LongObjectId to a byte array in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyTo(byte[] b, int o) {
		formatHexByte(b, o + 0, w1);
		formatHexByte(b, o + 16, w2);
		formatHexByte(b, o + 32, w3);
		formatHexByte(b, o + 48, w4);
	}

	/**
	 * Copy this LongObjectId to a ByteBuffer in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 */
	public void copyTo(ByteBuffer b) {
		b.put(toHexByteArray());
	}

	private byte[] toHexByteArray() {
		final byte[] dst = new byte[Constants.LONG_OBJECT_ID_STRING_LENGTH];
		formatHexByte(dst, 0, w1);
		formatHexByte(dst, 16, w2);
		formatHexByte(dst, 32, w3);
		formatHexByte(dst, 48, w4);
		return dst;
	}

	private static final byte[] hexbyte = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static void formatHexByte(byte[] dst, int p, long w) {
		int o = p + 15;
		while (o >= p && w != 0) {
			dst[o--] = hexbyte[(int) (w & 0xf)];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(Writer w) throws IOException {
		w.write(toHexCharArray());
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (64 characters or larger).
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(char[] tmp, Writer w) throws IOException {
		toHexCharArray(tmp);
		w.write(tmp, 0, Constants.LONG_OBJECT_ID_STRING_LENGTH);
	}

	/**
	 * Copy this LongObjectId to a StringBuilder in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (64 characters or larger).
	 * @param w
	 *            the string to append onto.
	 */
	public void copyTo(char[] tmp, StringBuilder w) {
		toHexCharArray(tmp);
		w.append(tmp, 0, Constants.LONG_OBJECT_ID_STRING_LENGTH);
	}

	char[] toHexCharArray() {
		final char[] dst = new char[Constants.LONG_OBJECT_ID_STRING_LENGTH];
		toHexCharArray(dst);
		return dst;
	}

	private void toHexCharArray(char[] dst) {
		formatHexChar(dst, 0, w1);
		formatHexChar(dst, 16, w2);
		formatHexChar(dst, 32, w3);
		formatHexChar(dst, 48, w4);
	}

	private static final char[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexChar(char[] dst, int p, long w) {
		int o = p + 15;
		while (o >= p && w != 0) {
			dst[o--] = hexchar[(int) (w & 0xf)];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AnyLongObjectId[" + name() + "]";
	}

	/**
	 * Get string form of the SHA-256
	 *
	 * @return string form of the SHA-256, in lower case hexadecimal.
	 */
	public final String name() {
		return new String(toHexCharArray());
	}

	/**
	 * Get string form of the SHA-256
	 *
	 * @return string form of the SHA-256, in lower case hexadecimal.
	 */
	public final String getName() {
		return name();
	}

	/**
	 * Return an abbreviation (prefix) of this object SHA-256.
	 * <p>
	 * This implementation does not guarantee uniqueness. Callers should instead
	 * use
	 * {@link org.eclipse.jgit.lib.ObjectReader#abbreviate(AnyObjectId, int)} to
	 * obtain a unique abbreviation within the scope of a particular object
	 * database.
	 *
	 * @param len
	 *            length of the abbreviated string.
	 * @return SHA-256 abbreviation.
	 */
	public AbbreviatedLongObjectId abbreviate(int len) {
		final long a = AbbreviatedLongObjectId.mask(len, 1, w1);
		final long b = AbbreviatedLongObjectId.mask(len, 2, w2);
		final long c = AbbreviatedLongObjectId.mask(len, 3, w3);
		final long d = AbbreviatedLongObjectId.mask(len, 4, w4);
		return new AbbreviatedLongObjectId(len, a, b, c, d);
	}

	/**
	 * Obtain an immutable copy of this current object.
	 * <p>
	 * Only returns <code>this</code> if this instance is an unsubclassed
	 * instance of {@link org.eclipse.jgit.lfs.lib.LongObjectId}; otherwise a
	 * new instance is returned holding the same value.
	 * <p>
	 * This method is useful to shed any additional memory that may be tied to
	 * the subclass, yet retain the unique identity of the object id for future
	 * lookups within maps and repositories.
	 *
	 * @return an immutable copy, using the smallest memory footprint possible.
	 */
	public final LongObjectId copy() {
		if (getClass() == LongObjectId.class)
			return (LongObjectId) this;
		return new LongObjectId(this);
	}

	/**
	 * Obtain an immutable copy of this current object.
	 * <p>
	 * See {@link #copy()} if <code>this</code> is a possibly subclassed (but
	 * immutable) identity and the application needs a lightweight identity
	 * <i>only</i> reference.
	 *
	 * @return an immutable copy. May be <code>this</code> if this is already an
	 *         immutable instance.
	 */
	public abstract LongObjectId toObjectId();
}
