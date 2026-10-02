/*
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
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.References;

/**
 * A (possibly mutable) object id abstraction.
 * <p>
 * An object id is the hash of a Git object computed with the repository's
 * {@link ObjectFormat}, e.g. SHA-1 (20 bytes) or SHA-256 (32 bytes).
 * <p>
 * If this is an instance of {@link org.eclipse.jgit.lib.MutableObjectId} the
 * concept of equality with this instance can alter at any time, if this
 * instance is modified to represent a different object name.
 */
public abstract class AnyObjectId implements Comparable<AnyObjectId> {

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
	public static boolean isEqual(final AnyObjectId firstObjectId,
			final AnyObjectId secondObjectId) {
		if (References.isSameObject(firstObjectId, secondObjectId)) {
			return true;
		}
		final int[] a = firstObjectId.w;
		final int[] b = secondObjectId.w;
		if (a.length != b.length) {
			return false;
		}
		// We test word 3 first since the git file-based ODB
		// uses the first byte of w[0], and we use w[1] as the
		// hash code, one of those probably came up with these
		// two instances which we are comparing for equality.
		// Therefore the first two words are very likely to be
		// identical. We want to break away from collisions as
		// quickly as possible.
		for (int i = 2; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return a[0] == b[0] && a[1] == b[1];
	}

	int[] w;

	/**
	 * Get the length of this ObjectId in bytes.
	 *
	 * @return length of this ObjectId in bytes, e.g. 20 for SHA-1 and 32 for
	 *         SHA-256.
	 * @since 7.9
	 */
	public final int getLength() {
		return w.length * 4;
	}

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
		return w[0] >>> 24;
	}

	/**
	 * Get any byte from the ObjectId.
	 *
	 * Callers hard-coding {@code getByte(0)} should instead use the much faster
	 * special case variant {@link #getFirstByte()}.
	 *
	 * @param index
	 *            index of the byte to obtain from the raw form of the ObjectId.
	 *            Must be in range [0, {@link #getLength()}).
	 * @return the value of the requested byte at {@code index}. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to {@link #getLength()},
	 *             or greater than {@link #getLength()}.
	 */
	public final int getByte(int index) {
		final int word = index >> 2;
		if (word >= w.length) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		return (w[word] >>> (8 * (3 - (index & 3)))) & 0xff;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Compare this ObjectId to another and obtain a sort ordering.
	 */
	@Override
	public final int compareTo(AnyObjectId other) {
		if (this == other)
			return 0;

		final int common = Math.min(w.length, other.w.length);
		for (int i = 0; i < common; i++) {
			final int cmp = NB.compareUInt32(w[i], other.w[i]);
			if (cmp != 0)
				return cmp;
		}
		return w.length - other.w.length;
	}

	/**
	 * Compare this ObjectId to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            {@link #getLength()} bytes, starting at this position are
	 *            required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(byte[] bs, int p) {
		for (int i = 0; i < w.length; i++) {
			final int cmp = NB.compareUInt32(w[i], NB.decodeInt32(bs, p + 4 * i));
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	/**
	 * Compare this ObjectId to a network-byte-order ObjectId.
	 *
	 * @param bs
	 *            array containing the other ObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            {@code w.length} integers, starting at this position are
	 *            required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(int[] bs, int p) {
		for (int i = 0; i < w.length; i++) {
			final int cmp = NB.compareUInt32(w[i], bs[p + i]);
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	/**
	 * Tests if this ObjectId starts with the given abbreviation.
	 *
	 * @param abbr
	 *            the abbreviation.
	 * @return true if this ObjectId begins with the abbreviation; else false.
	 */
	public boolean startsWith(AbbreviatedObjectId abbr) {
		return abbr.prefixCompare(this) == 0;
	}

	@Override
	public final int hashCode() {
		return w[1];
	}

	/**
	 * Determine if this ObjectId has exactly the same value as another.
	 *
	 * @param other
	 *            the other id to compare to. May be null.
	 * @return true only if both ObjectIds have identical bits.
	 */
	@SuppressWarnings({ "NonOverridingEquals", "AmbiguousMethodReference" })
	public final boolean equals(AnyObjectId other) {
		return other != null ? isEqual(this, other) : false;
	}

	@Override
	public final boolean equals(Object o) {
		if (o instanceof AnyObjectId) {
			return equals((AnyObjectId) o);
		}
		return false;
	}

	/**
	 * Copy this ObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the buffer to copy to. Must be in big endian order.
	 */
	public void copyRawTo(ByteBuffer w) {
		for (int word : this.w) {
			w.putInt(word);
		}
	}

	/**
	 * Copy this ObjectId to a byte array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(byte[] b, int o) {
		for (int i = 0; i < w.length; i++) {
			NB.encodeInt32(b, o + 4 * i, w[i]);
		}
	}

	/**
	 * Copy this ObjectId to an int array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(int[] b, int o) {
		System.arraycopy(w, 0, b, o, w.length);
	}

	/**
	 * Copy this ObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the stream to write to.
	 * @throws java.io.IOException
	 *             the stream writing failed.
	 */
	public void copyRawTo(OutputStream w) throws IOException {
		for (int word : this.w) {
			writeRawInt(w, word);
		}
	}

	private static void writeRawInt(OutputStream w, int v)
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
	public void copyTo(OutputStream w) throws IOException {
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
		for (int i = 0; i < w.length; i++) {
			formatHexByte(b, o + 8 * i, w[i]);
		}
	}

	/**
	 * Copy this ObjectId to a ByteBuffer in hex format.
	 *
	 * @param w
	 *            the buffer to copy to.
	 */
	public void copyTo(ByteBuffer w) {
		w.put(toHexByteArray());
	}

	private byte[] toHexByteArray() {
		final byte[] dst = new byte[w.length * 8];
		copyTo(dst, 0);
		return dst;
	}

	private static final byte[] hexbyte = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexByte(byte[] dst, int p, int w) {
		int o = p + 7;
		while (o >= p && w != 0) {
			dst[o--] = hexbyte[w & 0xf];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = (byte) '0';
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
	public void copyTo(char[] tmp, Writer w) throws IOException {
		toHexCharArray(tmp);
		w.write(tmp, 0, getLength() * 2);
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
	public void copyTo(char[] tmp, StringBuilder w) {
		toHexCharArray(tmp);
		w.append(tmp, 0, getLength() * 2);
	}

	private char[] toHexCharArray() {
		final char[] dst = new char[w.length * 8];
		toHexCharArray(dst);
		return dst;
	}

	private void toHexCharArray(char[] dst) {
		for (int i = 0; i < w.length; i++) {
			formatHexChar(dst, 8 * i, w[i]);
		}
	}

	private static final char[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexChar(char[] dst, int p, int w) {
		int o = p + 7;
		while (o >= p && w != 0) {
			dst[o--] = hexchar[w & 0xf];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AnyObjectId[" + name() + "]";
	}

	/**
	 * <p>name.</p>
	 *
	 * @return string form of the object id, in lower case hexadecimal.
	 */
	public final String name() {
		return new String(toHexCharArray());
	}

	/**
	 * Get string form of the object id, in lower case hexadecimal.
	 *
	 * @return string form of the object id, in lower case hexadecimal.
	 */
	public final String getName() {
		return name();
	}

	/**
	 * Return an abbreviation (prefix) of this object id.
	 * <p>
	 * This implementation does not guarantee uniqueness. Callers should instead
	 * use
	 * {@link org.eclipse.jgit.lib.ObjectReader#abbreviate(AnyObjectId, int)} to
	 * obtain a unique abbreviation within the scope of a particular object
	 * database.
	 *
	 * @param len
	 *            length of the abbreviated string. Must be in range [1, 40];
	 *            abbreviations longer than 40 digits are not supported.
	 * @return object id abbreviation.
	 */
	public AbbreviatedObjectId abbreviate(int len) {
		if (len > 40) {
			throw new IllegalArgumentException(
					"Abbreviations longer than 40 digits are not supported"); //$NON-NLS-1$
		}
		final int a = AbbreviatedObjectId.mask(len, 1, w[0]);
		final int b = AbbreviatedObjectId.mask(len, 2, w[1]);
		final int c = AbbreviatedObjectId.mask(len, 3, w[2]);
		final int d = AbbreviatedObjectId.mask(len, 4, w[3]);
		final int e = AbbreviatedObjectId.mask(len, 5, w[4]);
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
