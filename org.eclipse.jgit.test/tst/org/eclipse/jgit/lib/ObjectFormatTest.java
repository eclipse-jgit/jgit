/*
 * Copyright (C) 2026, Eclipse Foundation, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.security.MessageDigest;

import org.eclipse.jgit.util.Hex;
import org.junit.Test;

public class ObjectFormatTest {

	@Test
	public void testSha1() {
		assertEquals("sha1", ObjectFormat.SHA_1.getConfigName());
		assertEquals("SHA-1", ObjectFormat.SHA_1.getJcaName());
		assertEquals(20, ObjectFormat.SHA_1.getLength());
		assertEquals(40, ObjectFormat.SHA_1.getHexLength());
	}

	@Test
	public void testSha256() {
		assertEquals("sha256", ObjectFormat.SHA_256.getConfigName());
		assertEquals("SHA-256", ObjectFormat.SHA_256.getJcaName());
		assertEquals(32, ObjectFormat.SHA_256.getLength());
		assertEquals(64, ObjectFormat.SHA_256.getHexLength());
	}

	@Test
	public void testFindByConfigName() {
		assertSame(ObjectFormat.SHA_1, ObjectFormat.findByConfigName("sha1"));
		assertSame(ObjectFormat.SHA_256,
				ObjectFormat.findByConfigName("sha256"));
		// config values are case-insensitive
		assertSame(ObjectFormat.SHA_256,
				ObjectFormat.findByConfigName("SHA256"));
		assertNull(ObjectFormat.findByConfigName("md5"));
		assertNull(ObjectFormat.findByConfigName(""));
	}

	@Test
	public void testEmptyTreeAndBlobIds() {
		assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904",
				ObjectFormat.SHA_1.getEmptyTreeId().name());
		assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
				ObjectFormat.SHA_1.getEmptyBlobId().name());

		// Verify the SHA-256 empty ids by independent computation.
		assertEquals(hash(ObjectFormat.SHA_256, Constants.OBJ_TREE,
				new byte[0]), ObjectFormat.SHA_256.getEmptyTreeId());
		assertEquals(hash(ObjectFormat.SHA_256, Constants.OBJ_BLOB,
				new byte[0]), ObjectFormat.SHA_256.getEmptyBlobId());
	}

	private static ObjectId hash(ObjectFormat format, int type, byte[] data) {
		MessageDigest md = format.newMessageDigest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(data.length));
		md.update((byte) 0);
		md.update(data);
		byte[] raw = md.digest();
		return ObjectId.fromRaw(raw, 0, raw.length);
	}

	@Test
	public void testNewMessageDigest() {
		MessageDigest sha1 = ObjectFormat.SHA_1.newMessageDigest();
		assertEquals(20, sha1.getDigestLength());

		MessageDigest sha256 = ObjectFormat.SHA_256.newMessageDigest();
		assertEquals(32, sha256.getDigestLength());
		// SHA-256("abc") from FIPS 180-4
		byte[] hash = sha256.digest(new byte[] { 'a', 'b', 'c' });
		assertEquals(
				"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", //$NON-NLS-1$
				Hex.toHexString(hash));
	}
}
