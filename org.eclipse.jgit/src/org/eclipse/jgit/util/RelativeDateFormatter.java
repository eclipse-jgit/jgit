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

import org.eclipse.jgit.JGitText;

/**
 * Formatter to format timestamps relative to the current time using time units
 * in the format defined by {@code git log --relative-date}.
 */
public class RelativeDateFormatter {

	/**
	 * @param when
	 *            {@link Date} to format
	 * @return age of given {@link Date} compared to now formatted in the same
	 *         relative format as returned by {@code git log --relative-date}
	 */
	@SuppressWarnings("boxing")
	public static String format(Date when) {
		// convert to seconds
		long age = (System.currentTimeMillis() - when.getTime()) / 1000;

		// shouldn't happen in a perfect world
		if (age < 0)
			return JGitText.get().inTheFuture;

		// seconds
		if (age < 90)
			return MessageFormat.format(JGitText.get().secondsAgo, age);

		// convert to rounded minutes
		age = (age + 30) / 60;
		if (age < 90)
			return MessageFormat.format(JGitText.get().minutesAgo, age);

		// convert to rounded hours
		age = (age + 30) / 60;
		if (age < 36)
			return MessageFormat.format(JGitText.get().hoursAgo, age);

		// convert to rounded days
		age = (age + 12) / 24;
		if (age < 14)
			return MessageFormat.format(JGitText.get().daysAgo, age);

		// up to 10 weeks format to rounded weeks
		if (age < 70)
			return MessageFormat.format(JGitText.get().weeksAgo, (age + 3) / 7);

		// rounded months
		if (age < 365)
			return MessageFormat.format(JGitText.get().monthsAgo,
					(age + 15) / 30);

		// up to 5 years format to year, months rounded to months
		if (age < 1825) {
			long years = age / 365;
			String yearLabel = (years > 1) ? JGitText.get().years : //
					JGitText.get().year;
			long months = (age % 365 + 15) / 30;
			String monthLabel = (months > 1) ? JGitText.get().months : //
					JGitText.get().month;
			return MessageFormat.format(JGitText.get().yearsMonthsAgo,
					new Object[] { years, yearLabel, months, monthLabel });
		}

		// rounded years
		return MessageFormat.format(JGitText.get().yearsAgo, (age + 183) / 365);
	}
}