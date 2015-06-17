/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.PushCertificateIdent.parse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class PushCertificateIdentTest {
	@Test
	public void parseValid() throws Exception {
		String raw = "A U. Thor <a_u_thor@example.com> 1218123387 +0700";
		PushCertificateIdent ident = parse(raw);
		assertEquals(raw, ident.getRaw());
		assertEquals("A U. Thor <a_u_thor@example.com>", ident.getUserId());
		assertEquals("A U. Thor", ident.getName());
		assertEquals("a_u_thor@example.com", ident.getEmailAddress());
		assertEquals(1218123387000L, ident.getWhen().getTime());
		assertEquals(TimeZone.getTimeZone("GMT+0700"), ident.getTimeZone());
		assertEquals(7 * 60, ident.getTimeZoneOffset());
	}

	@Test
	public void trimName() throws Exception {
		String name = "A U. Thor";
		String email = "a_u_thor@example.com";
		String rest = "<a_u_thor@example.com> 1218123387 +0700";

		checkNameEmail(name, email, name + rest);
		checkNameEmail(name, email, " " + name + rest);
		checkNameEmail(name, email, "  " + name + rest);
		checkNameEmail(name, email, name + " " + rest);
		checkNameEmail(name, email, name + "  " + rest);
		checkNameEmail(name, email, " " + name + " " + rest);
	}

	@Test
	public void noEmail() throws Exception {
		String name = "A U. Thor";
		String rest = " 1218123387 +0700";

		checkNameEmail(name, null, name + rest);
		checkNameEmail(name, null, " " + name + rest);
		checkNameEmail(name, null, "  " + name + rest);
		checkNameEmail(name, null, name + " " + rest);
		checkNameEmail(name, null, name + "  " + rest);
		checkNameEmail(name, null, " " + name + " " + rest);
	}

	@Test
	public void exoticUserId() throws Exception {
		String rest = " 218123387 +0700";
		assertEquals("", parse(rest).getUserId());

		String id = "foo\n\0bar\uabcd\n ";
		assertEquals(id, parse(id + rest).getUserId());
	}

	@Test
	public void fuzzyCasesMatchPersonIdent() throws Exception {
		// See RawParseUtils_ParsePersonIdentTest#testParsePersonIdent_fuzzyCases()
		Date when = new Date(1234567890000l);
		TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertMatchesPersonIdent(
				"A U Thor <author@example.com>,  C O. Miter <comiter@example.com> 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz),
				"A U Thor <author@example.com>,  C O. Miter <comiter@example.com>");
		assertMatchesPersonIdent(
				"A U Thor <author@example.com> and others 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz),
				"A U Thor <author@example.com> and others");
	}

	@Test
	public void incompleteCasesMatchPersonIdent() throws Exception {
		// See RawParseUtils_ParsePersonIdentTest#testParsePersonIdent_incompleteCases()
		Date when = new Date(1234567890000l);
		TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertMatchesPersonIdent(
				"Me <> 1234567890 -0700",
				new PersonIdent("Me", "", when, tz),
				"Me <>");
		assertMatchesPersonIdent(
				" <me@example.com> 1234567890 -0700",
				new PersonIdent("", "me@example.com", when, tz),
				" <me@example.com>");
		assertMatchesPersonIdent(
				" <> 1234567890 -0700",
				new PersonIdent("", "", when, tz),
				" <>");
		assertMatchesPersonIdent(
				"<>",
				new PersonIdent("", "", 0, 0),
				"<>");
		assertMatchesPersonIdent(
				" <>",
				new PersonIdent("", "", 0, 0),
				" <>");
		assertMatchesPersonIdent(
				"<me@example.com>",
				new PersonIdent("", "me@example.com", 0, 0),
				"<me@example.com>");
		assertMatchesPersonIdent(
				" <me@example.com>",
				new PersonIdent("", "me@example.com", 0, 0),
				" <me@example.com>");
		assertMatchesPersonIdent(
				"Me <>",
				new PersonIdent("Me", "", 0, 0),
				"Me <>");
		assertMatchesPersonIdent(
				"Me <me@example.com>",
				new PersonIdent("Me", "me@example.com", 0, 0),
				"Me <me@example.com>");
		assertMatchesPersonIdent(
				"Me <me@example.com> 1234567890",
				new PersonIdent("Me", "me@example.com", 0, 0),
				"Me <me@example.com>");
		assertMatchesPersonIdent(
				"Me <me@example.com> 1234567890 ",
				new PersonIdent("Me", "me@example.com", 0, 0),
				"Me <me@example.com>");
	}

	private static void assertMatchesPersonIdent(String raw,
			PersonIdent expectedPersonIdent, String expectedUserId) {
		PushCertificateIdent certIdent = PushCertificateIdent.parse(raw);
		assertNotNull(raw);
		assertEquals(raw, certIdent.getRaw());
		assertEquals(expectedPersonIdent.getName(), certIdent.getName());
		assertEquals(expectedPersonIdent.getEmailAddress(),
				certIdent.getEmailAddress());
		assertEquals(expectedPersonIdent.getWhen(), certIdent.getWhen());
		assertEquals(expectedPersonIdent.getTimeZoneOffset(),
				certIdent.getTimeZoneOffset());
		assertEquals(expectedUserId, certIdent.getUserId());
	}

	private static void checkNameEmail(String expectedName, String expectedEmail,
			String raw) {
		PushCertificateIdent ident = parse(raw);
		assertNotNull(ident);
		assertEquals(raw, ident.getRaw());
		assertEquals(expectedName, ident.getName());
		assertEquals(expectedEmail, ident.getEmailAddress());
	}
}
