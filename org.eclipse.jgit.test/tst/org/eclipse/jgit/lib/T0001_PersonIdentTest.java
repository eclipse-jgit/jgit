/*
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

public class T0001_PersonIdentTest {

	@Test
	public void test001_NewIdent() {
		final PersonIdent p = new PersonIdent("A U Thor", "author@example.com",
				new Date(1142878501000L), TimeZone.getTimeZone("EST"));
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
		assertEquals("A U Thor <author@example.com> 1142878501 -0500",
				p.toExternalString());
	}

	@Test
	public void test002_NewIdent() {
		final PersonIdent p = new PersonIdent("A U Thor", "author@example.com",
				new Date(1142878501000L), TimeZone.getTimeZone("GMT+0230"));
		assertEquals("A U Thor", p.getName());
		assertEquals("author@example.com", p.getEmailAddress());
		assertEquals(1142878501000L, p.getWhen().getTime());
		assertEquals("A U Thor <author@example.com> 1142878501 +0230",
				p.toExternalString());
	}

	@SuppressWarnings("unused")
	@Test(expected = IllegalArgumentException.class)
	public void nullForNameShouldThrowIllegalArgumentException() {
		new PersonIdent(null, "author@example.com");
	}

	@SuppressWarnings("unused")
	@Test(expected = IllegalArgumentException.class)
	public void nullForEmailShouldThrowIllegalArgumentException() {
		new PersonIdent("A U Thor", null);
	}

	@Test
	public void testToExternalStringTrimsNameAndEmail() throws Exception {
		PersonIdent personIdent = new PersonIdent(" \u0010A U Thor  ",
				"  author@example.com \u0009");

		assertEquals(" \u0010A U Thor  ", personIdent.getName());
		assertEquals("  author@example.com \u0009", personIdent.getEmailAddress());

		String externalString = personIdent.toExternalString();
		assertTrue(externalString.startsWith("A U Thor <author@example.com>"));
	}

	@Test
	public void testToExternalStringTrimsAllWhitespace() {
		String ws = "  \u0001 \n ";
		PersonIdent personIdent = new PersonIdent(ws, ws);
		assertEquals(ws, personIdent.getName());
		assertEquals(ws, personIdent.getEmailAddress());

		String externalString = personIdent.toExternalString();
		assertTrue(externalString.startsWith(" <>"));
	}

	@Test
	public void testToExternalStringTrimsOtherBadCharacters() {
		String name = " Foo\r\n<Bar> ";
		String email = " Baz>\n\u1234<Quux ";
		PersonIdent personIdent = new PersonIdent(name, email);
		assertEquals(name, personIdent.getName());
		assertEquals(email, personIdent.getEmailAddress());

		String externalString = personIdent.toExternalString();
		assertTrue(externalString.startsWith("Foo\rBar <Baz\u1234Quux>"));
	}

	@Test
	public void testEmptyNameAndEmail() {
		PersonIdent personIdent = new PersonIdent("", "");
		assertEquals("", personIdent.getName());
		assertEquals("", personIdent.getEmailAddress());

		String externalString = personIdent.toExternalString();
		assertTrue(externalString.startsWith(" <>"));
	}

	@Test
	public void testAppendSanitized() {
		StringBuilder r = new StringBuilder();
		PersonIdent.appendSanitized(r, " Baz>\n\u1234<Quux ");
		assertEquals("Baz\u1234Quux", r.toString());
	}
}

