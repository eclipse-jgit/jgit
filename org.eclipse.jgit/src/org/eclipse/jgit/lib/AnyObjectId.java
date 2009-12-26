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
 * If this is an instance of {@link MutableObjectId} the concept of equality
 * with this instance can alter at any time, if this instance is modified to
 * represent a different object name.
 */
public abstract class AnyObjectId implements Comparable {

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

		// We test word 2 first as odds are someone already used our
		// word 1 as a hash code, and applying that came up with these
		// two instances we are comparing for equality. Therefore the
		// first two words are very likely to be identical. We want to
		// break away from collisions as quickly as possible.
		//
		return firstObjectId.w2 == secondObjectId.w2
				&& firstObjectId.w3 == secondObjectId.w3
				&& firstObjectId.w4 == secondObjectId.w4
				&& firstObjectId.w5 == secondObjectId.w5
				&& firstObjectId.w1 == secondObjectId.w1;
	}

	int w1;

	int w2;

	int w3;

	int w4;

	int w5;

	/**
	 * For ObjectIdMap
	 *
	 * @return a discriminator usable for a fan-out style map
	 */
	public final int getFirstByte() {
		return w1 >>> 24;
	}

	/**
	 * Compare this ObjectId to another and obtain a sort ordering.
	 *
	 * @param other
	 *            the other id to compare to. Must not be null.
	 * @return < 0 if this id comes before other; 0 if this id is equal to
	 *         other; > 0 if this id comes after other.
	 */
	public int compareTo(final ObjectId other) {
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

	public int compareTo(final Object other) {
		return compareTo(((ObjectId) other));
	}

	int compareTo(final byte[] bs, final int p) {
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

	int compareTo(final int[] bs, final int p) {
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

	public int hashCode() {
		return w2;
	}

	/**
	 * Determine if this ObjectId has exactly the same value as another.
	 *
	 * @param other
	 *            the other id to compare to. May be null.
	 * @return true only if both ObjectIds have identical bits.
	 */
	public boolean equals(final AnyObjectId other) {
		return other != null ? equals(this, other) : false;
	}

	public boolean equals(final Object o) {
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
	 * @throws IOException
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
	 * @throws IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final OutputStream w) throws IOException {
		w.write(toHexByteArray());
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
	 * @throws IOException
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
	 * @throws IOException
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

	@Override
	public String toString() {
		return "AnyObjectId[" + name() + "]";
	}

	/**
	 * @return string form of the SHA-1, in lower case hexadecimal.
	 */
	public final String name() {
		return new String(toHexCharArray());
	}

	/**
	 * @return string form of the SHA-1, in lower case hexadecimal.
	 */
	public final String getName() {
		return name();
	}

	/**
	 * Return unique abbreviation (prefix) of this object SHA-1.
	 * <p>
	 * This method is a utility for <code>abbreviate(repo, 8)</code>.
	 *
	 * @param repo
	 *            repository for checking uniqueness within.
	 * @return SHA-1 abbreviation.
	 */
	public AbbreviatedObjectId abbreviate(final Repository repo) {
		return abbreviate(repo, 8);
	}

	/**
	 * Return unique abbreviation (prefix) of this object SHA-1.
	 * <p>
	 * Current implementation is not guaranteeing uniqueness, it just returns
	 * fixed-length prefix of SHA-1 string.
	 *
	 * @param repo
	 *            repository for checking uniqueness within.
	 * @param len
	 *            minimum length of the abbreviated string.
	 * @return SHA-1 abbreviation.
	 */
	public AbbreviatedObjectId abbreviate(final Repository repo, final int len) {
		// TODO implement checking for uniqueness
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
	 * instance of {@link ObjectId}; otherwise a new instance is returned
	 * holding the same value.
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
