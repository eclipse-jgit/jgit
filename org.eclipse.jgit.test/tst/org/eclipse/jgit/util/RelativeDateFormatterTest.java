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
import static org.eclipse.jgit.util.RelativeDateFormatter.YEAR_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.SECOND_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.MINUTE_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.HOUR_IN_MILLIS;
import static org.eclipse.jgit.util.RelativeDateFormatter.DAY_IN_MILLIS;

import java.util.Date;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.RelativeDateFormatter;
import org.junit.Before;
import org.junit.Test;

public class RelativeDateFormatterTest {

	@Before
	public void setUp() {
		SystemReader.setInstance(new MockSystemReader());
	}

	private void assertFormat(long ageFromNow, long timeUnit,
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
		assertFormat(1824, DAY_IN_MILLIS, "4 years, 12 months ago");
	}

	@Test
	public void testFormatYears() {
		assertFormat(5, YEAR_IN_MILLIS, "5 years ago");
		assertFormat(60, YEAR_IN_MILLIS, "60 years ago");
	}
}
