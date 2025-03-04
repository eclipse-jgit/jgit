/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.StreamCorruptedException;
import java.time.Instant;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the line parsing in {@link AllowedSigners}.
 */
public class AllowedSignersParseTest {

	@Before
	public void setup() {
		// Uses GMT-03:30 as time zone.
		SystemReader.setInstance(new MockSystemReader());
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	public void testValidDate() {
		assertEquals(Instant.parse("2024-09-01T00:00:00.00Z"),
				AllowedSigners.parseDate("20240901Z"));
		assertEquals(Instant.parse("2024-09-01T01:02:00.00Z"),
				AllowedSigners.parseDate("202409010102Z"));
		assertEquals(Instant.parse("2024-09-01T01:02:03.00Z"),
				AllowedSigners.parseDate("20240901010203Z"));
		assertEquals(Instant.parse("2024-09-01T03:30:00.00Z"),
				AllowedSigners.parseDate("20240901"));
		assertEquals(Instant.parse("2024-09-01T04:32:00.00Z"),
				AllowedSigners.parseDate("202409010102"));
		assertEquals(Instant.parse("2024-09-01T04:32:03.00Z"),
				AllowedSigners.parseDate("20240901010203"));
	}

	@Test
	public void testInvalidDate() {
		assertThrows(Exception.class, () -> AllowedSigners.parseDate("1234"));
		assertThrows(Exception.class,
				() -> AllowedSigners.parseDate("09/01/2024"));
		assertThrows(Exception.class,
				() -> AllowedSigners.parseDate("2024-09-01"));
	}

	private void checkValidKey(String expected, String input, int from)
			throws StreamCorruptedException {
		assertEquals(expected, AllowedSigners.parsePublicKey(input, from));
	}
	@Test
	public void testValidPublicKey() throws StreamCorruptedException {
		checkValidKey(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				0);
		checkValidKey(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				"xyzssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				3);
		checkValidKey(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				"xyz ssh-ed25519   AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO abc",
				3);
		checkValidKey(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO",
				"xyz\tssh-ed25519 \tAAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO abc",
				3);
	}

	@Test
	public void testInvalidPublicKey() {
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey(null, 0));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("", 0));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("foo", 0));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("ssh-ed25519 bar", -1));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("ssh-ed25519 bar", 12));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("ssh-ed25519 bar", 13));
		assertThrows(Exception.class,
				() -> AllowedSigners.parsePublicKey("ssh-ed25519 bar", 16));
	}

	@Test
	public void testValidDequote() {
		assertEquals(new AllowedSigners.Dequoted("a\\bc", 4),
				AllowedSigners.dequote("a\\bc", 0));
		assertEquals(new AllowedSigners.Dequoted("a\\bc\"", 5),
				AllowedSigners.dequote("a\\bc\"", 0));
		assertEquals(new AllowedSigners.Dequoted("a\\b\"c", 5),
				AllowedSigners.dequote("a\\b\"c", 0));
		assertEquals(new AllowedSigners.Dequoted("a\\b\"c", 8),
				AllowedSigners.dequote("\"a\\b\\\"c\"", 0));
		assertEquals(new AllowedSigners.Dequoted("a\\b\"c", 11),
				AllowedSigners.dequote("xyz\"a\\b\\\"c\"", 3));
		assertEquals(new AllowedSigners.Dequoted("abc", 6),
				AllowedSigners.dequote("   abc def", 3));
	}

	@Test
	public void testInvalidDequote() {
		assertThrows(Exception.class, () -> AllowedSigners.dequote("\"abc", 0));
		assertThrows(Exception.class,
				() -> AllowedSigners.dequote("\"abc\\\"", 0));
	}

	@Test
	public void testValidLine() throws Exception {
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "*@a.com" },
				true, null, null, null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"*@a.com cert-authority ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "*@a.com", "*@b.a.com" },
				true, null, null, null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"*@a.com,*@b.a.com cert-authority ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "foo@a.com" },
				false, null, null, null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"foo@a.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "foo@a.com" },
				false, new String[] { "foo", "bar" }, null, null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"foo@a.com namespaces=\"foo,bar\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "foo@a.com" },
				false, null, Instant.parse("2024-09-01T03:30:00.00Z"), null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"foo@a.com valid-After=\"20240901\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "*@a.com", "*@b.a.com" },
				true, new String[] { "git" },
				Instant.parse("2024-09-01T03:30:00.00Z"),
				Instant.parse("2024-09-01T12:00:00.00Z"),
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"),
				AllowedSigners.parseLine(
						"*@a.com,*@b.a.com cert-authority namespaces=\"git\" valid-after=\"20240901\" valid-before=\"202409011200Z\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertEquals(new AllowedSigners.AllowedEntry(
				new String[] { "foo@a.com" },
				false, new String[] { "git" },
				Instant.parse("2024-09-01T03:30:00.00Z"),
				Instant.parse("2024-09-01T12:00:00.00Z"),
				"ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBGxkz2AUld8eitmyIYlVV+Sot4jT3CigyBmvFRff0q4cSsKLx4x2TxGQeKKVueJEawtsUC2GNRV9FxXsTCUGcZU="),
				AllowedSigners.parseLine(
						"foo@a.com namespaces=\"git\" valid-after=\"20240901\" valid-before=\"202409011200Z\" ecdsa-sha2-nistp256   AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBGxkz2AUld8eitmyIYlVV+Sot4jT3CigyBmvFRff0q4cSsKLx4x2TxGQeKKVueJEawtsUC2GNRV9FxXsTCUGcZU="));
	}

	@Test
	public void testInvalidLine() {
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"cert-authority ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"namespaces=\"git\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"valid-after=\"20240901\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"valid-before=\"20240901\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO foo@bar.com"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"a@a.com namespaces=\"\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"a@a.com namespaces=\",,,\" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
		assertThrows(Exception.class, () -> AllowedSigners.parseLine(
				"a@a.com,,b@a.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGATOZ8PcOKdY978fzIstnZ0+FuefIWKp7wRZynQLdzO"));
	}

	@Test
	public void testSkippedLine() throws Exception {
		assertNull(AllowedSigners.parseLine(null));
		assertNull(AllowedSigners.parseLine(""));
		assertNull(AllowedSigners.parseLine("# Comment"));
	}
}
