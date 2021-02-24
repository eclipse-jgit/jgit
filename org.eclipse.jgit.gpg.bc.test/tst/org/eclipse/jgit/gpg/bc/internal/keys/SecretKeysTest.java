/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Iterator;

import javax.crypto.Cipher;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SecretKeysTest {

	@BeforeClass
	public static void ensureBC() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	private static volatile Boolean haveOCB;

	private static boolean ocbAvailable() {
		Boolean haveIt = haveOCB;
		if (haveIt != null) {
			return haveIt.booleanValue();
		}
		try {
			Cipher c = Cipher.getInstance("AES/OCB/NoPadding"); //$NON-NLS-1$
			if (c == null) {
				haveOCB = Boolean.FALSE;
				return false;
			}
		} catch (NoClassDefFoundError | Exception e) {
			haveOCB = Boolean.FALSE;
			return false;
		}
		haveOCB = Boolean.TRUE;
		return true;
	}

	private static class TestData {

		final String name;

		final boolean encrypted;

		final boolean keyValue;

		TestData(String name, boolean encrypted, boolean keyValue) {
			this.name = name;
			this.encrypted = encrypted;
			this.keyValue = keyValue;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	@Parameters(name = "{0}")
	public static TestData[] initTestData() {
		return new TestData[] {
				new TestData("AFDA8EA10E185ACF8C0D0F8885A0EF61A72ECB11", false, false),
				new TestData("2FB05DBB70FC07CB84C13431F640CA6CEA1DBF8A", false, true),
				new TestData("66CCECEC2AB46A9735B10FEC54EDF9FD0F77BAF9", true, true),
				new TestData("F727FAB884DA3BD402B6E0F5472E108D21033124", true, true),
				new TestData("faked", false, true) };
	}

	private static byte[] readTestKey(String filename) throws Exception {
		try (InputStream in = new BufferedInputStream(
				SecretKeysTest.class.getResourceAsStream(filename))) {
			return SecretKeys.keyFromNameValueFormat(in);
		}
	}

	private static PGPPublicKey readAsc(InputStream in)
			throws IOException, PGPException {
		PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
			PGPUtil.getDecoderStream(in), new JcaKeyFingerprintCalculator());

		Iterator<PGPPublicKeyRing> keyRings = pgpPub.getKeyRings();
		while (keyRings.hasNext()) {
			PGPPublicKeyRing keyRing = keyRings.next();

			Iterator<PGPPublicKey> keys = keyRing.getPublicKeys();
			if (keys.hasNext()) {
				return keys.next();
			}
		}
		return null;
	}

	// Injected by JUnit
	@Parameter
	public TestData data;

	@Test
	public void testKeyRead() throws Exception {
		if (data.keyValue) {
			byte[] bytes = readTestKey(data.name + ".key");
			assertEquals('(', bytes[0]);
			assertEquals(')', bytes[bytes.length - 1]);
		}
		try (InputStream pubIn = this.getClass()
				.getResourceAsStream(data.name + ".asc")) {
			if (pubIn != null) {
				PGPPublicKey publicKey = readAsc(pubIn);
				// Do a full test trying to load the secret key.
				PGPDigestCalculatorProvider calculatorProvider = new JcaPGPDigestCalculatorProviderBuilder()
						.build();
				try (InputStream in = new BufferedInputStream(this.getClass()
						.getResourceAsStream(data.name + ".key"))) {
					PGPSecretKey secretKey = SecretKeys.readSecretKey(in,
							calculatorProvider,
							data.encrypted ? () -> "nonsense".toCharArray()
									: null,
							publicKey);
					assertNotNull(secretKey);
				} catch (PGPException e) {
					// Currently we may not be able to load OCB-encrypted keys.
					assertTrue(e.getMessage().contains("OCB"));
					assertTrue(data.encrypted);
					assertFalse(ocbAvailable());
				}
			}
		}
	}

}
