/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Locale;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.util.sha1.SHA1;
import org.junit.Test;

public class KeyGrip25519Test {

	interface Hash {
		byte[] hash(SHA1 sha, BigInteger q) throws PGPException;
	}

	private void assertKeyGrip(String key, String expectedKeyGrip, Hash hash)
			throws Exception {
		SHA1 grip = SHA1.newInstance();
		grip.setDetectCollision(false);
		BigInteger pk = new BigInteger(key, 16);
		byte[] keyGrip = hash.hash(grip, pk);
		assertEquals("Keygrip should match", expectedKeyGrip,
				Hex.toHexString(keyGrip).toUpperCase(Locale.ROOT));
	}

	@Test
	public void testCompressed() throws Exception {
		assertKeyGrip("40"
				+ "773E72848C1FD5F9652B29E2E7AF79571A04990E96F2016BF4E0EC1890C2B7DB",
				"9DB6C64A38830F4960701789475520BE8C821F47",
				KeyGrip::hashEd25519);
	}

	@Test
	public void testCompressedNoPrefix() throws Exception {
		assertKeyGrip(
				"773E72848C1FD5F9652B29E2E7AF79571A04990E96F2016BF4E0EC1890C2B7DB",
				"9DB6C64A38830F4960701789475520BE8C821F47",
				KeyGrip::hashEd25519);
	}

	@Test
	public void testCurve25519() throws Exception {
		assertKeyGrip("40"
				+ "918C1733127F6BF2646FAE3D081A18AE77111C903B906310B077505EFFF12740",
				"0F89A565D3EA187CE839332398F5D480677DF49C",
				KeyGrip::hashCurve25519);
	}
}
