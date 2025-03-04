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

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class RawParseUtils_ParsePersonIdentTest {

	@Test
	public void testParsePersonIdent_legalCases() {
		Instant when = Instant.ofEpochMilli(1234567890000L);
		ZoneId tz = ZoneOffset.ofHours(-7);

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
		Instant when = Instant.ofEpochMilli(1234567890000L);
		ZoneId tz = ZoneOffset.ofHours(-7);

		assertPersonIdent(
				"A U Thor <author@example.com>,  C O. Miter <comiter@example.com> 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));

		assertPersonIdent(
				"A U Thor <author@example.com> and others 1234567890 -0700",
				new PersonIdent("A U Thor", "author@example.com", when, tz));
	}

	@Test
	public void testParsePersonIdent_incompleteCases() {
		Instant when = Instant.ofEpochMilli(1234567890000L);
		ZoneId tz = ZoneOffset.ofHours(-7);

		assertPersonIdent("Me <> 1234567890 -0700", new PersonIdent("Me", "",
				when, tz));

		assertPersonIdent(" <me@example.com> 1234567890 -0700",
				new PersonIdent("", "me@example.com", when, tz));

		assertPersonIdent(" <> 1234567890 -0700", new PersonIdent("", "", when,
				tz));

		assertPersonIdent("<>", new PersonIdent("", "", EPOCH, UTC));

		assertPersonIdent(" <>", new PersonIdent("", "", EPOCH, UTC));

		assertPersonIdent("<me@example.com>", new PersonIdent("",
				"me@example.com", EPOCH, UTC));

		assertPersonIdent(" <me@example.com>", new PersonIdent("",
				"me@example.com", EPOCH, UTC));

		assertPersonIdent("Me <>", new PersonIdent("Me", "", EPOCH, UTC));

		assertPersonIdent("Me <me@example.com>", new PersonIdent("Me",
				"me@example.com", EPOCH, UTC));

		assertPersonIdent("Me <me@example.com> 1234567890", new PersonIdent(
				"Me", "me@example.com", EPOCH, UTC));

		assertPersonIdent("Me <me@example.com> 1234567890 ", new PersonIdent(
				"Me", "me@example.com", EPOCH, UTC));
	}

	@Test
	public void testParsePersonIdent_malformedCases() {
		assertPersonIdent("Me me@example.com> 1234567890 -0700", null);
		assertPersonIdent("Me <me@example.com 1234567890 -0700", null);
	}

	@Test
	public void testParsePersonIdent_badTz() {
		PersonIdent tooBig = RawParseUtils
				.parsePersonIdent("Me <me@example.com> 1234567890 +8315");
		assertEquals(tooBig.getZoneOffset().getTotalSeconds(), 0);

		PersonIdent tooSmall = RawParseUtils
				.parsePersonIdent("Me <me@example.com> 1234567890 -8315");
		assertEquals(tooSmall.getZoneOffset().getTotalSeconds(), 0);

		PersonIdent notATime = RawParseUtils
				.parsePersonIdent("Me <me@example.com> 1234567890 -0370");
		assertEquals(notATime.getZoneOffset().getTotalSeconds(), 0);
	}

	private static void assertPersonIdent(String line, PersonIdent expected) {
		PersonIdent actual = RawParseUtils.parsePersonIdent(line);
		assertEquals(expected, actual);
	}
}
