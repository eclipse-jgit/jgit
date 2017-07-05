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

import java.text.MessageFormat;
import java.util.Date;

import org.eclipse.jgit.internal.JGitText;

/**
 * Formatter to format timestamps relative to the current time using time units
 * in the format defined by {@code git log --relative-date}.
 */
public class RelativeDateFormatter {
	final static long SECOND_IN_MILLIS = 1000;

	final static long MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS;

	final static long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;

	final static long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;

	final static long WEEK_IN_MILLIS = 7 * DAY_IN_MILLIS;

	final static long MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS;

	final static long YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS;

	/**
	 * @param when
	 *            {@link Date} to format
	 * @return age of given {@link Date} compared to now formatted in the same
	 *         relative format as returned by {@code git log --relative-date}
	 */
	@SuppressWarnings("boxing")
	public static String format(Date when) {

		long ageMillis = SystemReader.getInstance().getCurrentTime()
				- when.getTime();

		// shouldn't happen in a perfect world
		if (ageMillis < 0)
			return JGitText.get().inTheFuture;

		// seconds
		if (ageMillis < upperLimit(MINUTE_IN_MILLIS))
			return MessageFormat.format(JGitText.get().secondsAgo,
					round(ageMillis, SECOND_IN_MILLIS));

		// minutes
		if (ageMillis < upperLimit(HOUR_IN_MILLIS))
			return MessageFormat.format(JGitText.get().minutesAgo,
					round(ageMillis, MINUTE_IN_MILLIS));

		// hours
		if (ageMillis < upperLimit(DAY_IN_MILLIS))
			return MessageFormat.format(JGitText.get().hoursAgo,
					round(ageMillis, HOUR_IN_MILLIS));

		// up to 14 days use days
		if (ageMillis < 14 * DAY_IN_MILLIS)
			return MessageFormat.format(JGitText.get().daysAgo,
					round(ageMillis, DAY_IN_MILLIS));

		// up to 10 weeks use weeks
		if (ageMillis < 10 * WEEK_IN_MILLIS)
			return MessageFormat.format(JGitText.get().weeksAgo,
					round(ageMillis, WEEK_IN_MILLIS));

		// months
		if (ageMillis < YEAR_IN_MILLIS)
			return MessageFormat.format(JGitText.get().monthsAgo,
					round(ageMillis, MONTH_IN_MILLIS));

		// up to 5 years use "year, months" rounded to months
		if (ageMillis < 5 * YEAR_IN_MILLIS) {
			long years = ageMillis / YEAR_IN_MILLIS;
			String yearLabel = (years > 1) ? JGitText.get().years : //
					JGitText.get().year;
			long months = round(ageMillis % YEAR_IN_MILLIS, MONTH_IN_MILLIS);
			String monthLabel = (months > 1) ? JGitText.get().months : //
					(months == 1 ? JGitText.get().month : ""); //$NON-NLS-1$
			return MessageFormat.format(
					months == 0 ? JGitText.get().years0MonthsAgo : JGitText
							.get().yearsMonthsAgo,
					new Object[] { years, yearLabel, months, monthLabel });
		}

		// years
		return MessageFormat.format(JGitText.get().yearsAgo,
				round(ageMillis, YEAR_IN_MILLIS));
	}

	private static long upperLimit(long unit) {
		long limit = unit + unit / 2;
		return limit;
	}

	private static long round(long n, long unit) {
		long rounded = (n + unit / 2) / unit;
		return rounded;
	}
}
