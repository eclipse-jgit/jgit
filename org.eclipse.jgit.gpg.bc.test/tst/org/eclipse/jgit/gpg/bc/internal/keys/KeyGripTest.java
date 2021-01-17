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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Consumer;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.util.encoders.Hex;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeyGripTest {

	@BeforeClass
	public static void ensureBC() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	protected static class TestData {

		String filename;

		String[] expectedKeyGrips;

		TestData(String filename, String... keyGrips) {
			this.filename = filename;
			this.expectedKeyGrips = keyGrips;
		}

		@Override
		public String toString() {
			return filename;
		}
	}

	@Parameters(name = "{0}")
	public static TestData[] initTestData() {
		return new TestData[] {
				new TestData("rsa.asc",
						"D148210FAF36468055B83D0F5A6DEB83FBC8E864",
						"A5E4CD2CBBE44A16E4D6EC05C2E3C3A599DC763C"),
				new TestData("dsa-elgamal.asc",
						"552286BEB2999F0A9E26A50385B90D9724001187",
						"CED7034A8EB5F4CE90DF99147EC33D86FCD3296C"),
				new TestData("brainpool256.asc",
						"A01BAA22A72F09A0FF0A1D4CBCE70844DD52DDD7",
						"C1678B7DE5F144C93B89468D5F9764ACE182ED36"),
				new TestData("brainpool384.asc",
						"2F25DB025DEBF3EA2715350209B985829B04F50A",
						"B6BD8B81F75AF914163D97DF8DE8F6FC64C283F8"),
				new TestData("brainpool512.asc",
						"5A484F56AB4B8B6583B6365034999F6543FAE1AE",
						"9133E4A7E8FC8515518DF444C3F2F247EEBBADEC"),
				new TestData("nistp256.asc",
						"FC81AECE90BCE6E54D0D637D266109783AC8DAC0",
						"A56DC8DB8355747A809037459B4258B8A743EAB5"),
				new TestData("nistp384.asc",
						"A1338230AED1C9C125663518470B49056C9D1733",
						"797A83FE041FFE06A7F4B1D32C6F4AE0F6D87ADF"),
				new TestData("nistp521.asc",
						"D91B789603EC9138AA20342A2B6DC86C81B70F5D",
						"FD048B2CA1919CB241DC8A2C7FA3E742EF343DCA"),
				new TestData("secp256k1.asc",
						"498B89C485489BA16B40755C0EBA580166393074",
						"48FFED40D018747363BDEFFDD404D1F4870F8064"),
				new TestData("ed25519.asc",
						"940D97D75C306D737A59A98EAFF1272832CEDC0B"),
				new TestData("x25519.asc",
						"A77DC8173DA6BEE126F5BD6F5A14E01200B52FCE",
						"636C983EDB558527BA82780B52CB5DAE011BE46B")
		};
	}

	// Injected by JUnit
	@Parameter
	public TestData data;

	private void readAsc(InputStream in, Consumer<PGPPublicKey> process)
			throws IOException, PGPException {
		PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
			PGPUtil.getDecoderStream(in), new JcaKeyFingerprintCalculator());

		Iterator<PGPPublicKeyRing> keyRings = pgpPub.getKeyRings();
		while (keyRings.hasNext()) {
			PGPPublicKeyRing keyRing = keyRings.next();

			Iterator<PGPPublicKey> keys = keyRing.getPublicKeys();
			while (keys.hasNext()) {
				process.accept(keys.next());
			}
		}
	}

	@Test
	public void testGrip() throws Exception {
		try (InputStream in = this.getClass()
				.getResourceAsStream(data.filename)) {
			int index[] = { 0 };
			readAsc(in, key -> {
				byte[] keyGrip = null;
				try {
					keyGrip = KeyGrip.getKeyGrip(key);
				} catch (PGPException e) {
					throw new RuntimeException(e);
				}
				assertTrue("More keys than expected",
						index[0] < data.expectedKeyGrips.length);
				assertEquals("Wrong keygrip", data.expectedKeyGrips[index[0]++],
						Hex.toHexString(keyGrip).toUpperCase(Locale.ROOT));
			});
			assertEquals("Missing keys", data.expectedKeyGrips.length,
					index[0]);
		}
	}
}
