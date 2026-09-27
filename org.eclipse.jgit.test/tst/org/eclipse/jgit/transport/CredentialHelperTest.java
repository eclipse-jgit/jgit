/*
 * Copyright (C) 2026, JGit contributors and others
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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.junit.TestCredentialHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link CredentialHelper} using a real stub helper script. The test
 * is POSIX-only and is skipped on Windows (via
 * {@link TestCredentialHelpers#assumeNotWindows()}), because it runs
 * {@code #!/bin/sh} stub scripts. This is a limitation of the test harness, not
 * of {@link CredentialHelper}, which runs helpers through
 * {@link org.eclipse.jgit.util.FS} on every platform.
 */
public class CredentialHelperTest {

	private Path script;

	@Before
	public void setUp() throws IOException {
		TestCredentialHelpers.assumeNotWindows();
		script = TestCredentialHelpers.writeGetHelper("alice", "s3cret");
	}

	@After
	public void tearDown() throws IOException {
		if (script != null) {
			Files.deleteIfExists(script);
		}
	}

	@Test
	public void getReturnsUsernamePassword() throws Exception {
		CredentialHelper.Answer answer = new CredentialHelper(script.toString())
				.get("https", "example.com", null, null);
		assertEquals("alice", answer.getUsername());
		assertArrayEquals("s3cret".toCharArray(), answer.getPassword());
	}

	@Test
	public void snippetHelperRuns() throws Exception {
		// A "!" prefix selects git's shell-snippet rule; here it runs the stub.
		CredentialHelper.Answer answer = new CredentialHelper(
				"!" + script.toString()).get("https", "example.com", null,
						null);
		assertEquals("alice", answer.getUsername());
		assertArrayEquals("s3cret".toCharArray(), answer.getPassword());
	}

	@Test
	public void bareNameResolvesToGitCredentialSubcommand() {
		// git dispatches "git credential-<name>", not a "git-credential-<name>"
		// binary on PATH.
		ProcessBuilder pb = new CredentialHelper("google-gerrit")
				.process("get");
		assertTrue(pb.command().stream()
				.anyMatch(s -> s.contains("git credential-google-gerrit")));
	}

	@Test
	public void eraseRunsWithoutError() throws Exception {
		// The stub ignores erase; the call must complete without throwing.
		new CredentialHelper(script.toString()).erase("https", "example.com",
				null, null);
	}
}
