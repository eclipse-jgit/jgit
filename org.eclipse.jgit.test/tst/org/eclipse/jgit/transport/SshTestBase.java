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
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.ssh.SshTestGitServer;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Test;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

public abstract class SshTestBase extends RepositoryTestCase {

	protected static final String TEST_USER = "testuser";

	protected File sshDir;

	protected File privateKey1;

	protected File privateKey2;

	private SshTestGitServer server;

	private SshSessionFactory factory;

	protected int testPort;

	protected File knownHosts;

	private File homeDir;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		writeTrashFile("file.txt", "something");
		try (Git git = new Git(db)) {
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Initial commit").call();
		}
		mockSystemReader.setProperty("user.home",
				getTemporaryDirectory().getAbsolutePath());
		mockSystemReader.setProperty("HOME",
				getTemporaryDirectory().getAbsolutePath());
		homeDir = FS.DETECTED.userHome();
		FS.DETECTED.setUserHome(getTemporaryDirectory().getAbsoluteFile());
		sshDir = new File(getTemporaryDirectory(), ".ssh");
		assertTrue(sshDir.mkdir());
		File serverDir = new File(getTemporaryDirectory(), "srv");
		assertTrue(serverDir.mkdir());
		// Create two key pairs. Let's not call them "id_rsa".
		privateKey1 = new File(sshDir, "first_key");
		privateKey2 = new File(sshDir, "second_key");
		createKeyPair(privateKey1);
		createKeyPair(privateKey2);
		ByteArrayOutputStream publicHostKey = new ByteArrayOutputStream();
		// Start a server with our test user and the first key.
		server = new SshTestGitServer(TEST_USER, privateKey1.toPath(), db,
				createHostKey(publicHostKey));
		testPort = server.start();
		assertTrue(testPort > 0);
		knownHosts = new File(sshDir, "known_hosts");
		Files.write(knownHosts.toPath(), Collections.singleton("[localhost]:"
				+ testPort + ' '
				+ publicHostKey.toString(StandardCharsets.US_ASCII.name())));
		factory = createSessionFactory();
		SshSessionFactory.setInstance(factory);
	}

	private static void createKeyPair(File privateKeyFile) throws Exception {
		// Found no way to do this with MINA sshd except rolling it all
		// ourselves...
		JSch jsch = new JSch();
		KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
		try (OutputStream out = new FileOutputStream(privateKeyFile)) {
			pair.writePrivateKey(out);
		}
		File publicKeyFile = new File(privateKeyFile.getParentFile(),
				privateKeyFile.getName() + ".pub");
		try (OutputStream out = new FileOutputStream(publicKeyFile)) {
			pair.writePublicKey(out, TEST_USER);
		}
	}

	private static byte[] createHostKey(OutputStream publicKey)
			throws Exception {
		JSch jsch = new JSch();
		KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
		pair.writePublicKey(publicKey, "");
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			pair.writePrivateKey(out);
			out.flush();
			return out.toByteArray();
		}
	}

	@After
	public void shutdownServer() throws Exception {
		if (server != null) {
			server.stop();
			server = null;
		}
		FS.DETECTED.setUserHome(homeDir);
		SshSessionFactory.setInstance(null);
		factory = null;
	}

	protected abstract SshSessionFactory createSessionFactory();

	protected SshSessionFactory getSessionFactory() {
		return factory;
	}

	protected abstract void installConfig(String... config);

	@Test(expected = TransportException.class)
	public void testSshCloneWithoutConfig() throws Exception {
		cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter", null);
	}

	@Test
	public void testSshCloneWithGlobalIdentity() throws Exception {
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithDefaultIdentity() throws Exception {
		File idRsa = new File(privateKey1.getParentFile(), "id_rsa");
		Files.copy(privateKey1.toPath(), idRsa.toPath());
		// We expect the session factory to pick up these keys...
		cloneWith("ssh://" + TEST_USER + "@localhost:" + testPort
				+ "/doesntmatter", null);
	}

	@Test
	public void testSshCloneWithConfig() throws Exception {
		cloneWith("ssh://localhost/doesntmatter", null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithConfigEncryptedUnusedKey() throws Exception {
		// Copy the encrypted test key from the bundle.
		File encryptedKey = new File(sshDir, "id_dsa");
		try (InputStream in = SshTestBase.class
				.getResourceAsStream("id_dsa_test")) {
			Files.copy(in, encryptedKey.toPath());
		}
		TestCredentialsProvider provider = new TestCredentialsProvider(
				"testpass");
		cloneWith("ssh://localhost/doesntmatter", provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		assertEquals("CredentialsProvider should not have been called", 0,
				provider.getLog().size());
	}

	@Test(expected = TransportException.class)
	public void testSshCloneWithoutKnownHosts() throws Exception {
		assertTrue("Could not delete known_hosts", knownHosts.delete());
		cloneWith("ssh://localhost/doesntmatter", null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithoutKnownHostsWithProvider() throws Exception {
		File copiedHosts = new File(knownHosts.getParentFile(),
				"copiedKnownHosts");
		assertTrue("Failed to rename known_hosts",
				knownHosts.renameTo(copiedHosts));
		TestCredentialsProvider provider = new TestCredentialsProvider();
		cloneWith("ssh://localhost/doesntmatter", provider, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		Map<URIish, List<CredentialItem>> messages = provider.getLog();
		assertFalse("Expected user iteraction", messages.isEmpty());
	}

	@Test
	public void testSftpCloneWithConfig() throws Exception {
		cloneWith("sftp://localhost/.git", null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test(expected = TransportException.class)
	public void testSshCloneWithConfigWrongKey() throws Exception {
		cloneWith("ssh://localhost/doesntmatter", null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey2.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithWrongUserNameInConfig() throws Exception {
		// Bug 526778
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				null, //
				"Host localhost", //
				"HostName localhost", //
				"User sombody_else", //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithWrongPortInConfig() throws Exception {
		// Bug 526778
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				null, //
				"Host localhost", //
				"HostName localhost", //
				"Port 22", //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshCloneWithAliasInConfig() throws Exception {
		// Bug 531118
		cloneWith("ssh://git/doesntmatter", null, //
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
	public void testSshCloneWithUnknownCiphersInConfig() throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"Ciphers chacha20-poly1305@openssh.com,aes256-gcm@openssh.com,aes128-gcm@openssh.com,aes256-ctr,aes192-ctr,aes128-ctr");
	}

	@Test
	public void testSshCloneWithUnknownHostKeyAlgorithmsInConfig()
			throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"HostKeyAlgorithms foobar,ssh-rsa,ssh-dss");
	}

	@Test
	public void testSshCloneWithUnknownKexAlgorithmsInConfig()
			throws Exception {
		// Bug 535672
		cloneWith("ssh://git/doesntmatter", null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"KexAlgorithms foobar,diffie-hellman-group14-sha1,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521");
	}

	@Test
	public void testSshCloneWithMinimalHostKeyAlgorithmsInConfig()
			throws Exception {
		// Bug 537790
		cloneWith("ssh://git/doesntmatter", null, //
				"Host git", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath(), //
				"HostKeyAlgorithms ssh-rsa,ssh-dss");
	}

	private void cloneWith(String uri, CredentialsProvider provider,
			String... config) throws Exception {
		installConfig(config);
		File cloned = new File(getTemporaryDirectory(), "cloned");
		CloneCommand clone = Git.cloneRepository().setCloneAllBranches(true)
				.setDirectory(cloned).setURI(uri);
		if (provider != null) {
			clone.setCredentialsProvider(provider);
		}
		try (Git git = clone.call()) {
			assertNotNull(git.getRepository().resolve("master"));
			assertNotEquals(db.getWorkTree(),
					git.getRepository().getWorkTree());
			checkFile(new File(git.getRepository().getWorkTree(), "file.txt"),
					"something");
		}
	}

	private static class TestCredentialsProvider extends CredentialsProvider {

		private final List<String> stringStore;

		private final Iterator<String> strings;

		public TestCredentialsProvider(String... strings) {
			if (strings == null || strings.length == 0) {
				stringStore = Collections.emptyList();
			} else {
				stringStore = Arrays.asList(strings);
			}
			this.strings = stringStore.iterator();
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
			System.out.println("URI: " + uri);
			for (CredentialItem item : items) {
				System.out.println(item.getClass().getSimpleName() + ' '
						+ item.getPromptText());
			}
			logItems(uri, items);
			for (CredentialItem item : items) {
				if (item instanceof CredentialItem.InformationalMessage) {
					continue;
				}
				if (item instanceof CredentialItem.YesNoType) {
					((CredentialItem.YesNoType) item).setValue(true);
				} else if (item instanceof CredentialItem.CharArrayType) {
					if (strings.hasNext()) {
						((CredentialItem.CharArrayType) item)
								.setValue(strings.next().toCharArray());
					} else {
						return false;
					}
				} else if (item instanceof CredentialItem.StringType) {
					if (strings.hasNext()) {
						((CredentialItem.StringType) item)
								.setValue(strings.next());
					} else {
						return false;
					}
				} else {
					return false;
				}
			}
			return true;
		}

		private Map<URIish, List<CredentialItem>> log = new LinkedHashMap<>();

		private void logItems(URIish uri, CredentialItem... items) {
			log.put(uri, Arrays.asList(items));
		}

		public Map<URIish, List<CredentialItem>> getLog() {
			return log;
		}
	}
}
