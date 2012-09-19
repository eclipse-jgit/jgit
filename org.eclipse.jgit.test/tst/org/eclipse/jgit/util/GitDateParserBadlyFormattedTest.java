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

import static org.junit.Assert.fail;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;

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
			GitDateParser.parse(dateStr, ref);
			fail("The expected ParseException while parsing '" + dateStr
					+ "' did not occur.");
		} catch (ParseException e) {
			// expected
		}
	}

	@Theory
	public void badlyFormattedWithoutRef() {
		try {
			GitDateParser.parse(dateStr, null);
			fail("The expected ParseException while parsing '" + dateStr
					+ "' did not occur.");
		} catch (ParseException e) {
			// expected
		}
	}
}
