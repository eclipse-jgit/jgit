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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.junit.TestCredentialHelpers;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link GitConfigCredentialsProvider}: resolving {@code credential.*}
 * config to a helper chain. POSIX-only (runs a real stub helper).
 */
public class GitConfigCredentialsProviderTest {

	private Path script;

	private Path bearerScript;

	@Before
	public void setUp() throws IOException {
		TestCredentialHelpers.assumeNotWindows();
		script = TestCredentialHelpers.writeGetHelper("alice", "s3cret");
		bearerScript = TestCredentialHelpers
				.writeGetHelperRaw("authtype=Bearer\\ncredential=tok\\n");
	}

	@After
	public void tearDown() throws IOException {
		if (script != null) {
			Files.deleteIfExists(script);
		}
		if (bearerScript != null) {
			Files.deleteIfExists(bearerScript);
		}
	}

	private static Config config(String text) throws Exception {
		Config c = new Config();
		c.fromText(text);
		return c;
	}

	private static boolean getUserPass(CredentialsProvider p, String url)
			throws Exception {
		CredentialItem.Username user = new CredentialItem.Username();
		CredentialItem.Password pass = new CredentialItem.Password();
		boolean ok = p.get(new URIish(url), user, pass);
		if (ok) {
			assertEquals("alice", user.getValue());
			assertArrayEquals("s3cret".toCharArray(), pass.getValue());
		}
		return ok;
	}

	@Test
	public void unscopedHelperIsUsed() throws Exception {
		Config c = config("[credential]\n\thelper = " + script + "\n");
		assertTrue(getUserPass(new GitConfigCredentialsProvider(c),
				"https://example.com/repo.git"));
	}

	@Test
	public void noHelperConfiguredReturnsFalse() throws Exception {
		assertFalse(getUserPass(new GitConfigCredentialsProvider(config("")),
				"https://example.com/repo.git"));
	}

	@Test
	public void emptyHelperValueResetsTheList() throws Exception {
		// A bogus helper, then an empty value (reset), then the real stub.
		Config c = config("[credential]\n\thelper = git-credential-bogusxyz\n"
				+ "\thelper =\n\thelper = " + script + "\n");
		assertTrue(getUserPass(new GitConfigCredentialsProvider(c),
				"https://example.com/repo.git"));
	}

	@Test
	public void urlSubsectionMatches() throws Exception {
		Config c = config("[credential \"https://example.com\"]\n\thelper = "
				+ script + "\n");
		// Matching host uses the subsection helper.
		assertTrue(getUserPass(new GitConfigCredentialsProvider(c),
				"https://example.com/repo.git"));
		// Non-matching host has no helper.
		assertFalse(getUserPass(new GitConfigCredentialsProvider(c),
				"https://other.example.org/repo.git"));
	}

	@Test
	public void bearerNotSupportedByDefault() throws Exception {
		Config c = config("[credential]\n\thelper = " + bearerScript + "\n");
		GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(c);
		assertFalse(p.supports(new CredentialItem.Bearer()));
	}

	@Test
	public void bearerHelperFillsBearerItemWhenOptedIn() throws Exception {
		Config c = config("[credential]\n\thelper = " + bearerScript + "\n");
		GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(c)
				.setUseBearer(true);
		CredentialItem.Bearer bearer = new CredentialItem.Bearer();
		assertTrue(p.supports(bearer));
		assertTrue(p.get(new URIish("https://example.com/repo.git"), bearer));
		assertArrayEquals("tok".toCharArray(), bearer.getValue());
	}

	@Test
	public void fallbackUsedWhenNoHelperConfigured() throws Exception {
		CredentialsProvider fallback = new UsernamePasswordCredentialsProvider(
				"bob", "pw");
		GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(
				config(""), fallback);
		CredentialItem.Username user = new CredentialItem.Username();
		CredentialItem.Password pass = new CredentialItem.Password();
		assertTrue(p.get(new URIish("https://example.com/repo.git"), user, pass));
		assertEquals("bob", user.getValue());
	}

	@Test
	public void storeAndEraseFanOutToHelper() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(
					config("[credential]\n\thelper = " + helper + "\n"));
			URIish uri = new URIish("https://example.com/repo.git");
			CredentialItem.Username u = new CredentialItem.Username();
			u.setValue("alice");
			CredentialItem.Password pw = new CredentialItem.Password();
			pw.setValue("s3cret".toCharArray());
			p.store(uri, u, pw);
			p.erase(uri);
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("op=store"));
			assertTrue(captured.contains("op=erase"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void baseAndSubsectionHelpersBothChain() throws Exception {
		Path mark = Files.createTempFile("chain-mark", ".txt");
		Path baseH = Files.createTempFile("git-credential-base", ".sh");
		// base helper returns nothing -> chain must fall through to subsection
		Files.write(baseH, ("#!/bin/sh\n" + "cat >/dev/null 2>&1\n" + "echo base >> "
				+ mark + "\n").getBytes(UTF_8));
		baseH.toFile().setExecutable(true);
		Path subH = Files.createTempFile("git-credential-sub", ".sh");
		Files.write(subH, ("#!/bin/sh\n" + "cat >/dev/null 2>&1\n" + "echo sub >> "
				+ mark + "\n" + "case \"$1\" in\n"
				+ "get) printf 'username=alice\\npassword=s3cret\\n\\n' ;;\n"
				+ "esac\n").getBytes(UTF_8));
		subH.toFile().setExecutable(true);
		try {
			Config c = config("[credential]\n\thelper = " + baseH + "\n"
					+ "[credential \"https://example.com\"]\n\thelper = " + subH
					+ "\n");
			CredentialItem.Username u = new CredentialItem.Username();
			CredentialItem.Password pw = new CredentialItem.Password();
			assertTrue(new GitConfigCredentialsProvider(c)
					.get(new URIish("https://example.com/repo.git"), u, pw));
			assertEquals("alice", u.getValue());
			String m = new String(Files.readAllBytes(mark), UTF_8);
			assertTrue(m.contains("base")); // base tried first
			assertTrue(m.contains("sub")); // then the matching subsection
		} finally {
			Files.deleteIfExists(baseH);
			Files.deleteIfExists(subH);
			Files.deleteIfExists(mark);
		}
	}

	@Test
	public void useHttpPathSubsectionOverridesBase() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			Config c = config("[credential]\n\thelper = " + helper + "\n"
					+ "\tuseHttpPath = false\n"
					+ "[credential \"https://example.com\"]\n"
					+ "\tuseHttpPath = true\n");
			new GitConfigCredentialsProvider(c).get(
					new URIish("https://example.com/repo.git"),
					new CredentialItem.Username(), new CredentialItem.Password());
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("path=repo.git")); // subsection wins
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void resetStoreEraseDelegateToFallbackWhenNoHelper() throws Exception {
		RecordingProvider fallback = new RecordingProvider();
		GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(
				config(""), fallback);
		URIish uri = new URIish("https://example.com/repo.git");
		p.reset(uri);
		p.store(uri, new CredentialItem.Username());
		p.erase(uri);
		assertTrue(fallback.reset);
		assertTrue(fallback.stored);
		assertTrue(fallback.erased);
	}

	@Test
	public void supportsDelegatesToFallback() throws Exception {
		GitConfigCredentialsProvider p = new GitConfigCredentialsProvider(
				config(""), new RecordingProvider());
		// A YesNoType is not a helper item; support must come from the fallback.
		assertTrue(p.supports(new CredentialItem.YesNoType("ok?")));
	}

	@Test
	public void baseStoreEraseAreNoOps() throws Exception {
		CredentialsProvider p = new UsernamePasswordCredentialsProvider("a", "b");
		URIish uri = new URIish("https://example.com/repo.git");
		p.store(uri, new CredentialItem.Username()); // inherited no-op, no throw
		p.erase(uri); // inherited no-op, no throw
	}

	private static class RecordingProvider extends CredentialsProvider {
		boolean reset;

		boolean stored;

		boolean erased;

		@Override
		public boolean isInteractive() {
			return false;
		}

		@Override
		public boolean supports(CredentialItem... items) {
			return true;
		}

		@Override
		public boolean get(URIish uri, CredentialItem... items) {
			return true;
		}

		@Override
		public void reset(URIish uri) {
			reset = true;
		}

		@Override
		public void store(URIish uri, CredentialItem... items) {
			stored = true;
		}

		@Override
		public void erase(URIish uri) {
			erased = true;
		}
	}
}
