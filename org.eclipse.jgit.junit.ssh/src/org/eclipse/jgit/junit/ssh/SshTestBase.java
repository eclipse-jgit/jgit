/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit.ssh;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SshSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ssh tests. Concrete subclasses can re-use these tests by implementing the
 * abstract operations from {@link SshTestHarness}. This gives a way to test
 * different ssh clients against a unified test suite.
 */
public abstract class SshTestBase extends SshBasicTestBase {

	public static String[] KEY_RESOURCES = { //
			"id_dsa", //
			"id_rsa_1024", //
			"id_rsa_2048", //
			"id_rsa_3072", //
			"id_rsa_4096", //
			"id_ecdsa_256", //
			"id_ecdsa_384", //
			"id_ecdsa_521", //
			"id_ed25519", //
			// And now encrypted. Passphrase is "testpass".
			"id_dsa_testpass", //
			"id_rsa_1024_testpass", //
			"id_rsa_2048_testpass", //
			"id_rsa_3072_testpass", //
			"id_rsa_4096_testpass", //
			"id_ecdsa_256_testpass", //
			"id_ecdsa_384_testpass", //
			"id_ecdsa_521_testpass", //
			"id_ed25519_testpass", //
			"id_ed25519_expensive_testpass" };

	@Test
	void testSshWithoutConfig() throws Exception {
		assertThrows(TransportException.class,
				() -> cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter", defaultCloneDir, null));
	}

	@Test
	void testSingleCommand() throws Exception {
		installConfig("IdentityFile " + privateKey1.getAbsolutePath());
		String command = SshTestGitServer.ECHO_COMMAND + " 1 without timeout";
		long start = System.nanoTime();
		String reply = SshSupport.runSshCommand(
				new URIish("ssh://" + TEST_USER + "@localhost:" + testPort),
				null, FS.DETECTED, command, 0); // 0 == no timeout
		long elapsed = System.nanoTime() - start;
		assertEquals(command, reply);
		// Now that we have an idea how long this takes on the test
		// infrastructure, try again with a timeout.
		command = SshTestGitServer.ECHO_COMMAND + " 1 expecting no timeout";
		// Still use a generous timeout.
		int timeout = 10 * ((int) TimeUnit.NANOSECONDS.toSeconds(elapsed) + 1);
		reply = SshSupport.runSshCommand(
				new URIish("ssh://" + TEST_USER + "@localhost:" + testPort),
				null, FS.DETECTED, command, timeout);
		assertEquals(command, reply);
	}

	@Test
	void testSingleCommandWithTimeoutExpired() throws Exception {
		installConfig("IdentityFile " + privateKey1.getAbsolutePath());
		String command = SshTestGitServer.ECHO_COMMAND + " 2 EXPECTING TIMEOUT";

		CommandFailedException e = assertThrows(CommandFailedException.class,
				() -> SshSupport.runSshCommand(new URIish(
						"ssh://" + TEST_USER + "@localhost:" + testPort), null,
						FS.DETECTED, command, 1));
		assertTrue(e.getMessage().contains(command));
		assertTrue(e.getMessage().contains("time"));
	}

	@Test
	void testSshWithGlobalIdentity() throws Exception {
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSshWithDefaultIdentity() throws Exception {
		File idRsa = new File(privateKey1.getParentFile(), "id_rsa");
		Files.copy(privateKey1.toPath(), idRsa.toPath());
		// We expect the session factory to pick up these keys...
		cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter", defaultCloneDir, null);
	}

	@Test
	void testSshWithConfigEncryptedUnusedKey() throws Exception {
		// Copy the encrypted test key from the bundle.
		File encryptedKey = new File(sshDir, "id_dsa");
		copyTestResource("id_dsa_testpass", encryptedKey);
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		assertEquals(0, provider.getLog().size(),
				"CredentialsProvider should not have been called");
	}

	@Test
	void testSshWithConfigEncryptedUnusedKeyInConfigLast() throws Exception {
		// Copy the encrypted test key from the bundle.
		File encryptedKey = new File(sshDir, "id_dsa_test_key");
		copyTestResource("id_dsa_testpass", encryptedKey);
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(),
				"IdentityFile " + encryptedKey.getAbsolutePath());
		// This test passes with JSch per chance because JSch completely ignores
		// the second IdentityFile
		assertEquals(0, provider.getLog().size(),
				"CredentialsProvider should not have been called");
	}

	private boolean isJsch() {
		return getSessionFactory().getType().equals("jsch");
	}

	@Test
	void testSshWithConfigEncryptedUnusedKeyInConfigFirst() throws Exception {
		// Test cannot pass with JSch; it handles only one IdentityFile.
		// assumeTrue(!(getSessionFactory() instanceof
		// JschConfigSessionFactory)); gives in bazel a failure with "Never
		// found parameters that satisfied method assumptions."
		// In maven it's fine!?
		if (isJsch()) {
			return;
		}
		// Copy the encrypted test key from the bundle.
		File encryptedKey = new File(sshDir, "id_dsa_test_key");
		copyTestResource("id_dsa_testpass", encryptedKey);
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + encryptedKey.getAbsolutePath(),
				"IdentityFile " + privateKey1.getAbsolutePath());
		assertEquals(1, provider.getLog().size(),
				"CredentialsProvider should have been called once");
	}

	@Test
	void testSshEncryptedUsedKeyCached() throws Exception {
		// Make sure we are asked for the password only once if we do several
		// operations with an encrypted key.
		File encryptedKey = new File(sshDir, "id_dsa_test_key");
		copyTestResource("id_dsa_testpass", encryptedKey);
		File encryptedPublicKey = new File(sshDir, "id_dsa_test_key.pub");
		copyTestResource("id_dsa_testpass.pub", encryptedPublicKey);
		server.setTestUserPublicKey(encryptedPublicKey.toPath());
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		pushTo(provider, cloneWith("ssh://localhost/doesntmatter", //
				defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + encryptedKey.getAbsolutePath()));
		assertEquals(1, provider.getLog().size(),
				"CredentialsProvider should have been called once");
	}

	@Test
	void testSshEncryptedUsedKeyWrongPassword() throws Exception {
		assertThrows(TransportException.class, () -> {
			File encryptedKey = new File(sshDir, "id_dsa_test_key");
			copyTestResource("id_dsa_testpass", encryptedKey);
			File encryptedPublicKey = new File(sshDir, "id_dsa_test_key.pub");
			copyTestResource("id_dsa_testpass.pub", encryptedPublicKey);
			server.setTestUserPublicKey(encryptedPublicKey.toPath());
			TestCredentialsProvider provider = new TestCredentialsProvider(
					"wrongpass");
			cloneWith("ssh://localhost/doesntmatter", //
					defaultCloneDir, provider, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"NumberOfPasswordPrompts 1", //
					"IdentityFile " + encryptedKey.getAbsolutePath());
		});
	}

	@Test
	void testSshEncryptedUsedKeySeveralPassword() throws Exception {
		File encryptedKey = new File(sshDir, "id_dsa_test_key");
		copyTestResource("id_dsa_testpass", encryptedKey);
		File encryptedPublicKey = new File(sshDir, "id_dsa_test_key.pub");
		copyTestResource("id_dsa_testpass.pub", encryptedPublicKey);
		server.setTestUserPublicKey(encryptedPublicKey.toPath());
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"wrongpass", "wrongpass2", "testpass");
		cloneWith("ssh://localhost/doesntmatter", //
				defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + encryptedKey.getAbsolutePath());
		assertEquals(3, provider.getLog().size(),
				"CredentialsProvider should have been called 3 times");
	}

	@Test
	void testSshWithoutKnownHosts() throws Exception {
		assertThrows(TransportException.class, () -> {
			assertTrue(knownHosts.delete(), "Could not delete known_hosts");
			cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"IdentityFile " + privateKey1.getAbsolutePath());
		});
	}

	@Test
	void testSshWithoutKnownHostsWithProviderAsk() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue(knownHosts.renameTo(copiedHosts),
				"Failed to rename known_hosts");
		// The provider will answer "yes" to all questions, so we should be able
		// to connect and end up with a new known_hosts file with the host key.
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		List<LogEntry> messages = provider.getLog();
		assertFalse(messages.isEmpty(), "Expected user interaction");
		if (isJsch()) {
			// JSch doesn't create a non-existing file.
			assertEquals(1, messages.size(),
					"Expected to be asked about the key");
			return;
		}
		assertEquals(2, messages.size(),
				"Expected to be asked about the key, and the file creation");
		assertTrue(knownHosts.exists(), "~/.ssh/known_hosts should exist now");
		// Instead of checking the file contents, let's just clone again
		// without provider. If it works, the server host key was written
		// correctly.
		File clonedAgain = new File(getTemporaryDirectory(), "cloned2");
		cloneWith("ssh://localhost/doesntmatter", clonedAgain, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSshWithoutKnownHostsWithProviderAcceptNew() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue(knownHosts.renameTo(copiedHosts),
				"Failed to rename known_hosts");
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"StrictHostKeyChecking accept-new", //
				"IdentityFile " + privateKey1.getAbsolutePath());
		if (isJsch()) {
			// JSch doesn't create new files.
			assertTrue(provider.getLog().isEmpty(),
					"CredentialsProvider not called");
			return;
		}
		assertEquals(1, provider.getLog().size(),
				"Expected to be asked about the file creation");
		assertTrue(knownHosts.exists(), "~/.ssh/known_hosts should exist now");
		// Instead of checking the file contents, let's just clone again
		// without provider. If it works, the server host key was written
		// correctly.
		File clonedAgain = new File(getTemporaryDirectory(), "cloned2");
		cloneWith("ssh://localhost/doesntmatter", clonedAgain, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSshWithoutKnownHostsDeny() throws Exception {
		assertThrows(TransportException.class, () -> {
			File copiedHosts = new File(knownHosts.getParentFile(),
					"copiedKnownHosts");
			assertTrue(knownHosts.renameTo(copiedHosts),
					"Failed to rename known_hosts");
			cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"StrictHostKeyChecking yes", //
					"IdentityFile " + privateKey1.getAbsolutePath());
		});
	}

	@Test
	void testSshModifiedHostKeyDeny() throws Exception {
		assertThrows(TransportException.class, () -> {
			File copiedHosts = new File(knownHosts.getParentFile(),
					"copiedKnownHosts");
			assertTrue(knownHosts.renameTo(copiedHosts),
					"Failed to rename known_hosts");
			// Now produce a new known_hosts file containing some other key.
			createKnownHostsFile(knownHosts, "localhost", testPort, publicKey1);
			cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"StrictHostKeyChecking yes", //
					"IdentityFile " + privateKey1.getAbsolutePath());
		});
	}

	@Test
	void testSshModifiedHostKeyWithProviderDeny() throws Exception {
		assertThrows(TransportException.class, () -> {
			File copiedHosts = new File(knownHosts.getParentFile(),
					"copiedKnownHosts");
			assertTrue(knownHosts.renameTo(copiedHosts),
					"Failed to rename known_hosts");
			// Now produce a new known_hosts file containing some other key.
			createKnownHostsFile(knownHosts, "localhost", testPort, publicKey1);
			TestCredentialsProvider provider = new TestCredentialsProvider();
			try {
				cloneWith("ssh://localhost/doesntmatter", defaultCloneDir,
						provider, //
						"Host localhost", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"StrictHostKeyChecking yes", //
						"IdentityFile " + privateKey1.getAbsolutePath());
			} catch (Exception e) {
				assertEquals(1, provider.getLog().size(),
						"Expected to be told about the modified key");
				assertTrue(provider.getLog().stream()
						.flatMap(l -> l.getItems().stream()).allMatch(
								c -> c instanceof CredentialItem.InformationalMessage),
						"Only messages expected");
				throw e;
			}
		});
	}

	private void checkKnownHostsModifiedHostKey(File backup, File newFile,
			String wrongKey) throws IOException {
		List<String> oldLines = Files.readAllLines(backup.toPath(), UTF_8);
		// Find the original entry. We should have that again in known_hosts.
		String oldKeyPart = null;
		for (String oldLine : oldLines) {
			if (oldLine.contains("[localhost]:")) {
				String[] parts = oldLine.split("\\s+");
				if (parts.length > 2) {
					oldKeyPart = parts[parts.length - 2] + ' '
							+ parts[parts.length - 1];
					break;
				}
			}
		}
		assertNotNull(oldKeyPart, "Old key not found");
		List<String> newLines = Files.readAllLines(newFile.toPath(), UTF_8);
		assertFalse(hasHostKey("localhost", testPort, wrongKey, newLines),
				"Old host key still found in known_hosts file" + newFile);
		assertTrue(hasHostKey("localhost", testPort, oldKeyPart, newLines),
				"New host key not found in known_hosts file" + newFile);

	}

	@Test
	void testSshModifiedHostKeyAllow() throws Exception {
		assertTrue(knownHosts.delete(), "Failed to delete known_hosts");
		createKnownHostsFile(knownHosts, "localhost", testPort, publicKey1);
		File backup = new File(getTemporaryDirectory(), "backupKnownHosts");
		Files.copy(knownHosts.toPath(), backup.toPath());
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"StrictHostKeyChecking no", //
				"IdentityFile " + privateKey1.getAbsolutePath());
		// File should not have been updated!
		String[] oldLines = Files.readAllLines(backup.toPath(), UTF_8)
				.toArray(new String[0]);
		String[] newLines = Files.readAllLines(knownHosts.toPath(), UTF_8)
				.toArray(new String[0]);
		assertArrayEquals(oldLines, newLines,
				"Known hosts file should not be modified");
	}

	@Test
	void testSshModifiedHostKeyAsk() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue(knownHosts.renameTo(copiedHosts),
				"Failed to rename known_hosts");
		String wrongKeyPart = createKnownHostsFile(knownHosts, "localhost",
				testPort, publicKey1);
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		checkKnownHostsModifiedHostKey(copiedHosts, knownHosts, wrongKeyPart);
		assertEquals(1, provider.getLog().size(),
				"Expected to be asked about the modified key");
	}

	@Test
	void testSshCloneWithConfigAndPush() throws Exception {
		pushTo(cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath()));
	}

	@Test
	void testSftpWithConfig() throws Exception {
		cloneWith("sftp://localhost/.git", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSftpCloneWithConfigAndPush() throws Exception {
		pushTo(cloneWith("sftp://localhost/.git", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath()));
	}

	@Test
	void testSshWithConfigWrongKey() throws Exception {
		assertThrows(TransportException.class, () -> {
			cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"IdentityFile " + privateKey2.getAbsolutePath());
		});
	}

	@Test
	void testSshWithWrongUserNameInConfig() throws Exception {
		// Bug 526778
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"User sombody_else", //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSshWithWrongPortInConfig() throws Exception {
		// Bug 526778
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port 22", //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testSshWithAliasInConfig() throws Exception {
		// Bug 531118
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), "", //
				"Host localhost", //
				"HostName localhost", //
				"Port 22", //
				"User someone_else", //
				"IdentityFile " + privateKey2.getAbsolutePath());
	}

	@Test
	void testSshWithUnknownCiphersInConfig() throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"Ciphers chacha20-poly1305@openssh.com,aes256-gcm@openssh.com,aes128-gcm@openssh.com,aes256-ctr,aes192-ctr,aes128-ctr");
	}

	@Test
	void testSshWithUnknownHostKeyAlgorithmsInConfig() throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"HostKeyAlgorithms foobar,ssh-rsa,ssh-dss");
	}

	@Test
	void testSshWithUnknownKexAlgorithmsInConfig() throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"KexAlgorithms foobar,diffie-hellman-group14-sha1,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521");
	}

	@Test
	void testSshWithMinimalHostKeyAlgorithmsInConfig() throws Exception {
		// Bug 537790
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"HostKeyAlgorithms ssh-rsa,ssh-dss");
	}

	@Test
	void testSshWithUnknownAuthInConfig() throws Exception {
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"PreferredAuthentications gssapi-with-mic,hostbased,publickey,keyboard-interactive,password");
	}

	@Test
	void testSshWithNoMatchingAuthInConfig() throws Exception {
		assertThrows(TransportException.class, () -> {
			// Server doesn't do password, and anyway we set no password.
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"IdentityFile " + privateKey1.getAbsolutePath(), //
					"PreferredAuthentications password");
		});
	}

	@Test
	void testRsaHostKeySecond() throws Exception {
		// See https://git.eclipse.org/r/#/c/130402/ : server has EcDSA
		// (preferred), RSA, we have RSA in known_hosts: client and server
		// should agree on RSA.
		File newHostKey = new File(getTemporaryDirectory(), "newhostkey");
		copyTestResource("id_ecdsa_256", newHostKey);
		server.addHostKey(newHostKey.toPath(), true);
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testEcDsaHostKey() throws Exception {
		// See https://git.eclipse.org/r/#/c/130402/ : server has RSA
		// (preferred), EcDSA, we have EcDSA in known_hosts: client and server
		// should agree on EcDSA.
		File newHostKey = new File(getTemporaryDirectory(), "newhostkey");
		copyTestResource("id_ecdsa_256", newHostKey);
		server.addHostKey(newHostKey.toPath(), false);
		File newHostKeyPub = new File(getTemporaryDirectory(),
				"newhostkey.pub");
		copyTestResource("id_ecdsa_256.pub", newHostKeyPub);
		createKnownHostsFile(knownHosts, "localhost", testPort, newHostKeyPub);
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	void testPasswordAuth() throws Exception {
		server.enablePasswordAuthentication();
		TestCredentialsProvider provider = new TestCredentialsProvider(
				TEST_USER.toUpperCase(Locale.ROOT));
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"PreferredAuthentications password");
	}

	@Test
	void testPasswordAuthSeveralTimes() throws Exception {
		server.enablePasswordAuthentication();
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"wrongpass", "wrongpass", TEST_USER.toUpperCase(Locale.ROOT));
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"PreferredAuthentications password");
	}

	@Test
	void testPasswordAuthWrongPassword() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enablePasswordAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider(
					"wrongpass");
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications password");
		});
	}

	@Test
	void testPasswordAuthNoPassword() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enablePasswordAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider();
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications password");
		});
	}

	@Test
	void testPasswordAuthCorrectPasswordTooLate() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enablePasswordAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider(
					"wrongpass", "wrongpass", "wrongpass",
					TEST_USER.toUpperCase(Locale.ROOT));
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications password");
		});
	}

	@Test
	void testKeyboardInteractiveAuth() throws Exception {
		server.enableKeyboardInteractiveAuthentication();
		TestCredentialsProvider provider = new TestCredentialsProvider(
				TEST_USER.toUpperCase(Locale.ROOT));
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"PreferredAuthentications keyboard-interactive");
	}

	@Test
	void testKeyboardInteractiveAuthSeveralTimes() throws Exception {
		server.enableKeyboardInteractiveAuthentication();
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"wrongpass", "wrongpass", TEST_USER.toUpperCase(Locale.ROOT));
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"PreferredAuthentications keyboard-interactive");
	}

	@Test
	void testKeyboardInteractiveAuthWrongPassword() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enableKeyboardInteractiveAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider(
					"wrongpass");
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications keyboard-interactive");
		});
	}

	@Test
	void testKeyboardInteractiveAuthNoPassword() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enableKeyboardInteractiveAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider();
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications keyboard-interactive");
		});
	}

	@Test
	void testKeyboardInteractiveAuthCorrectPasswordTooLate() throws Exception {
		assertThrows(TransportException.class, () -> {
			server.enableKeyboardInteractiveAuthentication();
			TestCredentialsProvider provider = new TestCredentialsProvider(
					"wrongpass", "wrongpass", "wrongpass",
					TEST_USER.toUpperCase(Locale.ROOT));
			cloneWith("ssh://git/doesntmatter", defaultCloneDir, provider, //
					"Host git", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"PreferredAuthentications keyboard-interactive");
		});
	}

	static Collection<Arguments> sshKeys() {
		List<Arguments> result = new ArrayList<>();
		for (String k : KEY_RESOURCES) {
			result.add(Arguments.of(k));
		}
		return result;
	}

	@ParameterizedTest
	@MethodSource("sshKeys")
	public void testSshKeys(String keyName) throws Exception {
		// JSch fails on ECDSA 384/521 keys. Compare
		// https://sourceforge.net/p/jsch/patches/10/
		assumeTrue(!(isJsch() && (keyName.contains("ed25519")
				|| keyName.startsWith("id_ecdsa_384")
				|| keyName.startsWith("id_ecdsa_521"))));
		File cloned = new File(getTemporaryDirectory(), "cloned");
		String keyFileName = keyName + "_key";
		File privateKey = new File(sshDir, keyFileName);
		copyTestResource(keyName, privateKey);
		File publicKey = new File(sshDir, keyFileName + ".pub");
		copyTestResource(keyName + ".pub", publicKey);
		server.setTestUserPublicKey(publicKey.toPath());
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		pushTo(provider, cloneWith("ssh://localhost/doesntmatter", //
				cloned, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey.getAbsolutePath()));
		int expectedCalls = keyName.endsWith("testpass") ? 1 : 0;
		assertEquals(expectedCalls, provider.getLog().size(),
				"Unexpected calls to CredentialsProvider");
		// Should now also work without credentials provider, even if the key
		// was encrypted.
		cloned = new File(getTemporaryDirectory(), "cloned2");
		pushTo(null, cloneWith("ssh://localhost/doesntmatter", //
				cloned, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey.getAbsolutePath()));
	}
}
