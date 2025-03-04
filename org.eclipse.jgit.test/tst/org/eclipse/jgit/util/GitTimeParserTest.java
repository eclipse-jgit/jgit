/*
 * Copyright (C) 2024, Christian Halstrick and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitTimeParserTest {
	MockSystemReader mockSystemReader;

	@Before
	public void setUp() {
		mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
	}

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	public void yesterday() throws ParseException {
		LocalDateTime parse = GitTimeParser.parse("yesterday");

		LocalDateTime now = SystemReader.getInstance().civilNow();
		assertEquals(Period.between(parse.toLocalDate(), now.toLocalDate()),
				Period.ofDays(1));
	}

	@Test
	public void never() throws ParseException {
		LocalDateTime parse = GitTimeParser.parse("never");
		assertEquals(LocalDateTime.MAX, parse);
	}

	@Test
	public void now_pointInTime() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 15:35:00 +0100");

		LocalDateTime parsedNow = GitTimeParser.parse("now", aTime);

		assertEquals(aTime, parsedNow);
	}

	@Test
	public void now_systemTime() throws ParseException {
		LocalDateTime firstNow = GitTimeParser.parse("now");
		assertEquals(SystemReader.getInstance().civilNow(), firstNow);
		mockSystemReader.tick(10);
		LocalDateTime secondNow = GitTimeParser.parse("now");
		assertTrue(secondNow.isAfter(firstNow));
	}

	@Test
	public void weeksAgo() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 15:35:00 +0100");

		LocalDateTime parse = GitTimeParser.parse("2 weeks ago", aTime);
		assertEquals(asLocalDateTime("2007-02-07 15:35:00 +0100"), parse);
	}

	@Test
	public void daysAndWeeksAgo() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 15:35:00 +0100");

		LocalDateTime twoWeeksAgoActual = GitTimeParser.parse("2 weeks ago",
				aTime);

		LocalDateTime twoWeeksAgoExpected = asLocalDateTime(
				"2007-02-07 15:35:00 +0100");
		assertEquals(twoWeeksAgoExpected, twoWeeksAgoActual);

		LocalDateTime combinedWhitespace = GitTimeParser
				.parse("3 days 2 weeks ago", aTime);
		LocalDateTime combinedWhitespaceExpected = asLocalDateTime(
				"2007-02-04 15:35:00 +0100");
		assertEquals(combinedWhitespaceExpected, combinedWhitespace);

		LocalDateTime combinedDots = GitTimeParser.parse("3.day.2.week.ago",
				aTime);
		LocalDateTime combinedDotsExpected = asLocalDateTime(
				"2007-02-04 15:35:00 +0100");
		assertEquals(combinedDotsExpected, combinedDots);
	}

	@Test
	public void hoursAgo() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 17:35:00 +0100");

		LocalDateTime twoHoursAgoActual = GitTimeParser.parse("2 hours ago",
				aTime);

		LocalDateTime twoHoursAgoExpected = asLocalDateTime(
				"2007-02-21 15:35:00 +0100");
		assertEquals(twoHoursAgoExpected, twoHoursAgoActual);
	}

	@Test
	public void hoursAgo_acrossDay() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 00:35:00 +0100");

		LocalDateTime twoHoursAgoActual = GitTimeParser.parse("2 hours ago",
				aTime);

		LocalDateTime twoHoursAgoExpected = asLocalDateTime(
				"2007-02-20 22:35:00 +0100");
		assertEquals(twoHoursAgoExpected, twoHoursAgoActual);
	}

	@Test
	public void minutesHoursAgoCombined() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-04 15:35:00 +0100");

		LocalDateTime combinedWhitespace = GitTimeParser
				.parse("3 hours 2 minutes ago", aTime);
		LocalDateTime combinedWhitespaceExpected = asLocalDateTime(
				"2007-02-04 12:33:00 +0100");
		assertEquals(combinedWhitespaceExpected, combinedWhitespace);

		LocalDateTime combinedDots = GitTimeParser
				.parse("3.hours.2.minutes.ago", aTime);
		LocalDateTime combinedDotsExpected = asLocalDateTime(
				"2007-02-04 12:33:00 +0100");
		assertEquals(combinedDotsExpected, combinedDots);
	}

	@Test
	public void minutesAgo() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 17:35:10 +0100");

		LocalDateTime twoMinutesAgo = GitTimeParser.parse("2 minutes ago",
				aTime);

		LocalDateTime twoMinutesAgoExpected = asLocalDateTime(
				"2007-02-21 17:33:10 +0100");
		assertEquals(twoMinutesAgoExpected, twoMinutesAgo);
	}

	@Test
	public void minutesAgo_acrossDay() throws ParseException {
		LocalDateTime aTime = asLocalDateTime("2007-02-21 00:35:10 +0100");

		LocalDateTime minutesAgoActual = GitTimeParser.parse("40 minutes ago",
				aTime);

		LocalDateTime minutesAgoExpected = asLocalDateTime(
				"2007-02-20 23:55:10 +0100");
		assertEquals(minutesAgoExpected, minutesAgoActual);
	}

	@Test
	public void iso() throws ParseException {
		String dateStr = "2007-02-21 15:35:00 +0100";

		LocalDateTime actual = GitTimeParser.parse(dateStr);

		LocalDateTime expected = asLocalDateTime(dateStr);
		assertEquals(expected, actual);
	}

	@Test
	public void rfc() throws ParseException {
		String dateStr = "Wed, 21 Feb 2007 15:35:00 +0100";

		LocalDateTime actual = GitTimeParser.parse(dateStr);

		LocalDateTime expected = asLocalDateTime(dateStr,
				"EEE, dd MMM yyyy HH:mm:ss Z");
		assertEquals(expected, actual);
	}

	@Test
	public void shortFmt() throws ParseException {
		assertParsing("2007-02-21", "yyyy-MM-dd");
	}

	@Test
	public void shortWithDots() throws ParseException {
		assertParsing("2007.02.21", "yyyy.MM.dd");
	}

	@Test
	public void shortWithSlash() throws ParseException {
		assertParsing("02/21/2007", "MM/dd/yyyy");
	}

	@Test
	public void shortWithDotsReverse() throws ParseException {
		assertParsing("21.02.2007", "dd.MM.yyyy");
	}

	@Test
	public void defaultFmt() throws ParseException {
		assertParsing("Wed Feb 21 15:35:00 2007 +0100",
				"EEE MMM dd HH:mm:ss yyyy Z");
	}

	@Test
	public void local() throws ParseException {
		assertParsing("Wed Feb 21 15:35:00 2007", "EEE MMM dd HH:mm:ss yyyy");
	}

	private static void assertParsing(String dateStr, String format)
			throws ParseException {
		LocalDateTime actual = GitTimeParser.parse(dateStr);

		LocalDateTime expected = asLocalDateTime(dateStr, format);
		assertEquals(expected, actual);
	}

	private static LocalDateTime asLocalDateTime(String dateStr) {
		return asLocalDateTime(dateStr, "yyyy-MM-dd HH:mm:ss Z");
	}

	private static LocalDateTime asLocalDateTime(String dateStr,
			String pattern) {
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
		TemporalAccessor ta = fmt
				.withZone(SystemReader.getInstance().getTimeZoneId())
				.withLocale(SystemReader.getInstance().getLocale())
				.parse(dateStr);
		return ta.isSupported(ChronoField.HOUR_OF_DAY) ? LocalDateTime.from(ta)
				: LocalDate.from(ta).atStartOfDay();
	}
}
