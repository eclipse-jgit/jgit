/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		Date when = new Date(1234567890000L);
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
		Date when = new Date(1234567890000L);
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

	@Test
	public void timezoneRange_hours_ok() {
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +1000");
		assertEquals(10 * 60, ident.getTimeZoneOffset());
		assertEquals(10 * 60 * 60 * 1000, ident.getTimeZone().getOffset(1218123387));
	}

	/*
	 * Real timezones are in the [-12, 14] range of hours.
	 *
	 * Before 7.2, PushCertificateIdent used java.util.Timezone, which
	 * accepted hours between 0 and 24. Now java.util.ZoneOffset accepts
	 * only hours between 0 and 18.
	 *
	 * Invalid timezones default to UTC.
	 */
	@Test
	public void getTimeZone_hours_unrealistic_utc() {
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +2300");
		assertEquals(0, ident.getTimeZoneOffset());
		assertEquals(0, ident.getTimeZone().getOffset(1218123387));
	}

	@Test
	public void getTimeZone_hours_tooBig_utc() {
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +2500");
		assertEquals(0, ident.getTimeZoneOffset());
		assertEquals(0, ident.getTimeZone().getOffset(1218123387));
	}

	@Test
	public void getTimeZone_minutes_ok() {
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +0059");
		assertEquals(59, ident.getTimeZoneOffset());
		assertEquals(59 * 60 * 1000,
				ident.getTimeZone().getOffset(1218123387));
	}
	@Test
	public void getTimeZone_minutes_tooBig_utc() {
		// Before 7.2, this was converted into minutes and then to GMT+0101
		// This is an invalid timezone and now becomes UTC.
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +0061");
		assertEquals(0, ident.getTimeZoneOffset());
		assertEquals(0, ident.getTimeZone().getOffset(1218123387));
	}

	@Test
	public void getTimeZone_minutes_tooBigMoreThanOneHour_utc() {
		// Before 7.2, this was converted into 99 minutes and then to GMT+0139
		// This is an invalid timezone and now becomes UTC.
		PushCertificateIdent ident = PushCertificateIdent
				.parse("A U. Thor <a_u_thor@example.com> 1218123387 +0199");
		assertEquals(0, ident.getTimeZoneOffset());
		assertEquals(0, ident.getTimeZone().getOffset(1218123387));
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
