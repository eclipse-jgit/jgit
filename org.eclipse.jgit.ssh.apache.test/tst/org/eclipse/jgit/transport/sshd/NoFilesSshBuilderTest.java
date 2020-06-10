/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.junit.ssh.SshTestHarness;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Test;

/**
 * Test for using the SshdSessionFactory without files in ~/.ssh but with an
 * in-memory setup, creating the factory via the builder API.
 */
public class NoFilesSshBuilderTest extends SshTestHarness {

	private PublicKey testServerKey;

	private KeyPair testUserKey;

	@Override
	protected SshSessionFactory createSessionFactory() {
		return new SshdSessionFactoryBuilder() //
				.setConfigStoreFactory((h, f, u) -> null)
				.setDefaultKeysProvider(f -> new KeyAuthenticator())
				.setServerKeyDatabase((h, s) -> new ServerKeyDatabase() {

					@Override
					public List<PublicKey> lookup(String connectAddress,
							InetSocketAddress remoteAddress,
							Configuration config) {
						return Collections.singletonList(testServerKey);
					}

					@Override
					public boolean accept(String connectAddress,
							InetSocketAddress remoteAddress,
							PublicKey serverKey, Configuration config,
							CredentialsProvider provider) {
						return KeyUtils.compareKeys(serverKey, testServerKey);
					}

				}) //
				.setPreferredAuthentications("publickey")
				.setHomeDirectory(FS.DETECTED.userHome())
				.setSshDirectory(sshDir) //
				.build(new JGitKeyCache());
	}

	private class KeyAuthenticator
			implements KeyIdentityProvider, Iterable<KeyPair> {

		@Override
		public Iterator<KeyPair> iterator() {
			// Should not be called. The use of the Iterable interface in
			// SshdSessionFactory.getDefaultKeys() made sense in sshd 2.0.0,
			// but sshd 2.2.0 added the SessionContext, which although good
			// (without it we couldn't check here) breaks the Iterable analogy.
			// But we're stuck now with that interface for getDefaultKeys, and
			// so this override throwing an exception is unfortunately needed.
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<KeyPair> loadKeys(SessionContext session)
				throws IOException, GeneralSecurityException {
			if (!TEST_USER.equals(session.getUsername())) {
				return Collections.emptyList();
			}
			SshdSocketAddress remoteAddress = SshdSocketAddress
					.toSshdSocketAddress(session.getRemoteAddress());
			switch (remoteAddress.getHostName()) {
			case "localhost":
			case "127.0.0.1":
				return Collections.singletonList(testUserKey);
			default:
				return Collections.emptyList();
			}
		}
	}

	@After
	public void cleanUp() {
		testServerKey = null;
		testUserKey = null;
	}

	@Override
	protected void installConfig(String... config) {
		File configFile = new File(sshDir, Constants.CONFIG);
		if (config != null) {
			try {
				Files.write(configFile.toPath(), Arrays.asList(config));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Test
	public void testCloneWithBuiltInKeys() throws Exception {
		// This test should fail unless our in-memory setup is taken: no
		// known_hosts file, a config that specifies a non-existing key,
		// and the test is using a newly generated KeyPairs anyway.
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		testUserKey = generator.generateKeyPair();
		KeyPair hostKey = generator.generateKeyPair();
		server.addHostKey(hostKey, true);
		testServerKey = hostKey.getPublic();
		assertNotNull(testServerKey);
		assertNotNull(testUserKey);
		server.setTestUserPublicKey(testUserKey.getPublic());
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				new File(getTemporaryDirectory(), "cloned"), null, //
				"Host localhost", //
				"IdentityFile "
						+ new File(sshDir, "does_not_exist").getAbsolutePath());
	}

}
