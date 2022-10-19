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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GitDateParserTest {
	@BeforeEach
	public void setUp() {
		MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
	}

	@AfterEach
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	void yesterday() throws ParseException {
		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		Date parse = GitDateParser.parse("yesterday", cal, SystemReader
				.getInstance().getLocale());
		cal.add(Calendar.DATE, -1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		assertEquals(cal.getTime(), parse);
	}

	@Test
	void never() throws ParseException {
		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		Date parse = GitDateParser.parse("never", cal, SystemReader
				.getInstance().getLocale());
		assertEquals(GitDateParser.NEVER, parse);
		parse = GitDateParser.parse("never", null);
		assertEquals(GitDateParser.NEVER, parse);
	}

	@Test
	void now() throws ParseException {
		String dateStr = "2007-02-21 15:35:00 +0100";
		Date refDate = SystemReader.getInstance()
				.getSimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(dateStr);

		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		cal.setTime(refDate);

		Date parse = GitDateParser.parse("now", cal, SystemReader.getInstance()
				.getLocale());
		assertEquals(refDate, parse);
		long t1 = SystemReader.getInstance().getCurrentTime();
		parse = GitDateParser.parse("now", null);
		long t2 = SystemReader.getInstance().getCurrentTime();
		assertTrue(t2 >= parse.getTime() && parse.getTime() >= t1);
	}

	@Test
	void weeksAgo() throws ParseException {
		String dateStr = "2007-02-21 15:35:00 +0100";
		SimpleDateFormat df = SystemReader.getInstance()
				.getSimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
		Date refDate = df.parse(dateStr);
		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		cal.setTime(refDate);

		Date parse = GitDateParser.parse("2 weeks ago", cal, SystemReader
				.getInstance().getLocale());
		assertEquals(df.parse("2007-02-07 15:35:00 +0100"), parse);
	}

	@Test
	void daysAndWeeksAgo() throws ParseException {
		String dateStr = "2007-02-21 15:35:00 +0100";
		SimpleDateFormat df = SystemReader.getInstance().getSimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss Z");
		Date refDate = df.parse(dateStr);
		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		cal.setTime(refDate);

		Date parse = GitDateParser.parse("2 weeks ago", cal, SystemReader.getInstance()
				.getLocale());
		assertEquals(df.parse("2007-02-07 15:35:00 +0100"), parse);
		parse = GitDateParser.parse("3 days 2 weeks ago", cal);
		assertEquals(df.parse("2007-02-04 15:35:00 +0100"), parse);
		parse = GitDateParser.parse("3.day.2.week.ago", cal);
		assertEquals(df.parse("2007-02-04 15:35:00 +0100"), parse);
	}

	@Test
	void iso() throws ParseException {
		String dateStr = "2007-02-21 15:35:00 +0100";
		Date exp = SystemReader.getInstance()
				.getSimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void rfc() throws ParseException {
		String dateStr = "Wed, 21 Feb 2007 15:35:00 +0100";
		Date exp = SystemReader.getInstance()
				.getSimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void shortFmt() throws ParseException {
		String dateStr = "2007-02-21";
		Date exp = SystemReader.getInstance().getSimpleDateFormat("yyyy-MM-dd")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void shortWithDots() throws ParseException {
		String dateStr = "2007.02.21";
		Date exp = SystemReader.getInstance().getSimpleDateFormat("yyyy.MM.dd")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void shortWithSlash() throws ParseException {
		String dateStr = "02/21/2007";
		Date exp = SystemReader.getInstance().getSimpleDateFormat("MM/dd/yyyy")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void shortWithDotsReverse() throws ParseException {
		String dateStr = "21.02.2007";
		Date exp = SystemReader.getInstance().getSimpleDateFormat("dd.MM.yyyy")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void defaultFmt() throws ParseException {
		String dateStr = "Wed Feb 21 15:35:00 2007 +0100";
		Date exp = SystemReader.getInstance()
				.getSimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z")
				.parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}

	@Test
	void local() throws ParseException {
		String dateStr = "Wed Feb 21 15:35:00 2007";
		Date exp = SystemReader.getInstance()
				.getSimpleDateFormat("EEE MMM dd HH:mm:ss yyyy").parse(dateStr);
		Date parse = GitDateParser.parse(dateStr, null, SystemReader
				.getInstance().getLocale());
		assertEquals(exp, parse);
	}
}
