/*
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.eclipse.jgit.util.NB;

/**
 * A (possibly mutable) SHA-1 abstraction.
 * <p>
 * If this is an instance of {@link org.eclipse.jgit.lib.MutableObjectId} the
 * concept of equality with this instance can alter at any time, if this
 * instance is modified to represent a different object name.
 */
public abstract class AnyObjectId implements Comparable<AnyObjectId> {

	/**
	 * Compare to object identifier byte sequences for equality.
	 *
	 * @param firstObjectId
	 *            the first identifier to compare. Must not be null.
	 * @param secondObjectId
	 *            the second identifier to compare. Must not be null.
	 * @return true if the two identifiers are the same.
	 */
	public static boolean equals(final AnyObjectId firstObjectId,
			final AnyObjectId secondObjectId) {
		if (firstObjectId == secondObjectId)
			return true;

		// We test word 3 first since the git file-based ODB
		// uses the first byte of w1, and we use w2 as the
		// hash code, one of those probably came up with these
		// two instances which we are comparing for equality.
		// Therefore the first two words are very likely to be
		// identical. We want to break away from collisions as
		// quickly as possible.
		//
		return firstObjectId.w3 == secondObjectId.w3
				&& firstObjectId.w4 == secondObjectId.w4
				&& firstObjectId.w5 == secondObjectId.w5
				&& firstObjectId.w1 == secondObjectId.w1
				&& firstObjectId.w2 == secondObjectId.w2;
	}

	int w1;

	int w2;

	int w3;

	int w4;

	int w5;

	/**
	 * Get the first 8 bits of the ObjectId.
	 *
	 * This is a faster version of {@code getByte(0)}.
	 *
	 * @return a discriminator usable for a fan-out style map. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 */
	public final int getFirstByte() {
		return w1 >>> 24;
	}

	/**
	 * Get any byte from the ObjectId.
	 *
	 * Callers hard-coding {@code getByte(0)} should instead use the much faster
	 * special case variant {@link #getFirstByte()}.
	 *
	 * @param index
	 *            index of the byte to obtain from the raw form of the ObjectId.
	 *            Must be in range [0,
	 *            {@link org.eclipse.jgit.lib.Constants#OBJECT_ID_LENGTH}).
	 * @return the value of the requested byte at {@code index}. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to
	 *             {@link org.eclipse.jgit.lib.Constants#OBJECT_ID_LENGTH}, or
	 *             greater than
	 *             {@link org.eclipse.jgit.lib.Constants#OBJECT_ID_LENGTH}.
	 */
	public final int getByte(int index) {
		int w;
		switch (index >> 2) {
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
		case 4:
			w = w5;
			break;
		default:
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return (w >>> (8 * (3 - (index & 3)))) & 0xff;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Compare this ObjectId to another and obtain a sort ordering.
	 */
	@Override
	public final int compareTo(final AnyObjectId other) {
		if (this == other)
			return 0;

		int cmp;

		cmp = NB.compareUInt32(w1, other.w1);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, other.w2);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, other.w3);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, other.w4);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, other.w5);
	}

	/**
	 * Compare this ObjectId to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            20 bytes, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(final byte[] bs, final int p) {
		int cmp;

		cmp = NB.compareUInt32(w1, NB.decodeInt32(bs, p));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, NB.decodeInt32(bs, p + 4));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, NB.decodeInt32(bs, p + 8));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, NB.decodeInt32(bs, p + 12));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, NB.decodeInt32(bs, p + 16));
	}

	/**
	 * Compare this ObjectId to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least 5
	 *            integers, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(final int[] bs, final int p) {
		int cmp;

		cmp = NB.compareUInt32(w1, bs[p]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, bs[p + 1]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, bs[p + 2]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, bs[p + 3]);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, bs[p + 4]);
	}

	/**
	 * Tests if this ObjectId starts with the given abbreviation.
	 *
	 * @param abbr
	 *            the abbreviation.
	 * @return true if this ObjectId begins with the abbreviation; else false.
	 */
	public boolean startsWith(final AbbreviatedObjectId abbr) {
		return abbr.prefixCompare(this) == 0;
	}

	/** {@inheritDoc} */
	@Override
	public final int hashCode() {
		return w2;
	}

	/**
	 * Determine if this ObjectId has exactly the same value as another.
	 *
	 * @param other
	 *            the other id to compare to. May be null.
	 * @return true only if both ObjectIds have identical bits.
	 */
	public final boolean equals(final AnyObjectId other) {
		return other != null ? equals(this, other) : false;
	}

	/** {@inheritDoc} */
	@Override
	public final boolean equals(final Object o) {
		if (o instanceof AnyObjectId)
			return equals((AnyObjectId) o);
		else
			return false;
	}

	/**
	 * Copy this ObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the buffer to copy to. Must be in big endian order.
	 */
	public void copyRawTo(final ByteBuffer w) {
		w.putInt(w1);
		w.putInt(w2);
		w.putInt(w3);
		w.putInt(w4);
		w.putInt(w5);
	}

	/**
	 * Copy this ObjectId to a byte array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(final byte[] b, final int o) {
		NB.encodeInt32(b, o, w1);
		NB.encodeInt32(b, o + 4, w2);
		NB.encodeInt32(b, o + 8, w3);
		NB.encodeInt32(b, o + 12, w4);
		NB.encodeInt32(b, o + 16, w5);
	}

	/**
	 * Copy this ObjectId to an int array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(final int[] b, final int o) {
		b[o] = w1;
		b[o + 1] = w2;
		b[o + 2] = w3;
		b[o + 3] = w4;
		b[o + 4] = w5;
	}

	/**
	 * Copy this ObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the stream to write to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyRawTo(final OutputStream w) throws IOException {
		writeRawInt(w, w1);
		writeRawInt(w, w2);
		writeRawInt(w, w3);
		writeRawInt(w, w4);
		writeRawInt(w, w5);
	}

	private static void writeRawInt(final OutputStream w, int v)
			throws IOException {
		w.write(v >>> 24);
		w.write(v >>> 16);
		w.write(v >>> 8);
		w.write(v);
	}

	/**
	 * Copy this ObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final OutputStream w) throws IOException {
		w.write(toHexByteArray());
	}

	/**
	 * Copy this ObjectId to a byte array in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyTo(byte[] b, int o) {
		formatHexByte(b, o + 0, w1);
		formatHexByte(b, o + 8, w2);
		formatHexByte(b, o + 16, w3);
		formatHexByte(b, o + 24, w4);
		formatHexByte(b, o + 32, w5);
	}

	/**
	 * Copy this ObjectId to a ByteBuffer in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 */
	public void copyTo(ByteBuffer b) {
		b.put(toHexByteArray());
	}

	private byte[] toHexByteArray() {
		final byte[] dst = new byte[Constants.OBJECT_ID_STRING_LENGTH];
		formatHexByte(dst, 0, w1);
		formatHexByte(dst, 8, w2);
		formatHexByte(dst, 16, w3);
		formatHexByte(dst, 24, w4);
		formatHexByte(dst, 32, w5);
		return dst;
	}

	private static final byte[] hexbyte = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static void formatHexByte(final byte[] dst, final int p, int w) {
		int o = p + 7;
		while (o >= p && w != 0) {
			dst[o--] = hexbyte[w & 0xf];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	/**
	 * Copy this ObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final Writer w) throws IOException {
		w.write(toHexCharArray());
	}

	/**
	 * Copy this ObjectId to an output writer in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (40 characters or larger).
	 * @param w
	 *            the stream to copy to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final char[] tmp, final Writer w) throws IOException {
		toHexCharArray(tmp);
		w.write(tmp, 0, Constants.OBJECT_ID_STRING_LENGTH);
	}

	/**
	 * Copy this ObjectId to a StringBuilder in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (40 characters or larger).
	 * @param w
	 *            the string to append onto.
	 */
	public void copyTo(final char[] tmp, final StringBuilder w) {
		toHexCharArray(tmp);
		w.append(tmp, 0, Constants.OBJECT_ID_STRING_LENGTH);
	}

	private char[] toHexCharArray() {
		final char[] dst = new char[Constants.OBJECT_ID_STRING_LENGTH];
		toHexCharArray(dst);
		return dst;
	}

	private void toHexCharArray(final char[] dst) {
		formatHexChar(dst, 0, w1);
		formatHexChar(dst, 8, w2);
		formatHexChar(dst, 16, w3);
		formatHexChar(dst, 24, w4);
		formatHexChar(dst, 32, w5);
	}

	private static final char[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexChar(final char[] dst, final int p, int w) {
		int o = p + 7;
		while (o >= p && w != 0) {
			dst[o--] = hexchar[w & 0xf];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AnyObjectId[" + name() + "]";
	}

	/**
	 * <p>name.</p>
	 *
	 * @return string form of the SHA-1, in lower case hexadecimal.
	 */
	public final String name() {
		return new String(toHexCharArray());
	}

	/**
	 * Get string form of the SHA-1, in lower case hexadecimal.
	 *
	 * @return string form of the SHA-1, in lower case hexadecimal.
	 */
	public final String getName() {
		return name();
	}

	/**
	 * Return an abbreviation (prefix) of this object SHA-1.
	 * <p>
	 * This implementation does not guarantee uniqueness. Callers should instead
	 * use
	 * {@link org.eclipse.jgit.lib.ObjectReader#abbreviate(AnyObjectId, int)} to
	 * obtain a unique abbreviation within the scope of a particular object
	 * database.
	 *
	 * @param len
	 *            length of the abbreviated string.
	 * @return SHA-1 abbreviation.
	 */
	public AbbreviatedObjectId abbreviate(final int len) {
		final int a = AbbreviatedObjectId.mask(len, 1, w1);
		final int b = AbbreviatedObjectId.mask(len, 2, w2);
		final int c = AbbreviatedObjectId.mask(len, 3, w3);
		final int d = AbbreviatedObjectId.mask(len, 4, w4);
		final int e = AbbreviatedObjectId.mask(len, 5, w5);
		return new AbbreviatedObjectId(len, a, b, c, d, e);
	}

	/**
	 * Obtain an immutable copy of this current object name value.
	 * <p>
	 * Only returns <code>this</code> if this instance is an unsubclassed
	 * instance of {@link org.eclipse.jgit.lib.ObjectId}; otherwise a new
	 * instance is returned holding the same value.
	 * <p>
	 * This method is useful to shed any additional memory that may be tied to
	 * the subclass, yet retain the unique identity of the object id for future
	 * lookups within maps and repositories.
	 *
	 * @return an immutable copy, using the smallest memory footprint possible.
	 */
	public final ObjectId copy() {
		if (getClass() == ObjectId.class)
			return (ObjectId) this;
		return new ObjectId(this);
	}

	/**
	 * Obtain an immutable copy of this current object name value.
	 * <p>
	 * See {@link #copy()} if <code>this</code> is a possibly subclassed (but
	 * immutable) identity and the application needs a lightweight identity
	 * <i>only</i> reference.
	 *
	 * @return an immutable copy. May be <code>this</code> if this is already
	 *         an immutable instance.
	 */
	public abstract ObjectId toObjectId();
}
