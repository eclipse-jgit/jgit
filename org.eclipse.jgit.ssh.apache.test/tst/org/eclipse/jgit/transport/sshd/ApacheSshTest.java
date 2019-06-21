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
package org.eclipse.jgit.transport.sshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.ssh.SshTestBase;
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

	@Test (expected = TransportException.class)
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
		cloneWith(
				"ssh://" + TEST_USER + "@localhost:" + testPort
						+ "/doesntmatter",
				defaultCloneDir, null,
				"IdentityFile " + privateKey1.getAbsolutePath());
	}
}
