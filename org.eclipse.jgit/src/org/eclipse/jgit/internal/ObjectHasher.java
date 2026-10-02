/*
 * Copyright (C) 2026, Eclipse Foundation, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal;

import java.security.MessageDigest;

import org.eclipse.jgit.lib.ObjectFormat;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.sha1.SHA1;

/**
 * Computes the object id of a Git object using the hash function of a given
 * {@link ObjectFormat}.
 * <p>
 * For {@link ObjectFormat#SHA_1} this uses JGit's collision detecting
 * {@link SHA1} implementation, other formats use a plain
 * {@link MessageDigest}.
 *
 * @since 7.9
 */
public final class ObjectHasher {

	/**
	 * Create a hasher for the given object format.
	 *
	 * @param format
	 *            object format whose hash function should be used.
	 * @return a new hasher instance.
	 */
	public static ObjectHasher forFormat(ObjectFormat format) {
		return new ObjectHasher(format);
	}

	private final ObjectFormat format;

	private final SHA1 sha1;

	private final MessageDigest md;

	private ObjectHasher(ObjectFormat format) {
		this.format = format;
		if (format == ObjectFormat.SHA_1) {
			sha1 = SHA1.newInstance();
			md = null;
		} else {
			sha1 = null;
			md = format.newMessageDigest();
		}
	}

	/**
	 * Get the object format this hasher computes object ids for.
	 *
	 * @return the object format of this hasher.
	 */
	public ObjectFormat getObjectFormat() {
		return format;
	}

	/**
	 * Update the hash with a single byte.
	 *
	 * @param b
	 *            the byte to hash.
	 */
	public void update(byte b) {
		if (sha1 != null) {
			sha1.update(b);
		} else {
			md.update(b);
		}
	}

	/**
	 * Update the hash with the contents of the given array.
	 *
	 * @param in
	 *            the bytes to hash.
	 */
	public void update(byte[] in) {
		if (sha1 != null) {
			sha1.update(in);
		} else {
			md.update(in);
		}
	}

	/**
	 * Update the hash with a slice of the given array.
	 *
	 * @param in
	 *            the array containing the bytes to hash.
	 * @param p
	 *            position of the first byte to hash.
	 * @param len
	 *            number of bytes to hash.
	 */
	public void update(byte[] in, int p, int len) {
		if (sha1 != null) {
			sha1.update(in, p, len);
		} else {
			md.update(in, p, len);
		}
	}

	/**
	 * Complete the hash computation and return the object id.
	 *
	 * @return the computed object id.
	 */
	public ObjectId toObjectId() {
		if (sha1 != null) {
			return sha1.toObjectId();
		}
		byte[] raw = md.digest();
		return ObjectId.fromRaw(raw, 0, raw.length);
	}

	/**
	 * Complete the hash computation and return the raw hash bytes.
	 *
	 * @return the raw hash bytes.
	 * @since 7.9
	 */
	public byte[] digest() {
		if (sha1 != null) {
			return sha1.digest();
		}
		return md.digest();
	}
}
