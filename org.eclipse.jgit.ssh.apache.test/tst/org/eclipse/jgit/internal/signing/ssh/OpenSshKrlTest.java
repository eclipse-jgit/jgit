/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link OpenSshKrl} using binary KRLs.
 */
@RunWith(Parameterized.class)
public class OpenSshKrlTest {

	// The test data was generated using the public domain OpenSSH test script
	// with some minor modifications (une ed25519 always, generate a "includes
	// everything KRl, name the unrekoked keys "unrevoked*", generate a plain
	// text KRL, and don't run the ssh-keygen tests). The original script is
	// available at
	// https://github.com/openssh/openssh-portable/blob/67a115e/regress/krl.sh

	private static final String[] KRLS = {
			"krl-empty", "krl-keys", "krl-all",
			"krl-sha1", "krl-sha256", "krl-hash",
			"krl-serial", "krl-keyid", "krl-cert", "krl-ca",
			"krl-serial-wild", "krl-keyid-wild", "krl-text" };

	private static final int[] REVOKED = { 1, 4, 10, 50, 90, 500, 510, 520, 550,
			799, 999 };

	private static final int[] UNREVOKED = { 5, 9, 14, 16, 29, 49, 51, 499, 800,
			1010, 1011 };

	private static class TestData {

		String key;

		String krl;

		Boolean expected;

		TestData(String key, String krl, boolean expected) {
			this.key = key;
			this.krl = krl;
			this.expected = Boolean.valueOf(expected);
		}

		@Override
		public String toString() {
			return key + '-' + krl;
		}
	}

	@Parameters(name = "{0}")
	public static List<TestData> initTestData() {
		List<TestData> tests = new ArrayList<>();
		for (int i = 0; i < REVOKED.length; i++) {
			String key = String.format("revoked-%04d",
					Integer.valueOf(REVOKED[i]));
			for (String krl : KRLS) {
				boolean expected = !krl.endsWith("-empty");
				tests.add(new TestData(key + "-cert.pub", krl, expected));
				expected = krl.endsWith("-keys") || krl.endsWith("-all")
						|| krl.endsWith("-hash") || krl.endsWith("-sha1")
						|| krl.endsWith("-sha256") || krl.endsWith("-text");
				tests.add(new TestData(key + ".pub", krl, expected));
			}
		}
		for (int i = 0; i < UNREVOKED.length; i++) {
			String key = String.format("unrevoked-%04d",
					Integer.valueOf(UNREVOKED[i]));
			for (String krl : KRLS) {
				boolean expected = false;
				tests.add(new TestData(key + ".pub", krl, expected));
				expected = krl.endsWith("-ca");
				tests.add(new TestData(key + "-cert.pub", krl, expected));
			}
		}
		return tests;
	}

	private static Path tmp;

	@BeforeClass
	public static void setUp() throws IOException {
		tmp = Files.createTempDirectory("krls");
		for (String krl : KRLS) {
			copyResource("krl/" + krl, tmp);
		}
	}

	private static void copyResource(String name, Path directory)
			throws IOException {
		try (InputStream in = OpenSshKrlTest.class
				.getResourceAsStream(name)) {
			int i = name.lastIndexOf('/');
			String fileName = i < 0 ? name : name.substring(i + 1);
			Files.copy(in, directory.resolve(fileName));
		}
	}

	@AfterClass
	public static void cleanUp() throws Exception {
		FileUtils.delete(tmp.toFile(), FileUtils.RECURSIVE);
	}

	// Injected by JUnit
	@Parameter
	public TestData data;

	@Test
	public void testIsRevoked() throws Exception {
		OpenSshKrl krl = new OpenSshKrl(tmp.resolve(data.krl));
		try (InputStream in = this.getClass()
				.getResourceAsStream("krl/" + data.key)) {
			PublicKey key = AuthorizedKeyEntry.readAuthorizedKeys(in, true)
					.get(0)
					.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
			assertEquals(data.expected, Boolean.valueOf(krl.isRevoked(key)));
		}
	}
}
