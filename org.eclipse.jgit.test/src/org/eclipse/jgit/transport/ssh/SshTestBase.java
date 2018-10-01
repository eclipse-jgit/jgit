/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.transport.ssh;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theory;

/**
 * The ssh tests. Concrete subclasses can re-use these tests by implementing the
 * abstract operations from {@link SshTestHarness}. This gives a way to test
 * different ssh clients against a unified test suite.
 */
public abstract class SshTestBase extends SshTestHarness {

	@DataPoints
	public static String[] KEY_RESOURCES = { //
			"id_dsa", //
			"id_rsa_1024", //
			"id_rsa_2048", //
			"id_rsa_3072", //
			"id_rsa_4096", //
			"id_ecdsa_256", //
			"id_ecdsa_384", //
			"id_ecdsa_521", //
			// And now encrypted. Passphrase is "testpass".
			"id_dsa_testpass", //
			"id_rsa_1024_testpass", //
			"id_rsa_2048_testpass", //
			"id_rsa_3072_testpass", //
			"id_rsa_4096_testpass", //
			"id_ecdsa_256_testpass", //
			"id_ecdsa_384_testpass", //
			"id_ecdsa_521_testpass" };

	protected File defaultCloneDir;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		defaultCloneDir = new File(getTemporaryDirectory(), "cloned");
	}

	@Test(expected = TransportException.class)
	public void testSshWithoutConfig() throws Exception {
		cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter", defaultCloneDir, null);
	}

	@Test
	public void testSshWithGlobalIdentity() throws Exception {
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshWithDefaultIdentity() throws Exception {
		File idRsa = new File(privateKey1.getParentFile(), "id_rsa");
		Files.copy(privateKey1.toPath(), idRsa.toPath());
		// We expect the session factory to pick up these keys...
		cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter", defaultCloneDir, null);
	}

	@Test
	public void testSshWithConfig() throws Exception {
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshWithConfigEncryptedUnusedKey() throws Exception {
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
		assertEquals("CredentialsProvider should not have been called", 0,
				provider.getLog().size());
	}

	@Test
	public void testSshWithConfigEncryptedUnusedKeyInConfigLast()
			throws Exception {
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
		assertEquals("CredentialsProvider should not have been called", 0,
				provider.getLog().size());
	}

	@Test
	public void testSshWithConfigEncryptedUnusedKeyInConfigFirst()
			throws Exception {
		// Test cannot pass with JSch; it handles only one IdentityFile.
		// assumeTrue(!(getSessionFactory() instanceof
		// JschConfigSessionFactory)); gives in bazel a failure with "Never
		// found parameters that satisfied method assumptions."
		// In maven it's fine!?
		if (getSessionFactory() instanceof JschConfigSessionFactory) {
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
		assertEquals("CredentialsProvider should have been called once", 1,
				provider.getLog().size());
	}

	@Test
	public void testSshEncryptedUsedKeyCached() throws Exception {
		// Make sure we are asked for the password only once if we do several
		// operations with an encrypted key.
		File encryptedKey = new File(sshDir, "id_dsa_test_key");
		copyTestResource("id_dsa_testpass", encryptedKey);
		File encryptedPublicKey = new File(sshDir, "id_dsa_test_key.pub");
		copyTestResource("id_dsa_testpass.pub", encryptedPublicKey);
		server.setTestUserPublicKey(encryptedPublicKey.toPath());
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		pushTo(provider,
				cloneWith("ssh://localhost/doesntmatter", //
						defaultCloneDir, provider, //
						"Host localhost", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + encryptedKey.getAbsolutePath()));
		assertEquals("CredentialsProvider should have been called once", 1,
				provider.getLog().size());
	}

	@Test(expected = TransportException.class)
	public void testSshWithoutKnownHosts() throws Exception {
		assertTrue("Could not delete known_hosts", knownHosts.delete());
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshWithoutKnownHostsWithProviderAsk()
			throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		// The provider will answer "yes" to all questions, so we should be able
		// to connect and end up with a new known_hosts file with the host key.
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		Map<URIish, List<CredentialItem>> messages = provider.getLog();
		assertFalse("Expected user interaction", messages.isEmpty());
		if (getSessionFactory() instanceof JschConfigSessionFactory) {
			// JSch doesn't create a non-existing file.
			assertEquals("Expected to be asked about the key", 1,
					messages.size());
			return;
		}
		assertEquals(
				"Expected to be asked about the key, and the file creation",
				2, messages.size());
		assertTrue("~/.ssh/known_hosts should exist now", knownHosts.exists());
		// Instead of checking the file contents, let's just clone again
		// without provider. If it works, the server host key was written
		// correctly.
		File clonedAgain = new File(getTemporaryDirectory(), "cloned2");
		cloneWith("ssh://localhost/doesntmatter", clonedAgain, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshWithoutKnownHostsWithProviderAcceptNew()
			throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"StrictHostKeyChecking accept-new", //
				"IdentityFile " + privateKey1.getAbsolutePath());
		if (getSessionFactory() instanceof JschConfigSessionFactory) {
			// JSch doesn't create new files.
			assertTrue("CredentialsProvider not called",
					provider.getLog().isEmpty());
			return;
		}
		assertEquals("Expected to be asked about the file creation", 1,
				provider.getLog().size());
		assertTrue("~/.ssh/known_hosts should exist now", knownHosts.exists());
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

	@Test(expected = TransportException.class)
	public void testSshWithoutKnownHostsDeny() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"StrictHostKeyChecking yes", //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test(expected = TransportException.class)
	public void testSshModifiedHostKeyDeny()
			throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		// Now produce a new known_hosts file containing some other key.
		createKnownHostsFile(knownHosts, "localhost", testPort, publicKey1);
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"StrictHostKeyChecking yes", //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test(expected = TransportException.class)
	public void testSshModifiedHostKeyWithProviderDeny() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		// Now produce a new known_hosts file containing some other key.
		createKnownHostsFile(knownHosts, "localhost", testPort, publicKey1);
		TestCredentialsProvider provider = new TestCredentialsProvider();
		try {
			cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, provider, //
					"Host localhost", //
					"HostName localhost", //
					"Port " + testPort, //
					"User " + TEST_USER, //
					"StrictHostKeyChecking yes", //
					"IdentityFile " + privateKey1.getAbsolutePath());
		} catch (Exception e) {
			assertEquals("Expected to be told about the modified key", 1,
					provider.getLog().size());
			assertTrue("Only messages expected", provider.getLog().values()
					.stream().flatMap(List::stream).allMatch(
							c -> c instanceof CredentialItem.InformationalMessage));
			throw e;
		}
	}

	private void checkKnownHostsModifiedHostKey(File backup, File newFile,
			String wrongKey) throws IOException {
		List<String> oldLines = Files.readAllLines(backup.toPath(),
				StandardCharsets.UTF_8);
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
		assertNotNull("Old key not found", oldKeyPart);
		List<String> newLines = Files.readAllLines(newFile.toPath(),
				StandardCharsets.UTF_8);
		assertFalse("Old host key still found in known_hosts file" + newFile,
				hasHostKey("localhost", testPort, wrongKey, newLines));
		assertTrue("New host key not found in known_hosts file" + newFile,
				hasHostKey("localhost", testPort, oldKeyPart, newLines));

	}

	@Test
	public void testSshModifiedHostKeyAllow() throws Exception {
		assertTrue("Failed to delete known_hosts", knownHosts.delete());
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
		String[] oldLines = Files
				.readAllLines(backup.toPath(), StandardCharsets.UTF_8)
				.toArray(new String[0]);
		String[] newLines = Files
				.readAllLines(knownHosts.toPath(), StandardCharsets.UTF_8)
				.toArray(new String[0]);
		assertArrayEquals("Known hosts file should not be modified", oldLines,
				newLines);
	}

	@Test
	public void testSshModifiedHostKeyAsk() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
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
		assertEquals("Expected to be asked about the modified key", 1,
				provider.getLog().size());
	}

	@Test
	public void testSshCloneWithConfigAndPush() throws Exception {
		pushTo(cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath()));
	}

	@Test
	public void testSftpWithConfig() throws Exception {
		cloneWith("sftp://localhost/.git", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSftpCloneWithConfigAndPush() throws Exception {
		pushTo(cloneWith("sftp://localhost/.git", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath()));
	}

	@Test(expected = TransportException.class)
	public void testSshWithConfigWrongKey() throws Exception {
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey2.getAbsolutePath());
	}

	@Test
	public void testSshWithWrongUserNameInConfig() throws Exception {
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
	public void testSshWithWrongPortInConfig() throws Exception {
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
	public void testSshWithAliasInConfig() throws Exception {
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
	public void testSshWithUnknownCiphersInConfig() throws Exception {
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
	public void testSshWithUnknownHostKeyAlgorithmsInConfig()
			throws Exception {
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
	public void testSshWithUnknownKexAlgorithmsInConfig()
			throws Exception {
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
	public void testSshWithMinimalHostKeyAlgorithmsInConfig()
			throws Exception {
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
	public void testSshWithUnknownAuthInConfig() throws Exception {
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"PreferredAuthentications gssapi-with-mic,hostbased,publickey,keyboard-interactive,password");
	}

	@Test(expected = TransportException.class)
	public void testSshWithNoMatchingAuthInConfig() throws Exception {
		// Server doesn't do password, and anyway we set no password.
		cloneWith("ssh://git/doesntmatter", defaultCloneDir, null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"PreferredAuthentications password");
	}

	@Theory
	public void testSshKeys(String keyName) throws Exception {
		// JSch fails on ECDSA 384/521 keys. Compare
		// https://sourceforge.net/p/jsch/patches/10/
		assumeTrue(!(getSessionFactory() instanceof JschConfigSessionFactory
				&& (keyName.startsWith("id_ecdsa_384")
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
		pushTo(provider,
				cloneWith("ssh://localhost/doesntmatter", //
						cloned, provider, //
						"Host localhost", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey.getAbsolutePath()));
		int expectedCalls = keyName.endsWith("testpass") ? 1 : 0;
		assertEquals("Unexpected calls to CredentialsProvider", expectedCalls,
				provider.getLog().size());
		// Should now also work without credentials provider, even if the key
		// was encrypted.
		cloned = new File(getTemporaryDirectory(), "cloned2");
		pushTo(null,
				cloneWith("ssh://localhost/doesntmatter", //
						cloned, null, //
						"Host localhost", //
						"HostName localhost", //
						"Port " + testPort, //
						"User " + TEST_USER, //
						"IdentityFile " + privateKey.getAbsolutePath()));
	}
}
