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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.Test;

/** Tests for the git-compatible credential wire codec on {@link Credential}. */
public class CredentialTest {

	@Test
	public void writesMinimalGetRequest() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "example.com";
		assertEquals("protocol=https\nhost=example.com\n\n",
				new String(c.write(true), UTF_8));
	}

	@Test
	public void writesFullGetRequestInGitFieldOrder() {
		Credential c = new Credential();
		c.capaAuthtype = true;
		c.protocol = "https";
		c.host = "example.com";
		c.path = "repo.git";
		c.username = "alice";
		assertEquals("capability[]=authtype\n" + "protocol=https\n"
				+ "host=example.com\n" + "path=repo.git\n" + "username=alice\n\n",
				new String(c.write(true), UTF_8));
	}

	@Test
	public void roundTripsMultiBytePassword() throws Exception {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "example.com";
		c.password = "päß—wörd".toCharArray();
		Credential read = new Credential();
		read.read(new ByteArrayInputStream(c.write(true)));
		assertArrayEquals("päß—wörd".toCharArray(), read.password);
	}

	@Test
	public void writesMultiBytePasswordGoldenBytes() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "example.com";
		c.password = "pä".toCharArray();
		byte[] prefix = "protocol=https\nhost=example.com\npassword=p"
				.getBytes(UTF_8);
		byte[] expected = new byte[prefix.length + 4];
		System.arraycopy(prefix, 0, expected, 0, prefix.length);
		// 'ä' (U+00E4) is UTF-8 0xC3 0xA4, then item and record newlines
		expected[prefix.length] = (byte) 0xC3;
		expected[prefix.length + 1] = (byte) 0xA4;
		expected[prefix.length + 2] = '\n';
		expected[prefix.length + 3] = '\n';
		assertArrayEquals(expected, c.write(true));
	}

	@Test
	public void rejectsNulInValueAlways() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "exa\0mple.com";
		assertThrows(IllegalArgumentException.class, () -> c.write(false));
	}

	@Test
	public void rejectsNewlineInValueAlways() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "exa\nmple.com";
		assertThrows(IllegalArgumentException.class, () -> c.write(false));
	}

	@Test
	public void rejectsCarriageReturnOnlyUnderProtectProtocol() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "exa\rmple.com";
		assertThrows(IllegalArgumentException.class, () -> c.write(true));
		c.write(false); // allowed when protectProtocol is off (git default)
	}

	@Test
	public void requiresProtocolAndHost() {
		Credential c = new Credential();
		c.host = "example.com";
		assertThrows(IllegalArgumentException.class, () -> c.write(true));
	}

	@Test
	public void readsUsernamePassword() throws Exception {
		Credential c = new Credential();
		c.read(in("username=alice\npassword=s3cret\n\n"));
		assertEquals("alice", c.username);
		assertArrayEquals("s3cret".toCharArray(), c.password);
	}

	@Test
	public void readsAuthtypeCredentialAndCapability() throws Exception {
		Credential c = new Credential();
		c.read(in("capability[]=authtype\nauthtype=Bearer\ncredential=tok\n\n"));
		assertTrue(c.capaAuthtype);
		assertEquals("Bearer", c.authtype);
		assertArrayEquals("tok".toCharArray(), c.credential);
	}

	@Test
	public void blankLineTerminatesRecord() throws Exception {
		Credential c = new Credential();
		c.read(in("username=alice\n\npassword=ignored\n"));
		assertEquals("alice", c.username);
		assertNull(c.password);
	}

	@Test
	public void parsesExpiry() throws Exception {
		Credential c = new Credential();
		c.read(in("password_expiry_utc=1700000000\n\n"));
		assertEquals(1700000000L, c.passwordExpiryUtc);
	}

	@Test
	public void zeroExpiryMeansNoExpiry() throws Exception {
		Credential c = new Credential();
		c.read(in("password_expiry_utc=0\n\n"));
		assertEquals(Credential.NO_EXPIRY, c.passwordExpiryUtc);
	}

	@Test
	public void unparseableExpiryMeansNoExpiry() throws Exception {
		Credential c = new Credential();
		c.read(in("password_expiry_utc=notanumber\n\n"));
		assertEquals(Credential.NO_EXPIRY, c.passwordExpiryUtc);
	}

	@Test
	public void ignoresMalformedLineWithoutEquals() throws Exception {
		Credential c = new Credential();
		c.read(in("garbage-no-equals\nusername=alice\n\n"));
		assertEquals("alice", c.username);
	}

	@Test
	public void parsesLastLineWithoutTrailingNewline() throws Exception {
		Credential c = new Credential();
		c.read(in("username=alice\npassword=s3cret"));
		assertEquals("alice", c.username);
		assertArrayEquals("s3cret".toCharArray(), c.password);
	}

	@Test
	public void deferredKeysAreIgnored() throws Exception {
		Credential c = new Credential();
		c.read(in("state[]=opaque\ncontinue=1\nusername=alice\n\n"));
		assertEquals("alice", c.username);
	}

	@Test
	public void rejectsOverlongLine() {
		StringBuilder sb = new StringBuilder("username=");
		for (int i = 0; i <= Credential.MAX_LINE; i++) {
			sb.append('a');
		}
		assertThrows(java.io.IOException.class,
				() -> new Credential().read(in(sb.toString())));
	}

	@Test
	public void writesPasswordValue() {
		Credential c = new Credential();
		c.protocol = "https";
		c.host = "example.com";
		c.username = "alice";
		c.password = "s3cret".toCharArray();
		assertEquals("protocol=https\nhost=example.com\nusername=alice\n"
				+ "password=s3cret\n\n", new String(c.write(true), UTF_8));
	}

	@Test
	public void writesAuthtypeAndCredentialBeforeProtocol() {
		Credential c = new Credential();
		c.capaAuthtype = true;
		c.authtype = "Bearer";
		c.credential = "tok".toCharArray();
		c.protocol = "https";
		c.host = "example.com";
		assertEquals("capability[]=authtype\nauthtype=Bearer\ncredential=tok\n"
				+ "protocol=https\nhost=example.com\n\n",
				new String(c.write(true), UTF_8));
	}

	@Test
	public void requiresHost() {
		Credential c = new Credential();
		c.protocol = "https";
		assertThrows(IllegalArgumentException.class, () -> c.write(true));
	}

	private static ByteArrayInputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(UTF_8));
	}
}
