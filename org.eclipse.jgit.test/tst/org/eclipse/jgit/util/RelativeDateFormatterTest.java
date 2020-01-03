/*
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.eclipse.jgit.util.RelativeDateFormatter.DAY_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.HOUR_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.MINUTE_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.SECOND_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.YEAR_IN_MILLIS;
import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RelativeDateFormatterTest {

	@Before
	public void setUp() {
		SystemReader.setInstance(new MockSystemReader());
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	private static void assertFormat(long ageFromNow, long timeUnit,
			String expectedFormat) {
		Date d = new Date(SystemReader.getInstance().getCurrentTime()
				- ageFromNow * timeUnit);
		String s = RelativeDateFormatter.format(d);
		assertEquals(expectedFormat, s);
	}

	@Test
	public void testFuture() {
		assertFormat(-100, YEAR_IN_MILLIS, "in the future");
		assertFormat(-1, SECOND_IN_MILLIS, "in the future");
	}

	@Test
	public void testFormatSeconds() {
		assertFormat(1, SECOND_IN_MILLIS, "1 seconds ago");
		assertFormat(89, SECOND_IN_MILLIS, "89 seconds ago");
	}

	@Test
	public void testFormatMinutes() {
		assertFormat(90, SECOND_IN_MILLIS, "2 minutes ago");
		assertFormat(3, MINUTE_IN_MILLIS, "3 minutes ago");
		assertFormat(60, MINUTE_IN_MILLIS, "60 minutes ago");
		assertFormat(89, MINUTE_IN_MILLIS, "89 minutes ago");
	}

	@Test
	public void testFormatHours() {
		assertFormat(90, MINUTE_IN_MILLIS, "2 hours ago");
		assertFormat(149, MINUTE_IN_MILLIS, "2 hours ago");
		assertFormat(35, HOUR_IN_MILLIS, "35 hours ago");
	}

	@Test
	public void testFormatDays() {
		assertFormat(36, HOUR_IN_MILLIS, "2 days ago");
		assertFormat(13, DAY_IN_MILLIS, "13 days ago");
	}

	@Test
	public void testFormatWeeks() {
		assertFormat(14, DAY_IN_MILLIS, "2 weeks ago");
		assertFormat(69, DAY_IN_MILLIS, "10 weeks ago");
	}

	@Test
	public void testFormatMonths() {
		assertFormat(70, DAY_IN_MILLIS, "2 months ago");
		assertFormat(75, DAY_IN_MILLIS, "3 months ago");
		assertFormat(364, DAY_IN_MILLIS, "12 months ago");
	}

	@Test
	public void testFormatYearsMonths() {
		assertFormat(366, DAY_IN_MILLIS, "1 year ago");
		assertFormat(380, DAY_IN_MILLIS, "1 year, 1 month ago");
		assertFormat(410, DAY_IN_MILLIS, "1 year, 2 months ago");
		assertFormat(2, YEAR_IN_MILLIS, "2 years ago");
	}

	@Test
	public void testFullYearMissingSomeDays() {
		// avoid "x year(s), 12 months", as humans would always round this up to
		// "x+1 years"
		assertFormat(5 * 365 + 1, DAY_IN_MILLIS, "5 years ago");
		assertFormat(2 * 365 - 10, DAY_IN_MILLIS, "2 years ago");
	}

	@Test
	public void testFormatYears() {
		assertFormat(5, YEAR_IN_MILLIS, "5 years ago");
		assertFormat(60, YEAR_IN_MILLIS, "60 years ago");
	}
}
