/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.ServerAuthenticationManager;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.StaticDecisionForwardingFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.junit.ssh.SshTestBase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ApacheSshTest extends SshTestBase {

	@Override
	protected SshSessionFactory createSessionFactory() {
		SshdSessionFactory result = new SshdSessionFactory(new JGitKeyCache(),
				null);
		// The home directory is mocked at this point!
		result.setHomeDirectory(FS.DETECTED.userHome());
		result.setSshDirectory(sshDir);
		return result;
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
	public void testEd25519HostKey() throws Exception {
		// Using ed25519 user identities is tested in the super class in
		// testSshKeys().
		File newHostKey = new File(getTemporaryDirectory(), "newhostkey");
		copyTestResource("id_ed25519", newHostKey);
		server.addHostKey(newHostKey.toPath(), true);
		File newHostKeyPub = new File(getTemporaryDirectory(),
				"newhostkey.pub");
		copyTestResource("id_ed25519.pub", newHostKeyPub);
		createKnownHostsFile(knownHosts, "localhost", testPort, newHostKeyPub);
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testHashedKnownHosts() throws Exception {
		assertTrue("Failed to delete known_hosts", knownHosts.delete());
		// The provider will answer "yes" to all questions, so we should be able
		// to connect and end up with a new known_hosts file with the host key.
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"HashKnownHosts yes", //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		List<LogEntry> messages = provider.getLog();
		assertFalse("Expected user interaction", messages.isEmpty());
		assertEquals(
				"Expected to be asked about the key, and the file creation", 2,
				messages.size());
		assertTrue("~/.ssh/known_hosts should exist now", knownHosts.exists());
		// Let's clone again without provider. If it works, the server host key
		// was written correctly.
		File clonedAgain = new File(getTemporaryDirectory(), "cloned2");
		cloneWith("ssh://localhost/doesntmatter", clonedAgain, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		// Check that the first line contains neither "localhost" nor
		// "127.0.0.1", but does contain the expected hash.
		List<String> lines = Files.readAllLines(knownHosts.toPath()).stream()
				.filter(s -> s != null && s.length() >= 1 && s.charAt(0) != '#'
						&& !s.trim().isEmpty())
				.collect(Collectors.toList());
		assertEquals("Unexpected number of known_hosts lines", 1, lines.size());
		String line = lines.get(0);
		assertFalse("Found host in line", line.contains("localhost"));
		assertFalse("Found IP in line", line.contains("127.0.0.1"));
		assertTrue("Hash not found", line.contains("|"));
		KnownHostEntry entry = KnownHostEntry.parseKnownHostEntry(line);
		assertTrue("Hash doesn't match localhost",
				entry.isHostMatch("localhost", testPort)
						|| entry.isHostMatch("127.0.0.1", testPort));
	}

	@Test
	public void testPreamble() throws Exception {
		// Test that the client can deal with strange lines being sent before
		// the server identification string.
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 257; i++) {
			b.append('a');
		}
		server.setPreamble("A line with a \000 NUL",
				"A long line: " + b.toString());
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testLongPreamble() throws Exception {
		// Test that the client can deal with a long (about 60k) preamble.
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 1024; i++) {
			b.append('a');
		}
		String line = b.toString();
		String[] lines = new String[60];
		for (int i = 0; i < lines.length; i++) {
			lines[i] = line;
		}
		server.setPreamble(lines);
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testHugePreamble() throws Exception {
		// Test that the connection fails when the preamble is longer than 64k.
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 1024; i++) {
			b.append('a');
		}
		String line = b.toString();
		String[] lines = new String[70];
		for (int i = 0; i < lines.length; i++) {
			lines[i] = line;
		}
		server.setPreamble(lines);
		TransportException e = assertThrows(TransportException.class,
				() -> cloneWith(
						"ssh://" + TEST_USER + "@localhost:" + testPort
								+ "/doesntmatter",
						defaultCloneDir, null,
						"IdentityFile " + privateKey1.getAbsolutePath()));
		// The assertions test that we don't run into bug 565394 / SSHD-1050
		assertFalse(e.getMessage().contains("timeout"));
		assertTrue(e.getMessage().contains("65536")
				|| e.getMessage().contains("closed"));
	}

	/**
	 * Test for SSHD-1028. If the server doesn't close sessions, the second
	 * fetch will fail. Occurs on sshd 2.5.[01].
	 *
	 * @throws Exception
	 *             on errors
	 * @see <a href=
	 *      "https://issues.apache.org/jira/projects/SSHD/issues/SSHD-1028">SSHD-1028</a>
	 */
	@Test
	public void testCloneAndFetchWithSessionLimit() throws Exception {
		PropertyResolverUtils.updateProperty(server.getPropertyResolver(),
				ServerFactoryManager.MAX_CONCURRENT_SESSIONS, 2);
		File localClone = cloneWith("ssh://localhost/doesntmatter",
				defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		// Fetch a couple of times
		try (Git git = Git.open(localClone)) {
			git.fetch().call();
			git.fetch().call();
		}
	}

	/**
	 * Creates a simple proxy server. Accepts only publickey authentication from
	 * the given user with the given key, allows all forwardings. Adds the
	 * proxy's host key to {@link #knownHosts}.
	 *
	 * @param user
	 *            to accept
	 * @param userKey
	 *            public key of that user at this server
	 * @param report
	 *            single-element array to report back the forwarded address.
	 * @return the started server
	 * @throws Exception
	 */
	private SshServer createProxy(String user, File userKey,
			SshdSocketAddress[] report) throws Exception {
		SshServer proxy = SshServer.setUpDefaultServer();
		// Give the server its own host key
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair proxyHostKey = generator.generateKeyPair();
		proxy.setKeyPairProvider(
				session -> Collections.singletonList(proxyHostKey));
		// Allow (only) publickey authentication
		proxy.setUserAuthFactories(Collections.singletonList(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PUBLIC_KEY_FACTORY));
		// Install the user's public key
		PublicKey userProxyKey = AuthorizedKeyEntry
				.readAuthorizedKeys(userKey.toPath()).get(0)
				.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
		proxy.setPublickeyAuthenticator(
				(userName, publicKey, session) -> user.equals(userName)
						&& KeyUtils.compareKeys(userProxyKey, publicKey));
		// Allow forwarding
		proxy.setForwardingFilter(new StaticDecisionForwardingFilter(true) {

			@Override
			protected boolean checkAcceptance(String request, Session session,
					SshdSocketAddress target) {
				report[0] = target;
				return super.checkAcceptance(request, session, target);
			}
		});
		proxy.start();
		// Add the proxy's host key to knownhosts
		try (BufferedWriter writer = Files.newBufferedWriter(
				knownHosts.toPath(), StandardCharsets.US_ASCII,
				StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
			writer.append('\n');
			KnownHostHashValue.appendHostPattern(writer, "localhost",
					proxy.getPort());
			writer.append(',');
			KnownHostHashValue.appendHostPattern(writer, "127.0.0.1",
					proxy.getPort());
			writer.append(' ');
			PublicKeyEntry.appendPublicKeyEntry(writer,
					proxyHostKey.getPublic());
			writer.append('\n');
		}
		return proxy;
	}

	@Test
	public void testJumpHost() throws Exception {
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				// Now try to clone via the proxy
				cloneWith("ssh://server/doesntmatter", defaultCloneDir, null, //
						"Host server", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey1.getAbsolutePath(), //
						"ProxyJump " + TEST_USER + "X@proxy:" + proxy.getPort(), //
						"", //
						"Host proxy", //
						"Hostname localhost", //
						"IdentityFile " + privateKey2.getAbsolutePath());
				assertNotNull(forwarded[0]);
				assertEquals(testPort, forwarded[0].getPort());
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostWrongKeyAtProxy() throws Exception {
		// Test that we find the proxy server's URI in the exception message
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				// Now try to clone via the proxy
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith("ssh://server/doesntmatter",
								defaultCloneDir, null, //
								"Host server", //
								"HostName localhost", //
								"Port " + testPort, //
								"User " + TEST_USER, //
								"IdentityFile " + privateKey1.getAbsolutePath(),
								"ProxyJump " + TEST_USER + "X@proxy:"
										+ proxy.getPort(), //
								"", //
								"Host proxy", //
								"Hostname localhost", //
								"IdentityFile "
										+ privateKey1.getAbsolutePath()));
				String message = e.getMessage();
				assertTrue(message.contains("localhost:" + proxy.getPort()));
				assertTrue(message.contains("proxy:" + proxy.getPort()));
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostWrongKeyAtServer() throws Exception {
		// Test that we find the target server's URI in the exception message
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				// Now try to clone via the proxy
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith("ssh://server/doesntmatter",
								defaultCloneDir, null, //
								"Host server", //
								"HostName localhost", //
								"Port " + testPort, //
								"User " + TEST_USER, //
								"IdentityFile " + privateKey2.getAbsolutePath(),
								"ProxyJump " + TEST_USER + "X@proxy:"
										+ proxy.getPort(), //
								"", //
								"Host proxy", //
								"Hostname localhost", //
								"IdentityFile "
										+ privateKey2.getAbsolutePath()));
				String message = e.getMessage();
				assertTrue(message.contains("localhost:" + testPort));
				assertTrue(message.contains("ssh://server"));
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostNonSsh() throws Exception {
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith("ssh://server/doesntmatter",
								defaultCloneDir, null, //
								"Host server", //
								"HostName localhost", //
								"Port " + testPort, //
								"User " + TEST_USER, //
								"IdentityFile " + privateKey1.getAbsolutePath(), //
								"ProxyJump http://" + TEST_USER + "X@proxy:"
										+ proxy.getPort(), //
								"", //
								"Host proxy", //
								"Hostname localhost", //
								"IdentityFile "
										+ privateKey2.getAbsolutePath()));
				// Find the expected message
				Throwable t = e;
				while (t != null) {
					if (t instanceof URISyntaxException) {
						break;
					}
					t = t.getCause();
				}
				assertNotNull(t);
				assertTrue(t.getMessage().contains("Non-ssh"));
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostWithPath() throws Exception {
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith("ssh://server/doesntmatter",
								defaultCloneDir, null, //
								"Host server", //
								"HostName localhost", //
								"Port " + testPort, //
								"User " + TEST_USER, //
								"IdentityFile " + privateKey1.getAbsolutePath(), //
								"ProxyJump ssh://" + TEST_USER + "X@proxy:"
										+ proxy.getPort() + "/wrongPath", //
								"", //
								"Host proxy", //
								"Hostname localhost", //
								"IdentityFile "
										+ privateKey2.getAbsolutePath()));
				// Find the expected message
				Throwable t = e;
				while (t != null) {
					if (t instanceof URISyntaxException) {
						break;
					}
					t = t.getCause();
				}
				assertNotNull(t);
				assertTrue(t.getMessage().contains("wrongPath"));
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostWithPathShort() throws Exception {
		SshdSocketAddress[] forwarded = { null };
		try (SshServer proxy = createProxy(TEST_USER + 'X', publicKey2,
				forwarded)) {
			try {
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith("ssh://server/doesntmatter",
								defaultCloneDir, null, //
								"Host server", //
								"HostName localhost", //
								"Port " + testPort, //
								"User " + TEST_USER, //
								"IdentityFile " + privateKey1.getAbsolutePath(), //
								"ProxyJump " + TEST_USER + "X@proxy:wrongPath", //
								"", //
								"Host proxy", //
								"Hostname localhost", //
								"Port " + proxy.getPort(), //
								"IdentityFile "
										+ privateKey2.getAbsolutePath()));
				// Find the expected message
				Throwable t = e;
				while (t != null) {
					if (t instanceof URISyntaxException) {
						break;
					}
					t = t.getCause();
				}
				assertNotNull(t);
				assertTrue(t.getMessage().contains("wrongPath"));
			} finally {
				proxy.stop();
			}
		}
	}

	@Test
	public void testJumpHostChain() throws Exception {
		SshdSocketAddress[] forwarded1 = { null };
		SshdSocketAddress[] forwarded2 = { null };
		try (SshServer proxy1 = createProxy(TEST_USER + 'X', publicKey2,
				forwarded1);
				SshServer proxy2 = createProxy("foo", publicKey1, forwarded2)) {
			try {
				// Clone proxy1 -> proxy2 -> server
				cloneWith("ssh://server/doesntmatter", defaultCloneDir, null, //
						"Host server", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey1.getAbsolutePath(), //
						"ProxyJump proxy2," + TEST_USER + "X@proxy:"
								+ proxy1.getPort(), //
						"", //
						"Host proxy", //
						"Hostname localhost", //
						"IdentityFile " + privateKey2.getAbsolutePath(), //
						"", //
						"Host proxy2", //
						"Hostname localhost", //
						"User foo", //
						"Port " + proxy2.getPort(), //
						"IdentityFile " + privateKey1.getAbsolutePath());
				assertNotNull(forwarded1[0]);
				assertEquals(proxy2.getPort(), forwarded1[0].getPort());
				assertNotNull(forwarded2[0]);
				assertEquals(testPort, forwarded2[0].getPort());
			} finally {
				proxy1.stop();
				proxy2.stop();
			}
		}
	}

	@Test
	public void testJumpHostCascade() throws Exception {
		SshdSocketAddress[] forwarded1 = { null };
		SshdSocketAddress[] forwarded2 = { null };
		try (SshServer proxy1 = createProxy(TEST_USER + 'X', publicKey2,
				forwarded1);
				SshServer proxy2 = createProxy("foo", publicKey1, forwarded2)) {
			try {
				// Clone proxy2 -> proxy1 -> server
				cloneWith("ssh://server/doesntmatter", defaultCloneDir, null, //
						"Host server", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey1.getAbsolutePath(), //
						"ProxyJump " + TEST_USER + "X@proxy", //
						"", //
						"Host proxy", //
						"Hostname localhost", //
						"Port " + proxy1.getPort(), //
						"ProxyJump ssh://proxy2:" + proxy2.getPort(), //
						"IdentityFile " + privateKey2.getAbsolutePath(), //
						"", //
						"Host proxy2", //
						"Hostname localhost", //
						"User foo", //
						"IdentityFile " + privateKey1.getAbsolutePath());
				assertNotNull(forwarded1[0]);
				assertEquals(testPort, forwarded1[0].getPort());
				assertNotNull(forwarded2[0]);
				assertEquals(proxy1.getPort(), forwarded2[0].getPort());
			} finally {
				proxy1.stop();
				proxy2.stop();
			}
		}
	}

	@Test
	public void testJumpHostRecursion() throws Exception {
		SshdSocketAddress[] forwarded1 = { null };
		SshdSocketAddress[] forwarded2 = { null };
		try (SshServer proxy1 = createProxy(TEST_USER + 'X', publicKey2,
				forwarded1);
				SshServer proxy2 = createProxy("foo", publicKey1, forwarded2)) {
			try {
				TransportException e = assertThrows(TransportException.class,
						() -> cloneWith(
						"ssh://server/doesntmatter", defaultCloneDir, null, //
						"Host server", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey1.getAbsolutePath(), //
						"ProxyJump " + TEST_USER + "X@proxy", //
						"", //
						"Host proxy", //
						"Hostname localhost", //
						"Port " + proxy1.getPort(), //
						"ProxyJump ssh://proxy2:" + proxy2.getPort(), //
						"IdentityFile " + privateKey2.getAbsolutePath(), //
						"", //
						"Host proxy2", //
						"Hostname localhost", //
						"User foo", //
						"ProxyJump " + TEST_USER + "X@proxy", //
						"IdentityFile " + privateKey1.getAbsolutePath()));
				assertTrue(e.getMessage().contains("proxy"));
			} finally {
				proxy1.stop();
				proxy2.stop();
			}
		}
	}
}
