/*
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.eclipse.jgit.util.RelativeDateFormatter;
import org.junit.Test;

public class RelativeDateFormatterTest {
	final int SECOND = 1000;

	final int MINUTE = 60 * SECOND;

	final int HOUR = 60 * MINUTE;

	final long DAY = 24 * HOUR;

	final long MONTH = 30 * DAY;

	final long YEAR = 365 * DAY;

	private void assertFormat(long ageFromNow, long timeUnit,
			String expectedFormat) {
		Date d = new Date(System.currentTimeMillis() - ageFromNow * timeUnit);
		String s = RelativeDateFormatter.format(d);
		assertEquals(expectedFormat, s);
	}

	@Test
	public void testFuture() {
		assertFormat(-100, YEAR, "in the future");
		assertFormat(-1, SECOND, "in the future");
	}

	@Test
	public void testFormatSeconds() {
		assertFormat(1, SECOND, "1 seconds ago");
		assertFormat(89, SECOND, "89 seconds ago");
	}

	@Test
	public void testFormatMinutes() {
		assertFormat(90, SECOND, "2 minutes ago");
		assertFormat(3, MINUTE, "3 minutes ago");
		assertFormat(60, MINUTE, "60 minutes ago");
		assertFormat(89, MINUTE, "89 minutes ago");
	}

	@Test
	public void testFormatHours() {
		assertFormat(90, MINUTE, "2 hours ago");
		assertFormat(149, MINUTE, "2 hours ago");
		assertFormat(35, HOUR, "35 hours ago");
	}

	@Test
	public void testFormatDays() {
		assertFormat(36, HOUR, "2 days ago");
		assertFormat(13, DAY, "13 days ago");
	}

	@Test
	public void testFormatWeeks() {
		assertFormat(14, DAY, "2 weeks ago");
		assertFormat(69, DAY, "10 weeks ago");
	}

	@Test
	public void testFormatMonths() {
		assertFormat(70, DAY, "2 months ago");
		assertFormat(75, DAY, "3 months ago");
		assertFormat(364, DAY, "12 months ago");
	}

	@Test
	public void testFormatYearsMonths() {
		assertFormat(366, DAY, "1 year, 0 month ago");
		assertFormat(380, DAY, "1 year, 1 month ago");
		assertFormat(410, DAY, "1 year, 2 months ago");
		assertFormat(2, YEAR, "2 years, 0 month ago");
		assertFormat(1824, DAY, "4 years, 12 months ago");
	}

	@Test
	public void testFormatYears() {
		assertFormat(5, YEAR, "5 years ago");
		assertFormat(60, YEAR, "60 years ago");
	}
}
