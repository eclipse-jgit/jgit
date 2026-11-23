/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import org.eclipse.jgit.util.Hex;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.sha1.Sha1CollisionException;
import org.junit.Test;

/**
 * {@link ObjectFormat#newObjectDigest()}: object names in SHA-1 repositories
 * keep JGit's collision detection (SHA-1DC); SHA-256 uses the plain digest.
 */
public class ObjectFormatObjectDigestTest {
	private static final byte[] EMPTY_BLOB = "blob 0\0".getBytes(UTF_8);

	private static byte[] shattered1() throws IOException {
		try (InputStream in = ObjectFormatObjectDigestTest.class
				.getResourceAsStream(
						"/org/eclipse/jgit/util/sha1/shattered-1.pdf")) {
			ByteBuffer buf = IO.readWholeStream(in, 422435);
			byte[] r = new byte[buf.remaining()];
			buf.get(r);
			return r;
		}
	}

	private static void assumeCollisionDetection() {
		assumeFalse("jdkNative".equalsIgnoreCase(System
				.getProperty("org.eclipse.jgit.util.sha1.implementation")));
		assumeTrue(System.getProperty(
				"org.eclipse.jgit.util.sha1.detectCollision") == null);
		assumeTrue(System
				.getProperty("org.eclipse.jgit.util.sha1.safeHash") == null);
	}

	@Test
	public void sha1ObjectDigestDetectsShattered() throws IOException {
		assumeCollisionDetection();
		MessageDigest md = ObjectFormat.SHA1.newObjectDigest();
		md.update(shattered1());
		assertThrows(Sha1CollisionException.class, md::digest);
	}

	@Test
	public void sha1ObjectDigestMatchesPlainSha1() {
		MessageDigest md = ObjectFormat.SHA1.newObjectDigest();
		assertEquals(ObjectFormat.SHA1.getNumBytes(), md.getDigestLength());
		byte[] id = md.digest(EMPTY_BLOB);
		assertArrayEquals(
				ObjectFormat.SHA1.newMessageDigest().digest(EMPTY_BLOB), id);
		assertEquals(ObjectFormat.SHA1.getEmptyBlobId(), ObjectId.fromRaw(id));
		// digest() resets, so the same instance can name the next object.
		assertArrayEquals(id, md.digest(EMPTY_BLOB));
	}

	@Test
	public void sha256ObjectDigestIsPlainSha256() throws IOException {
		byte[] pdf = shattered1();
		MessageDigest md = ObjectFormat.SHA256.newObjectDigest();
		assertEquals(ObjectFormat.SHA256.getNumBytes(), md.getDigestLength());
		assertArrayEquals(ObjectFormat.SHA256.newMessageDigest().digest(pdf),
				md.digest(pdf));
		assertEquals(
				"473a0f4c3be8a93681a267e3b1e9a7dcda1185436fe141f7749120a303721813",
				Hex.toHexString(md.digest(EMPTY_BLOB)));
	}
}
