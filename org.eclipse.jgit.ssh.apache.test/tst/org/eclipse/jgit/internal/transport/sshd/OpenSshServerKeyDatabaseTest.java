/*
 * Copyright (C) 2025 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.certificate.OpenSshCertificateBuilder;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link OpenSshServerKeyDatabase}.
 */
public class OpenSshServerKeyDatabaseTest {

	private static final InetSocketAddress LOCAL = new InetSocketAddress(
			InetAddress.getLoopbackAddress(), SshConstants.DEFAULT_PORT);

	private static final InetSocketAddress LOCAL_29418 = new InetSocketAddress(
			InetAddress.getLoopbackAddress(), 29418);

	private static PublicKey rsa1024;
	private static PublicKey rsa2048;
	private static PublicKey ec256;
	private static PublicKey ec384;
	private static PublicKey caKey;
	private static PublicKey certificate;

	@BeforeClass
	public static void initKeys() throws Exception {
		// Generate a few keys that we can use
		KeyPairGenerator gen = SecurityUtils.getKeyPairGenerator(KeyUtils.RSA_ALGORITHM);
		gen.initialize(1024);
		rsa1024 = gen.generateKeyPair().getPublic();
		gen.initialize(2048);
		rsa2048 = gen.generateKeyPair().getPublic();
		gen = SecurityUtils.getKeyPairGenerator(KeyUtils.EC_ALGORITHM);
		ECCurves curve = ECCurves.fromCurveSize(256);
		gen.initialize(curve.getParameters());
		ec256 = gen.generateKeyPair().getPublic();
		PublicKey certKey = gen.generateKeyPair().getPublic();
		curve = ECCurves.fromCurveSize(384);
		gen.initialize(curve.getParameters());
		ec384 = gen.generateKeyPair().getPublic();
		// Generate a certificate for some key
		gen.initialize(curve.getParameters());
		KeyPair ca = gen.generateKeyPair();
		caKey = ca.getPublic();
		certificate = OpenSshCertificateBuilder
				.hostCertificate()
				.serial(System.currentTimeMillis())
				.publicKey(certKey)
				.id("test-host-cert")
				.validBefore(
						System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
				.principals(List.of("localhost", "127.0.0.1"))
				.sign(ca, "ecdsa-sha2-nistp384");
	}

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private Path knownHosts;
	private Path knownHosts2;
	private ServerKeyDatabase database;

	@Before
	public void setupDatabase() throws Exception {
		Path root = tmp.getRoot().toPath();
		knownHosts = root.resolve("known_hosts");
		knownHosts2 = root.resolve("known_hosts2");
		database = new OpenSshServerKeyDatabase(false, List.of(knownHosts, knownHosts2));
	}

	@Test
	public void testFindInSecondFile() throws Exception {
		Files.write(knownHosts,
				List.of(
						"some.other.host " + PublicKeyEntry.toString(rsa1024),
						"some.other.com " + PublicKeyEntry.toString(ec384)));
		Files.write(knownHosts2,
				List.of(
						"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
						"some.other.com " + PublicKeyEntry.toString(rsa2048)));
		assertTrue(database.accept("localhost", LOCAL, ec256,
				new KnownHostsConfig(), null));
		assertFalse(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testNoFirstFile() throws Exception {
		Files.write(knownHosts2,
				List.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
						"some.other.com " + PublicKeyEntry.toString(rsa2048)));
		assertTrue(database.accept("localhost", LOCAL, ec256,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testFind() throws Exception {
		Files.write(knownHosts,
				List.of("localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(rsa1024),
						"some.other.com " + PublicKeyEntry.toString(ec384)));
		Files.write(knownHosts2,
				List.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
						"some.other.com " + PublicKeyEntry.toString(rsa2048)));
		assertTrue(database.accept("localhost", LOCAL, ec256,
				new KnownHostsConfig(), null));
		assertTrue(database.accept("localhost:22", LOCAL, ec256,
				new KnownHostsConfig(), null));
		assertTrue(database.accept("127.0.0.1", LOCAL, rsa1024,
				new KnownHostsConfig(), null));
		assertTrue(database.accept("[127.0.0.1]:22", LOCAL, rsa1024,
				new KnownHostsConfig(), null));
		assertFalse(database.accept("localhost:29418", LOCAL_29418, ec256,
				new KnownHostsConfig(), null));
		assertFalse(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testFindCertificate() throws Exception {
		Files.write(knownHosts,
				List.of("localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(rsa1024),
						"some.other.com " + PublicKeyEntry.toString(ec384),
						"@cert-authority localhost,127.0.0.1 "
								+ PublicKeyEntry.toString(caKey)));
		assertTrue(database.accept("localhost", LOCAL, certificate,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testCaKeyNotConsidered() throws Exception {
		Files.write(knownHosts,
				List.of("localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(rsa1024),
						"some.other.com " + PublicKeyEntry.toString(ec384),
						"@cert-authority localhost,127.0.0.1 "
								+ PublicKeyEntry.toString(ec256)));
		assertFalse(database.accept("localhost", LOCAL, ec256,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testkeyPlainAndCa() throws Exception {
		Files.write(knownHosts, List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384),
				"@cert-authority localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(ec256),
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256)));
		// ec256 is a CA key, but also a valid direct host key for localhost
		assertTrue(database.accept("localhost", LOCAL, ec256,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testLookupCertificate() throws Exception {
		List<PublicKey> keys = database.lookup("localhost", LOCAL,
				new KnownHostsConfig());
		// Certificates or CA keys are not reported via lookup.
		assertTrue(keys.isEmpty());
	}

	@Test
	public void testCertificateNotAdded() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		assertFalse(database.accept("localhost", LOCAL, certificate,
				new KnownHostsConfig(), null));
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertFalse(
				database.accept("localhost", LOCAL, certificate,
						new KnownHostsConfig(
								KnownHostsConfig.StrictHostKeyChecking.ASK),
						ui));
		assertEquals(0, ui.invocations);
		assertFile(knownHosts, initialKnownHosts);
	}

	@Test
	public void testCertificateNotModified() throws Exception {
		List<String> initialKnownHosts = List.of(
				"@cert-authority localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(ec384),
				"some.other.com " + PublicKeyEntry.toString(ec256));
		Files.write(knownHosts, initialKnownHosts);
		assertFalse(database.accept("localhost", LOCAL, certificate,
				new KnownHostsConfig(), null));
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertFalse(
				database.accept("localhost", LOCAL, certificate,
						new KnownHostsConfig(
								KnownHostsConfig.StrictHostKeyChecking.ASK),
						ui));
		assertEquals(0, ui.invocations);
		assertFile(knownHosts, initialKnownHosts);
	}

	@Test
	public void testModifyFile() throws Exception {
		List<String> initialKnownHosts = List.of(
				"some.other.host " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
				"some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		assertFalse(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(), null));
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, initialKnownHosts2);
		assertFalse(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ACCEPT_NEW),
				null));
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ACCEPT_ANY),
				null));
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, initialKnownHosts2);
		assertFalse(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				null));
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, initialKnownHosts2);

		TestCredentialsProvider ui = new TestCredentialsProvider(true, false);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, initialKnownHosts2);

		ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2, List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384),
				"some.other.com " + PublicKeyEntry.toString(rsa2048)));
		assertTrue(database.accept("127.0.0.1", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testModifyFirstFile() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List.of(
				"some.other.host " + PublicKeyEntry.toString(ec256),
				"some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts,
				List.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384),
						"some.other.com " + PublicKeyEntry.toString(ec384)));
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testModifyMatchingKeyType() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
				"some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, rsa2048,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts,
				List.of("localhost,127.0.0.1 "
						+ PublicKeyEntry.toString(rsa2048),
						"some.other.com " + PublicKeyEntry.toString(ec384)));
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, rsa2048,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testModifyMatchingKeyType2() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts,
				List.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384),
						"some.other.com " + PublicKeyEntry.toString(ec384)));
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testModifySecondFile() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec256),
				"some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts, initialKnownHosts);
		assertFile(knownHosts2,
				List.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384),
						"some.other.com " + PublicKeyEntry.toString(rsa2048)));
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testAddNewKey() throws Exception {
		List<String> initialKnownHosts = List.of(
				"some.other.host " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec256));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List
				.of("some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		List<String> expected = new ArrayList<>(initialKnownHosts);
		expected.add("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384));
		assertFile(knownHosts, expected);
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testCreateNewFile() throws Exception {
		List<String> initialKnownHosts2 = List
				.of("some.other.com " + PublicKeyEntry.toString(ec256));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		assertFile(knownHosts, List
				.of("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384)));
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testAddNewKey2() throws Exception {
		List<String> initialKnownHosts = List.of(
				"some.other.host " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec256));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List
				.of("some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("127.0.0.1:29418", LOCAL_29418, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		List<String> expected = new ArrayList<>(initialKnownHosts);
		expected.add("[127.0.0.1]:29418,[localhost]:29418 "
				+ PublicKeyEntry.toString(ec384));
		assertFile(knownHosts, expected);
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("localhost:29418", LOCAL_29418, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testAddNewKey3() throws Exception {
		List<String> initialKnownHosts = List.of(
				"some.other.host " + PublicKeyEntry.toString(rsa1024),
				"some.other.com " + PublicKeyEntry.toString(ec256));
		Files.write(knownHosts, initialKnownHosts);
		List<String> initialKnownHosts2 = List
				.of("some.other.com " + PublicKeyEntry.toString(rsa2048));
		Files.write(knownHosts2, initialKnownHosts2);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost:29418", LOCAL_29418, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		List<String> expected = new ArrayList<>(initialKnownHosts);
		expected.add("[localhost]:29418,[127.0.0.1]:29418 "
				+ PublicKeyEntry.toString(ec384));
		assertFile(knownHosts, expected);
		assertFile(knownHosts2, initialKnownHosts2);
		assertTrue(database.accept("127.0.0.1:29418", LOCAL_29418, ec384,
				new KnownHostsConfig(), null));
	}

	@Test
	public void testUnknownKeyType() throws Exception {
		List<String> initialKnownHosts = List.of(
				"localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384)
						.replace("ecdsa", "foo"),
				"some.other.com " + PublicKeyEntry.toString(ec384));
		Files.write(knownHosts, initialKnownHosts);
		TestCredentialsProvider ui = new TestCredentialsProvider(true, true);
		assertTrue(database.accept("localhost", LOCAL, ec384,
				new KnownHostsConfig(
						ServerKeyDatabase.Configuration.StrictHostKeyChecking.ASK),
				ui));
		assertEquals(1, ui.invocations);
		// The "modified key" dialog has two questions; whereas the "add new
		// key" is just a simple question.
		assertEquals(2, ui.questions);
		List<String> expected = new ArrayList<>(initialKnownHosts);
		expected.add("localhost,127.0.0.1 " + PublicKeyEntry.toString(ec384));
		assertFile(knownHosts, expected);
		assertTrue(database.accept("127.0.0.1:22", LOCAL, ec384,
				new KnownHostsConfig(), null));
	}

	private void assertFile(Path path, List<String> lines) throws Exception {
		assertEquals(lines, Files.readAllLines(path).stream()
				.filter(s -> !s.isBlank()).toList());
	}

	private static class TestCredentialsProvider extends CredentialsProvider {

		private final boolean[] values;

		int invocations = 0;

		int questions = 0;

		TestCredentialsProvider(boolean accept, boolean store) {
			values = new boolean[] { accept, store };
		}

		@Override
		public boolean isInteractive() {
			return true;
		}

		@Override
		public boolean supports(CredentialItem... items) {
			return true;
		}

		@Override
		public boolean get(URIish uri, CredentialItem... items)
				throws UnsupportedCredentialItem {
			invocations++;
			int i = 0;
			for (CredentialItem item : items) {
				if (item instanceof CredentialItem.YesNoType) {
					((CredentialItem.YesNoType) item)
							.setValue(i < values.length && values[i++]);
					questions++;
				}
			}
			return true;
		}
	}

	private static class KnownHostsConfig implements ServerKeyDatabase.Configuration {

		@NonNull
		private final StrictHostKeyChecking check;

		KnownHostsConfig() {
			this(StrictHostKeyChecking.REQUIRE_MATCH);
		}

		KnownHostsConfig(@NonNull StrictHostKeyChecking check) {
			this.check = check;
		}

		@Override
		public List<String> getUserKnownHostsFiles() {
			return List.of();
		}

		@Override
		public List<String> getGlobalKnownHostsFiles() {
			return List.of();
		}

		@Override
		public StrictHostKeyChecking getStrictHostKeyChecking() {
			return check;
		}

		@Override
		public boolean getHashKnownHosts() {
			return false;
		}

		@Override
		public String getUsername() {
			return "user";
		}

	}
}
