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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.junit.TestCredentialHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ExecCredentialsProvider} against a stub helper
 * script. POSIX-only (runs a real subprocess).
 */
public class ExecCredentialsProviderTest {

	private Path script;

	private ExecCredentialsProvider provider;

	@Before
	public void setUp() throws IOException {
		TestCredentialHelpers.assumeNotWindows();
		script = TestCredentialHelpers.writeGetHelper("alice", "s3cret");
		provider = new ExecCredentialsProvider(script.toString());
	}

	@After
	public void tearDown() throws IOException {
		if (script != null) {
			Files.deleteIfExists(script);
		}
	}

	@Test
	public void getFillsUsernameAndPassword() throws Exception {
		CredentialItem.Username user = new CredentialItem.Username();
		CredentialItem.Password pass = new CredentialItem.Password();
		assertTrue(provider.get(new URIish("https://example.com/repo.git"), user,
				pass));
		assertEquals("alice", user.getValue());
		assertArrayEquals("s3cret".toCharArray(), pass.getValue());
	}

	@Test
	public void supportsUsernamePasswordButNotYesNo() {
		assertTrue(provider.supports(new CredentialItem.Username(),
				new CredentialItem.Password()));
		assertFalse(provider.supports(new CredentialItem.YesNoType("ok?")));
	}

	@Test
	public void rejectsUnsupportedItem() throws Exception {
		assertThrows(UnsupportedCredentialItem.class,
				() -> provider.get(new URIish("https://example.com/repo.git"),
						new CredentialItem.YesNoType("ok?")));
	}

	@Test
	public void notInteractive() {
		assertFalse(provider.isInteractive());
	}

	@Test
	public void nullHostIsNoOp() throws Exception {
		URIish noHost = new URIish("/local/path");
		assertTrue(noHost.getHost() == null);
		CredentialItem.Username user = new CredentialItem.Username();
		CredentialItem.Password pass = new CredentialItem.Password();
		assertFalse(provider.get(noHost, user, pass));
		// store/erase must not throw for a hostless URI
		provider.store(noHost, user, pass);
		provider.erase(noHost);
	}

	@Test
	public void bearerNotSupportedByDefault() {
		assertFalse(provider.supports(new CredentialItem.Bearer()));
	}

	@Test
	public void storeRunsHelperStoreWithCredentials() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			CredentialItem.Username u = new CredentialItem.Username();
			u.setValue("alice");
			CredentialItem.Password p = new CredentialItem.Password();
			p.setValue("s3cret".toCharArray());
			new ExecCredentialsProvider(helper.toString())
					.store(new URIish("https://example.com/repo.git"), u, p);
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("op=store"));
			assertTrue(captured.contains("username=alice"));
			assertTrue(captured.contains("password=s3cret"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void eraseRunsHelperErase() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString())
					.erase(new URIish("https://example.com/repo.git"));
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("op=erase"));
			assertTrue(captured.contains("host=example.com"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void expiredCredentialIsSkipped() throws Exception {
		Path expired = TestCredentialHelpers.writeGetHelperRaw(
				"username=alice\\npassword=s3cret\\npassword_expiry_utc=1\\n");
		try {
			CredentialItem.Username user = new CredentialItem.Username();
			CredentialItem.Password pass = new CredentialItem.Password();
			assertFalse(new ExecCredentialsProvider(expired.toString())
					.get(new URIish("https://example.com/repo.git"), user, pass));
		} finally {
			Files.deleteIfExists(expired);
		}
	}

	@Test
	public void unexpiredCredentialIsUsed() throws Exception {
		Path fresh = TestCredentialHelpers.writeGetHelperRaw(
				"username=alice\\npassword=s3cret\\npassword_expiry_utc=99999999999\\n");
		try {
			CredentialItem.Username user = new CredentialItem.Username();
			CredentialItem.Password pass = new CredentialItem.Password();
			assertTrue(new ExecCredentialsProvider(fresh.toString())
					.get(new URIish("https://example.com/repo.git"), user, pass));
			assertEquals("alice", user.getValue());
		} finally {
			Files.deleteIfExists(fresh);
		}
	}

	@Test
	public void useHttpPathSendsStrippedPath() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString()).setUseHttpPath(true)
					.get(new URIish("https://example.com/repo.git"),
							new CredentialItem.Username(),
							new CredentialItem.Password());
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("path=repo.git")); // leading / stripped
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void pathOmittedByDefault() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString()).get(
					new URIish("https://example.com/repo.git"),
					new CredentialItem.Username(), new CredentialItem.Password());
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertFalse(captured.contains("path=")); // useHttpPath off by default
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void storeBearerRunsHelperWithAuthtype() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			CredentialItem.Bearer bearer = new CredentialItem.Bearer();
			bearer.setValue("tok".toCharArray());
			new ExecCredentialsProvider(helper.toString()).setUseBearer(true)
					.store(new URIish("https://example.com/repo.git"), bearer);
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("op=store"));
			assertTrue(captured.contains("authtype=Bearer"));
			assertTrue(captured.contains("credential=tok"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void nonBearerAuthtypeNotFilled() throws Exception {
		Path helper = TestCredentialHelpers.writeGetHelperRaw(
				"capability[]=authtype\\nauthtype=Basic\\ncredential=x\\n");
		try {
			CredentialItem.Bearer bearer = new CredentialItem.Bearer();
			assertFalse(new ExecCredentialsProvider(helper.toString())
					.setUseBearer(true)
					.get(new URIish("https://example.com/repo.git"), bearer));
		} finally {
			Files.deleteIfExists(helper);
		}
	}

	@Test
	public void resetDoesNotRunHelper() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString())
					.reset(new URIish("https://example.com/repo.git"));
			assertEquals(0, Files.size(capture)); // reset must not run the helper
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void defaultPortOmittedFromHost() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString()).get(
					new URIish("https://example.com:443/repo.git"),
					new CredentialItem.Username(), new CredentialItem.Password());
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("host=example.com\n"));
			assertFalse(captured.contains("host=example.com:443"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void nonDefaultPortIncludedInHost() throws Exception {
		Path capture = Files.createTempFile("cred-capture", ".txt");
		Path helper = TestCredentialHelpers.writeCapturingHelper(capture);
		try {
			new ExecCredentialsProvider(helper.toString()).get(
					new URIish("https://example.com:8443/repo.git"),
					new CredentialItem.Username(), new CredentialItem.Password());
			String captured = new String(Files.readAllBytes(capture), UTF_8);
			assertTrue(captured.contains("host=example.com:8443"));
		} finally {
			Files.deleteIfExists(helper);
			Files.deleteIfExists(capture);
		}
	}

	@Test
	public void nonexistentHelperReturnsFalse() throws Exception {
		ExecCredentialsProvider p = new ExecCredentialsProvider(
				"/nonexistent/git-credential-nope");
		assertFalse(p.get(new URIish("https://example.com/repo.git"),
				new CredentialItem.Username(), new CredentialItem.Password()));
	}

	@Test
	public void bearerHelperFillsBearerItemWhenOptedIn() throws Exception {
		Path bearer = TestCredentialHelpers.writeGetHelperRaw(
				"capability[]=authtype\\nauthtype=Bearer\\ncredential=tok123\\n");
		try {
			ExecCredentialsProvider p =
					new ExecCredentialsProvider(bearer.toString())
							.setUseBearer(true);
			CredentialItem.Bearer item = new CredentialItem.Bearer();
			assertTrue(p.supports(item));
			assertTrue(p.get(new URIish("https://example.com/repo.git"), item));
			assertArrayEquals("tok123".toCharArray(), item.getValue());
		} finally {
			Files.deleteIfExists(bearer);
		}
	}
}
