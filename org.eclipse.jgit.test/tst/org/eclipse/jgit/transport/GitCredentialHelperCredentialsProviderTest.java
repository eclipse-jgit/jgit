/*
 * Copyright (C) 2026, JGit contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GitCredentialHelperCredentialsProviderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	// Skip all tests on Windows: shell-snippet helpers use 'sh -c'
	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT) //$NON-NLS-1$ //$NON-NLS-2$
				.contains("windows"); //$NON-NLS-1$
	}

	private static URIish uri(String raw) {
		try {
			return new URIish(raw);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	// ---------------------------------------------------------------------------
	// supports()
	// ---------------------------------------------------------------------------

	@Test
	public void testSupportsUsernameAndPassword() {
		GitCredentialHelperCredentialsProvider p = new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertTrue(p.supports(new CredentialItem.Username(),
				new CredentialItem.Password()));
	}

	@Test
	public void testSupportsPasswordStringType() {
		GitCredentialHelperCredentialsProvider p = new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertTrue(p.supports(new CredentialItem.StringType("Password: ", true))); //$NON-NLS-1$
	}

	@Test
	public void testDoesNotSupportYesNoType() {
		GitCredentialHelperCredentialsProvider p = new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertFalse(p.supports(new CredentialItem.YesNoType("Trust?"))); //$NON-NLS-1$
	}

	@Test
	public void testDoesNotSupportArbitraryStringType() {
		GitCredentialHelperCredentialsProvider p = new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertFalse(p.supports(new CredentialItem.StringType("Token: ", true))); //$NON-NLS-1$
	}

	@Test
	public void testIsNotInteractive() {
		GitCredentialHelperCredentialsProvider p = new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertFalse(p.isInteractive());
	}

	// ---------------------------------------------------------------------------
	// buildCommand()
	// ---------------------------------------------------------------------------

	@Test
	public void testBuildCommandShellSnippet() {
		assumeFalse(isWindows());
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider("!my-helper --opt"); //$NON-NLS-1$
		List<String> cmd = p.buildCommand("get"); //$NON-NLS-1$
		assertEquals("sh", cmd.get(0)); //$NON-NLS-1$
		assertEquals("-c", cmd.get(1)); //$NON-NLS-1$
		assertEquals("my-helper --opt get", cmd.get(2)); //$NON-NLS-1$
	}

	@Test
	public void testBuildCommandAbsolutePath() {
		assumeFalse(isWindows());
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider("/usr/bin/git-credential-foo"); //$NON-NLS-1$
		List<String> cmd = p.buildCommand("get"); //$NON-NLS-1$
		assertEquals(2, cmd.size());
		assertEquals("/usr/bin/git-credential-foo", cmd.get(0)); //$NON-NLS-1$
		assertEquals("get", cmd.get(1)); //$NON-NLS-1$
	}

	@Test
	public void testBuildCommandRelativeName() {
		assumeFalse(isWindows());
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider("osxkeychain"); //$NON-NLS-1$
		List<String> cmd = p.buildCommand("get"); //$NON-NLS-1$
		assertEquals("sh", cmd.get(0)); //$NON-NLS-1$
		assertEquals("-c", cmd.get(1)); //$NON-NLS-1$
		assertEquals("git-credential-osxkeychain get", cmd.get(2)); //$NON-NLS-1$
	}

	// ---------------------------------------------------------------------------
	// get()
	// ---------------------------------------------------------------------------

	@Test
	public void testGetPopulatesUsernameAndPassword()
			throws UnsupportedCredentialItem {
		assumeFalse(isWindows());
		// Shell snippet that echoes username and password
		String snippet = "!printf 'username=alice\\npassword=s3cr3t\\n\\n'"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();

		assertTrue(p.get(uri("https://example.com/repo.git"), u, pw)); //$NON-NLS-1$
		assertEquals("alice", u.getValue()); //$NON-NLS-1$
		assertArrayEquals("s3cr3t".toCharArray(), pw.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testGetPopulatesPasswordStringType()
			throws UnsupportedCredentialItem {
		assumeFalse(isWindows());
		String snippet = "!printf 'username=bob\\npassword=p@ss\\n\\n'"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.StringType pw =
				new CredentialItem.StringType("Password: ", true); //$NON-NLS-1$

		assertTrue(p.get(uri("https://example.com/repo.git"), pw)); //$NON-NLS-1$
		assertEquals("p@ss", pw.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testGetReturnsFalseWhenHelperProducesNoOutput()
			throws UnsupportedCredentialItem {
		assumeFalse(isWindows());
		// Helper that outputs nothing (just a blank line)
		String snippet = "!printf '\\n'"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();

		assertFalse(p.get(uri("https://example.com/repo.git"), u, pw)); //$NON-NLS-1$
	}

	@Test
	public void testGetReturnsFalseWhenHelperExitsNonZero()
			throws UnsupportedCredentialItem {
		assumeFalse(isWindows());
		String snippet = "!exit 1"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();

		assertFalse(p.get(uri("https://example.com/repo.git"), u, pw)); //$NON-NLS-1$
	}

	@Test
	public void testGetSendsProtocolAndHostToHelper()
			throws UnsupportedCredentialItem, IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("stdin-capture.txt").toPath(); //$NON-NLS-1$
		// Write stdin to a file, then echo fixed credentials
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'username=x\\npassword=y\\n\\n'; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet)
						.setUseHttpPath(true);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();

		p.get(uri("https://example.com/my/repo.git"), u, pw); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("protocol=https")); //$NON-NLS-1$
		assertTrue(written.contains("host=example.com")); //$NON-NLS-1$
		assertTrue(written.contains("path=my/repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testGetSendsPortWhenNonDefault()
			throws UnsupportedCredentialItem, IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("stdin-capture-port.txt").toPath(); //$NON-NLS-1$
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'username=x\\npassword=y\\n\\n'; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();

		p.get(uri("https://example.com:8443/repo.git"), u, pw); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("host=example.com:8443")); //$NON-NLS-1$
	}

	// ---------------------------------------------------------------------------
	// reset() / store()
	// ---------------------------------------------------------------------------

	@Test
	public void testResetInvokesEraseAction() throws IOException {
		assumeFalse(isWindows());
		Path sideEffect = tmp.newFile("erase.txt").toPath(); //$NON-NLS-1$
		// Write the received action to a side-effect file
		String snippet = "!f() { echo \"$1\" > " //$NON-NLS-1$
				+ sideEffect.toAbsolutePath() + "; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		p.reset(uri("https://example.com/repo.git")); //$NON-NLS-1$

		// Allow the async process a moment to write (it's a fast shell op)
		String content = new String(Files.readAllBytes(sideEffect),
				StandardCharsets.UTF_8).trim();
		assertEquals("erase", content); //$NON-NLS-1$
	}

	@Test
	public void testStoreInvokesStoreAction() throws IOException {
		assumeFalse(isWindows());
		Path sideEffect = tmp.newFile("store.txt").toPath(); //$NON-NLS-1$
		String snippet = "!f() { echo \"$1\" > " //$NON-NLS-1$
				+ sideEffect.toAbsolutePath() + "; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		p.store(uri("https://example.com/repo.git"), "alice", //$NON-NLS-1$ //$NON-NLS-2$
				"s3cr3t".toCharArray()); //$NON-NLS-1$

		String content = new String(Files.readAllBytes(sideEffect),
				StandardCharsets.UTF_8).trim();
		assertEquals("store", content); //$NON-NLS-1$
	}

	@Test
	public void testStoreSendsCredentialsToHelper() throws IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("store-capture.txt").toPath(); //$NON-NLS-1$
		// Wrap in a function so the appended action argument is harmless
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		p.store(uri("https://example.com/repo.git"), "alice", //$NON-NLS-1$ //$NON-NLS-2$
				"s3cr3t".toCharArray()); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("username=alice")); //$NON-NLS-1$
		assertTrue(written.contains("password=s3cr3t")); //$NON-NLS-1$
	}

	// ---------------------------------------------------------------------------
	// useHttpPath
	// ---------------------------------------------------------------------------

	@Test
	public void testPathOmittedForHttpsByDefault()
			throws UnsupportedCredentialItem, IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("no-path-capture.txt").toPath(); //$NON-NLS-1$
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'username=x\\npassword=y\\n\\n'; }; f"; //$NON-NLS-1$
		// useHttpPath defaults to false
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();
		p.get(uri("https://example.com/my/repo.git"), u, pw); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("protocol=https")); //$NON-NLS-1$
		assertTrue(written.contains("host=example.com")); //$NON-NLS-1$
		assertFalse("path must not be sent when useHttpPath=false", //$NON-NLS-1$
				written.contains("path=")); //$NON-NLS-1$
	}

	@Test
	public void testPathSentForHttpsWhenUseHttpPathTrue()
			throws UnsupportedCredentialItem, IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("with-path-capture.txt").toPath(); //$NON-NLS-1$
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'username=x\\npassword=y\\n\\n'; }; f"; //$NON-NLS-1$
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet)
						.setUseHttpPath(true);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();
		p.get(uri("https://example.com/my/repo.git"), u, pw); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("path=my/repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testPathAlwaysSentForSshRegardlessOfUseHttpPath()
			throws UnsupportedCredentialItem, IOException {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("ssh-path-capture.txt").toPath(); //$NON-NLS-1$
		String snippet = "!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'username=x\\npassword=y\\n\\n'; }; f"; //$NON-NLS-1$
		// useHttpPath=false but scheme is ssh, so path must still be sent
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider(snippet);

		CredentialItem.Username u = new CredentialItem.Username();
		CredentialItem.Password pw = new CredentialItem.Password();
		p.get(uri("ssh://example.com/proj/repo.git"), u, pw); //$NON-NLS-1$

		String written = new String(Files.readAllBytes(capture),
				StandardCharsets.UTF_8);
		assertTrue(written.contains("path=proj/repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testSetUseHttpPathReturnsSelf() {
		GitCredentialHelperCredentialsProvider p =
				new GitCredentialHelperCredentialsProvider("test"); //$NON-NLS-1$
		assertFalse(p.isUseHttpPath());
		GitCredentialHelperCredentialsProvider returned = p.setUseHttpPath(true);
		assertTrue(p.isUseHttpPath());
		assertTrue(returned == p); // fluent setter returns same instance
	}
}
