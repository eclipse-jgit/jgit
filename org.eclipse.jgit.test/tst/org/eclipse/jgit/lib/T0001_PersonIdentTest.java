/*
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
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

