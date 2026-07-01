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

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ConfigAwareCredentialsProviderTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

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

	@Test
	public void testReadsUsernameFromBaseCredentialConfig() throws Exception {
		Config config = new Config();
		config.fromText("[credential]\n\tusername = alice\n"); //$NON-NLS-1$

		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(config);
		CredentialItem.Username username = new CredentialItem.Username();

		assertTrue(provider.get(uri("https://example.com/repo.git"), username)); //$NON-NLS-1$
		assertEquals("alice", username.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testUriSpecificCredentialUsernameOverridesBase()
			throws Exception {
		Config config = new Config();
		config.fromText("[credential]\n\tusername = base\n" //$NON-NLS-1$
				+ "[credential \"https://example.com/path\"]\n" //$NON-NLS-1$
				+ "\tusername = path\n" //$NON-NLS-1$
				+ "[credential \"https://user@example.com/path\"]\n" //$NON-NLS-1$
				+ "\tusername = specific\n"); //$NON-NLS-1$

		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(config);
		CredentialItem.Username username = new CredentialItem.Username();

		assertTrue(provider.get(uri("https://user@example.com/path/repo.git"), //$NON-NLS-1$
				username));
		assertEquals("specific", username.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredUsernameCombinesWithHelperPassword()
			throws Exception {
		assumeFalse(isWindows());
		Path capture = tmp.newFile("credential-helper-input.txt").toPath(); //$NON-NLS-1$
		Config config = new Config();
		config.fromText("[credential]\n" //$NON-NLS-1$
				+ "\tusername = alice\n" //$NON-NLS-1$
				+ "\tuseHttpPath = true\n" //$NON-NLS-1$
				+ "\thelper = \"!f() { cat > " + capture.toAbsolutePath() //$NON-NLS-1$
				+ "; printf 'password=s3cr3t\\n\\n'; }; f\"\n"); //$NON-NLS-1$

		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(config);
		CredentialItem.Username username = new CredentialItem.Username();
		CredentialItem.Password password = new CredentialItem.Password();

		assertTrue(provider.get(uri("https://example.com/repo.git"), username, //$NON-NLS-1$
				password));
		assertEquals("alice", username.getValue()); //$NON-NLS-1$
		assertArrayEquals("s3cr3t".toCharArray(), password.getValue()); //$NON-NLS-1$

		String written = Files.readString(capture, StandardCharsets.UTF_8);
		assertTrue(written.contains("username=alice")); //$NON-NLS-1$
		assertTrue(written.contains("path=repo.git")); //$NON-NLS-1$
	}

	@Test
	public void testTriesMultipleHelpersInOrder() throws Exception {
		assumeFalse(isWindows());
		Config config = new Config();
		config.fromText("[credential]\n" //$NON-NLS-1$
				+ "\thelper = !printf '\\n'\n" //$NON-NLS-1$
				+ "\thelper = !printf 'password=second\\n\\n'\n"); //$NON-NLS-1$

		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(config);
		CredentialItem.Password password = new CredentialItem.Password();

		assertTrue(provider.get(uri("https://example.com/repo.git"), password)); //$NON-NLS-1$
		assertArrayEquals("second".toCharArray(), password.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testResetForwardsToMatchedHelper() throws Exception {
		assumeFalse(isWindows());
		Path sideEffect = tmp.newFile("erase.txt").toPath(); //$NON-NLS-1$
		Config config = new Config();
		config.fromText("[credential \"https://example.com/path\"]\n" //$NON-NLS-1$
				+ "\thelper = \"!f() { echo \\\"$1\\\" > " //$NON-NLS-1$
				+ sideEffect.toAbsolutePath() + "; }; f\"\n"); //$NON-NLS-1$

		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(config);

		provider.reset(uri("https://example.com/path/repo.git")); //$NON-NLS-1$

		assertEquals("erase", //$NON-NLS-1$
				Files.readString(sideEffect, StandardCharsets.UTF_8).trim());
	}

	@Test
	public void testReturnsFalseWhenNothingConfigured() throws Exception {
		ConfigAwareCredentialsProvider provider =
				new ConfigAwareCredentialsProvider(new Config());
		CredentialItem.Password password = new CredentialItem.Password();

		assertFalse(provider.get(uri("https://example.com/repo.git"), password)); //$NON-NLS-1$
	}
}