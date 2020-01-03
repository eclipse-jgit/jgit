/*
 * Copyright (C) 2012, Christian Halstrick and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.junit.Assert.fail;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests which assert that unparseable Strings lead to ParseExceptions
 */
@RunWith(Theories.class)
public class GitDateParserBadlyFormattedTest {
	private String dateStr;

	@Before
	public void setUp() {
		MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	public GitDateParserBadlyFormattedTest(String dateStr) {
		this.dateStr = dateStr;
	}

	@DataPoints
	static public String[] getDataPoints() {
		return new String[] { "", "1970", "3000.3000.3000", "3 yesterday ago",
				"now yesterday ago", "yesterdays", "3.day. 2.week.ago",
				"day ago", "Gra Feb 21 15:35:00 2007 +0100",
				"Sun Feb 21 15:35:00 2007 +0100",
				"Wed Feb 21 15:35:00 Grand +0100" };
	}

	@Theory
	public void badlyFormattedWithExplicitRef() {
		Calendar ref = new GregorianCalendar(SystemReader.getInstance()
				.getTimeZone(), SystemReader.getInstance().getLocale());
		try {
			GitDateParser.parse(dateStr, ref, SystemReader.getInstance()
					.getLocale());
			fail("The expected ParseException while parsing '" + dateStr
					+ "' did not occur.");
		} catch (ParseException e) {
			// expected
		}
	}

	@Theory
	public void badlyFormattedWithoutRef() {
		try {
			GitDateParser.parse(dateStr, null, SystemReader.getInstance()
					.getLocale());
			fail("The expected ParseException while parsing '" + dateStr
					+ "' did not occur.");
		} catch (ParseException e) {
			// expected
		}
	}
}
