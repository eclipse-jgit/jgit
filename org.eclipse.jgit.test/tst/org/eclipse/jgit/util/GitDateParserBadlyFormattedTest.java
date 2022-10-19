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

import static org.junit.jupiter.api.Assertions.fail;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests which assert that unparseable Strings lead to ParseExceptions
 */
public class GitDateParserBadlyFormattedTest {

	private static final String DISPLAY_NAME = "dateStr=''{0}''";

	@BeforeEach
	public void setUp() {
		MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
	}

	@AfterEach
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	private static String[] getDataPoints() {
		return new String[] { "", "1970", "3000.3000.3000", "3 yesterday ago",
				"now yesterday ago", "yesterdays", "3.day. 2.week.ago",
				"day ago", "Gra Feb 21 15:35:00 2007 +0100",
				"Sun Feb 21 15:35:00 2007 +0100",
				"Wed Feb 21 15:35:00 Grand +0100" };
	}

	@ParameterizedTest(name = DISPLAY_NAME)
	@MethodSource("getDataPoints")
	public void badlyFormattedWithExplicitRef(String dateStr) {
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

	@ParameterizedTest(name = DISPLAY_NAME)
	@MethodSource("getDataPoints")
	public void badlyFormattedWithoutRef(String dateStr) {
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
