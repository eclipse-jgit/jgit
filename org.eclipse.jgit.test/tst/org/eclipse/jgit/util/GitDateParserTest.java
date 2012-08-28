/*
 * Copyright (C) 2012, Christian Halstrick
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

package org.eclipse.jgit.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.eclipse.jgit.junit.MockSystemReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GitDateParserTest {
	SimpleDateFormat df;
	private Date refDate;
	private Date refDateMidnight;

	@Before
	public void setUp() throws ParseException {
		MockSystemReader mockSystemReader = new MockSystemReader() {
			@Override
			public long getCurrentTime() {
				return 1318125997291L;
			}
		};
		SystemReader.setInstance(mockSystemReader);
		df = SystemReader.getInstance().getSimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss Z");
		refDate = df.parse("2007-02-21 15:35:00 +0100");
		refDateMidnight = df.parse("2007-02-21 00:00:00 +0100");
	}

	@Test
	public void badlyFormatted() {
		Assert.assertNull(GitDateParser.parse("foo", refDate));
		Assert.assertNull(GitDateParser.parse("", refDate));
		Assert.assertNull(GitDateParser.parse("", null));
		Assert.assertNull(GitDateParser.parse("1970", refDate));
		Assert.assertNull(GitDateParser.parse("3000.3000.3000", refDate));
		Assert.assertNull(GitDateParser.parse("3 yesterday ago", refDate));
		Assert.assertNull(GitDateParser.parse("now yesterday ago", refDate));
		Assert.assertNull(GitDateParser.parse("yesterdays", refDate));
		Assert.assertNull(GitDateParser.parse("3.day. 2.week.ago", refDate));
		Assert.assertNull(GitDateParser.parse("day ago", refDate));
		Assert.assertNull(GitDateParser.parse("Gra Feb 21 15:35:00 2007 +0100",
				null));
		Assert.assertNull(GitDateParser.parse("Sun Feb 21 15:35:00 2007 +0100",
				null));
		Assert.assertNull(GitDateParser.parse(
				"Wed Feb 21 15:35:00 Grand +0100",
				null));
	}

	@Test
	public void yesterday() {
		GregorianCalendar cal = new GregorianCalendar(SystemReader
				.getInstance().getTimeZone(), SystemReader.getInstance()
				.getLocale());
		Date parse = GitDateParser.parse("yesterday", refDate);
		cal.setTime(refDate);
		cal.add(Calendar.DATE, -1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		Assert.assertEquals(cal.getTime(), parse);
	}

	@Test
	public void now() {
		Date parse = GitDateParser.parse("now", refDate);
		Assert.assertEquals(refDate, parse);
		long t1 = SystemReader.getInstance().getCurrentTime();
		parse = GitDateParser.parse("now", null);
		long t2 = SystemReader.getInstance().getCurrentTime();
		Assert.assertTrue(t2 >= parse.getTime() && parse.getTime() >= t1);
	}

	@Test
	public void weeksAgo() throws ParseException {
		Date parse = GitDateParser.parse("2 weeks ago", refDate);
		Assert.assertEquals(df.parse("2007-02-07 15:35:00 +0100"), parse);
	}

	@Test
	public void daysAndWeeksAgo() throws ParseException {
		Date parse = GitDateParser.parse("3 days 2 weeks ago", refDate);
		Assert.assertEquals(df.parse("2007-02-04 15:35:00 +0100"), parse);
		parse = GitDateParser.parse("3.day.2.week.ago", refDate);
		Assert.assertEquals(df.parse("2007-02-04 15:35:00 +0100"), parse);
	}

	@Test
	public void iso() {
		Date parse = GitDateParser.parse("2007-02-21 15:35:00 +0100", null);
		Assert.assertEquals(refDate, parse);
	}

	@Test
	public void rfc() {
		Date parse = GitDateParser.parse("Wed, 21 Feb 2007 15:35:00 +0100",
				null);
		Assert.assertEquals(refDate, parse);
	}

	@Test
	public void shortFmt() {
		Date parse = GitDateParser.parse("2007-02-21", null);
		Assert.assertEquals(refDateMidnight, parse);
	}

	@Test
	public void shortWithDots() {
		Date parse = GitDateParser.parse("2007.02.21", null);
		Assert.assertEquals(refDateMidnight, parse);
	}

	@Test
	public void shortWithSlash() {
		Date parse = GitDateParser.parse("02/21/2007", null);
		Assert.assertEquals(refDateMidnight, parse);
	}

	@Test
	public void shortWithDotsReverse() {
		Date parse = GitDateParser.parse("21.02.2007", null);
		Assert.assertEquals(refDateMidnight, parse);
	}

	@Test
	public void defaultFmt() {
		Date parse = GitDateParser
				.parse("Wed Feb 21 15:35:00 2007 +0100", null);
		Assert.assertEquals(refDate, parse);
	}

	@Test
	public void local() {
		Date parse = GitDateParser.parse("Wed Feb 21 15:35:00 2007", null);
		Assert.assertEquals(refDate, parse);
	}
}
