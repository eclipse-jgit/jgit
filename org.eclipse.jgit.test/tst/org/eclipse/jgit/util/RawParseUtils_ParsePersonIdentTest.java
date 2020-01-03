/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class RawParseUtils_ParsePersonIdentTest {

	@Test
	public void testParsePersonIdent_legalCases() {
		final Date when = new Date(1234567890000l);
		final TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertPersonIdent("Me <me@example.com> 1234567890 -0700",
				new PersonIdent("Me", "me@example.com", when, tz));

		assertPersonIdent(" Me <me@example.com> 1234567890 -0700",
				new PersonIdent(" Me", "me@example.com", when, tz));

		assertPersonIdent("A U Thor <author@example.com> 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));

		assertPersonIdent("A U Thor<author@example.com> 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));

		assertPersonIdent("A U Thor<author@example.com>1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));

		assertPersonIdent(
				" A U Thor   < author@example.com > 1234567890 -0700",
				new PersonIdent(" A U Thor  ", " author@example.com ", when, tz));

		assertPersonIdent("A U Thor<author@example.com>1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));
	}

	@Test
	public void testParsePersonIdent_fuzzyCases() {
		final Date when = new Date(1234567890000l);
		final TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertPersonIdent(
				"A U Thor <author@example.com>,  C O. Miter <comiter@example.com> 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));

		assertPersonIdent(
				"A U Thor <author@example.com> and others 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));
	}

	@Test
	public void testParsePersonIdent_incompleteCases() {
		final Date when = new Date(1234567890000l);
		final TimeZone tz = TimeZone.getTimeZone("GMT-7");

		assertPersonIdent("Me <> 1234567890 -0700", new PersonIdent("Me", "",
				when, tz));

		assertPersonIdent(" <me@example.com> 1234567890 -0700",
				new PersonIdent("", "me@example.com", when, tz));

		assertPersonIdent(" <> 1234567890 -0700", new PersonIdent("", "", when,
				tz));

		assertPersonIdent("<>", new PersonIdent("", "", 0, 0));

		assertPersonIdent(" <>", new PersonIdent("", "", 0, 0));

		assertPersonIdent("<me@example.com>", new PersonIdent("",
				"me@example.com", 0, 0));

		assertPersonIdent(" <me@example.com>", new PersonIdent("",
				"me@example.com", 0, 0));

		assertPersonIdent("Me <>", new PersonIdent("Me", "", 0, 0));

		assertPersonIdent("Me <me@example.com>", new PersonIdent("Me",
				"me@example.com", 0, 0));

		assertPersonIdent("Me <me@example.com> 1234567890", new PersonIdent(
				"Me", "me@example.com", 0, 0));

		assertPersonIdent("Me <me@example.com> 1234567890 ", new PersonIdent(
				"Me", "me@example.com", 0, 0));
	}

	@Test
	public void testParsePersonIdent_malformedCases() {
		assertPersonIdent("Me me@example.com> 1234567890 -0700", null);
		assertPersonIdent("Me <me@example.com 1234567890 -0700", null);
	}

	private static void assertPersonIdent(String line, PersonIdent expected) {
		PersonIdent actual = RawParseUtils.parsePersonIdent(line);
		assertEquals(expected, actual);
	}
}
