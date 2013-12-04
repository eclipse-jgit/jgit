/*
 * Copyright (C) 2012 Christian Halstrick
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jgit.internal.JGitText;

/**
 * Parses strings with time and date specifications into {@link Date}.
 *
 * When git needs to parse strings specified by the user this parser can be
 * used. One example is the parsing of the config parameter gc.pruneexpire. The
 * parser can handle only subset of what native gits approxidate parser
 * understands.
 */
public class GitDateParser {
	/**
	 * The Date representing never. Though this is a concrete value, most
	 * callers are adviced to avoid depending on the actual value.
	 */
	public static final Date NEVER = new Date(Long.MAX_VALUE);

	// Since SimpleDateFormat instances are expensive to instantiate they should
	// be cached. Since they are also not threadsafe they are cached using
	// ThreadLocal.
	private static ThreadLocal<Map<Locale, Map<ParseableSimpleDateFormat, SimpleDateFormat>>> formatCache =
			new ThreadLocal<Map<Locale, Map<ParseableSimpleDateFormat, SimpleDateFormat>>>() {

		protected Map<Locale, Map<ParseableSimpleDateFormat, SimpleDateFormat>> initialValue() {
			return new HashMap<Locale, Map<ParseableSimpleDateFormat, SimpleDateFormat>>();
		}
	};

	// Gets an instance of a SimpleDateFormat for the specified locale. If there
	// is not already an appropriate instance in the (ThreadLocal) cache then
	// create one and put it into the cache.
	private static SimpleDateFormat getDateFormat(ParseableSimpleDateFormat f,
			Locale locale) {
		Map<Locale, Map<ParseableSimpleDateFormat, SimpleDateFormat>> cache = formatCache
				.get();
		Map<ParseableSimpleDateFormat, SimpleDateFormat> map = cache
				.get(locale);
		if (map == null) {
			map = new HashMap<ParseableSimpleDateFormat, SimpleDateFormat>();
			cache.put(locale, map);
			return getNewSimpleDateFormat(f, locale, map);
		}
		SimpleDateFormat dateFormat = map.get(f);
		if (dateFormat != null)
			return dateFormat;
		SimpleDateFormat df = getNewSimpleDateFormat(f, locale, map);
		return df;
	}

	private static SimpleDateFormat getNewSimpleDateFormat(
			ParseableSimpleDateFormat f, Locale locale,
			Map<ParseableSimpleDateFormat, SimpleDateFormat> map) {
		SimpleDateFormat df = SystemReader.getInstance().getSimpleDateFormat(
				f.formatStr, locale);
		map.put(f, df);
		return df;
	}

	// An enum of all those formats which this parser can parse with the help of
	// a SimpleDateFormat. There are other formats (e.g. the relative formats
	// like "yesterday" or "1 week ago") which this parser can parse but which
	// are not listed here because they are parsed without the help of a
	// SimpleDateFormat.
	enum ParseableSimpleDateFormat {
		ISO("yyyy-MM-dd HH:mm:ss Z"), // //$NON-NLS-1$
		RFC("EEE, dd MMM yyyy HH:mm:ss Z"), // //$NON-NLS-1$
		SHORT("yyyy-MM-dd"), // //$NON-NLS-1$
		SHORT_WITH_DOTS_REVERSE("dd.MM.yyyy"), // //$NON-NLS-1$
		SHORT_WITH_DOTS("yyyy.MM.dd"), // //$NON-NLS-1$
		SHORT_WITH_SLASH("MM/dd/yyyy"), // //$NON-NLS-1$
		DEFAULT("EEE MMM dd HH:mm:ss yyyy Z"), // //$NON-NLS-1$
		LOCAL("EEE MMM dd HH:mm:ss yyyy");

		String formatStr;

		private ParseableSimpleDateFormat(String formatStr) {
			this.formatStr = formatStr;
		}
	}

	/**
	 * Parses a string into a {@link Date} using the default locale. Since this
	 * parser also supports relative formats (e.g. "yesterday") the caller can
	 * specify the reference date. These types of strings can be parsed:
	 * <ul>
	 * <li>"never"</li>
	 * <li>"now"</li>
	 * <li>"yesterday"</li>
	 * <li>"(x) years|months|weeks|days|hours|minutes|seconds ago"<br>
	 * Multiple specs can be combined like in "2 weeks 3 days ago". Instead of
	 * ' ' one can use '.' to seperate the words</li>
	 * <li>"yyyy-MM-dd HH:mm:ss Z" (ISO)</li>
	 * <li>"EEE, dd MMM yyyy HH:mm:ss Z" (RFC)</li>
	 * <li>"yyyy-MM-dd"</li>
	 * <li>"yyyy.MM.dd"</li>
	 * <li>"MM/dd/yyyy",</li>
	 * <li>"dd.MM.yyyy"</li>
	 * <li>"EEE MMM dd HH:mm:ss yyyy Z" (DEFAULT)</li>
	 * <li>"EEE MMM dd HH:mm:ss yyyy" (LOCAL)</li>
	 * </ul>
	 *
	 * @param dateStr
	 *            the string to be parsed
	 * @param now
	 *            the base date which is used for the calculation of relative
	 *            formats. E.g. if baseDate is "25.8.2012" then parsing of the
	 *            string "1 week ago" would result in a date corresponding to
	 *            "18.8.2012". This is used when a JGit command calls this
	 *            parser often but wants a consistent starting point for calls.<br>
	 *            If set to <code>null</code> then the current time will be used
	 *            instead.
	 * @return the parsed {@link Date}
	 * @throws ParseException
	 *             if the given dateStr was not recognized
	 */
	public static Date parse(String dateStr, Calendar now)
			throws ParseException {
		return parse(dateStr, now, Locale.getDefault());
	}

	/**
	 * Parses a string into a {@link Date} using the given locale. Since this
	 * parser also supports relative formats (e.g. "yesterday") the caller can
	 * specify the reference date. These types of strings can be parsed:
	 * <ul>
	 * <li>"never"</li>
	 * <li>"now"</li>
	 * <li>"yesterday"</li>
	 * <li>"(x) years|months|weeks|days|hours|minutes|seconds ago"<br>
	 * Multiple specs can be combined like in "2 weeks 3 days ago". Instead of
	 * ' ' one can use '.' to seperate the words</li>
	 * <li>"yyyy-MM-dd HH:mm:ss Z" (ISO)</li>
	 * <li>"EEE, dd MMM yyyy HH:mm:ss Z" (RFC)</li>
	 * <li>"yyyy-MM-dd"</li>
	 * <li>"yyyy.MM.dd"</li>
	 * <li>"MM/dd/yyyy",</li>
	 * <li>"dd.MM.yyyy"</li>
	 * <li>"EEE MMM dd HH:mm:ss yyyy Z" (DEFAULT)</li>
	 * <li>"EEE MMM dd HH:mm:ss yyyy" (LOCAL)</li>
	 * </ul>
	 *
	 * @param dateStr
	 *            the string to be parsed
	 * @param now
	 *            the base date which is used for the calculation of relative
	 *            formats. E.g. if baseDate is "25.8.2012" then parsing of the
	 *            string "1 week ago" would result in a date corresponding to
	 *            "18.8.2012". This is used when a JGit command calls this
	 *            parser often but wants a consistent starting point for calls.<br>
	 *            If set to <code>null</code> then the current time will be used
	 *            instead.
	 * @param locale
	 *            locale to be used to parse the date string
	 * @return the parsed {@link Date}
	 * @throws ParseException
	 *             if the given dateStr was not recognized
	 * @since 3.2
	 */
	public static Date parse(String dateStr, Calendar now, Locale locale)
			throws ParseException {
		dateStr = dateStr.trim();
		Date ret;

		if ("never".equalsIgnoreCase(dateStr)) //$NON-NLS-1$
			return NEVER;
		ret = parse_relative(dateStr, now);
		if (ret != null)
			return ret;
		for (ParseableSimpleDateFormat f : ParseableSimpleDateFormat.values()) {
			try {
				return parse_simple(dateStr, f, locale);
			} catch (ParseException e) {
				// simply proceed with the next parser
			}
		}
		ParseableSimpleDateFormat[] values = ParseableSimpleDateFormat.values();
		StringBuilder allFormats = new StringBuilder("\"") //$NON-NLS-1$
				.append(values[0].formatStr);
		for (int i = 1; i < values.length; i++)
			allFormats.append("\", \"").append(values[i].formatStr); //$NON-NLS-1$
		allFormats.append("\""); //$NON-NLS-1$
		throw new ParseException(MessageFormat.format(
				JGitText.get().cannotParseDate, dateStr, allFormats.toString()), 0);
	}

	// tries to parse a string with the formats supported by SimpleDateFormat
	private static Date parse_simple(String dateStr,
			ParseableSimpleDateFormat f, Locale locale)
			throws ParseException {
		SimpleDateFormat dateFormat = getDateFormat(f, locale);
		dateFormat.setLenient(false);
		return dateFormat.parse(dateStr);
	}

	// tries to parse a string with a relative time specification
	private static Date parse_relative(String dateStr, Calendar now) {
		Calendar cal;
		SystemReader sysRead = SystemReader.getInstance();

		// check for the static words "yesterday" or "now"
		if ("now".equals(dateStr)) { //$NON-NLS-1$
			return ((now == null) ? new Date(sysRead.getCurrentTime()) : now
					.getTime());
		}

		if (now == null) {
			cal = new GregorianCalendar(sysRead.getTimeZone(),
					sysRead.getLocale());
			cal.setTimeInMillis(sysRead.getCurrentTime());
		} else
			cal = (Calendar) now.clone();

		if ("yesterday".equals(dateStr)) { //$NON-NLS-1$
			cal.add(Calendar.DATE, -1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			return cal.getTime();
		}

		// parse constructs like "3 days ago", "5.week.2.day.ago"
		String[] parts = dateStr.split("\\.| "); //$NON-NLS-1$
		int partsLength = parts.length;
		// check we have an odd number of parts (at least 3) and that the last
		// part is "ago"
		if (partsLength < 3 || (partsLength & 1) == 0
				|| !"ago".equals(parts[parts.length - 1])) //$NON-NLS-1$
			return null;
		int number;
		for (int i = 0; i < parts.length - 2; i += 2) {
			try {
				number = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				return null;
			}
			if ("year".equals(parts[i + 1]) || "years".equals(parts[i + 1])) //$NON-NLS-1$ //$NON-NLS-2$
				cal.add(Calendar.YEAR, -number);
			else if ("month".equals(parts[i + 1]) //$NON-NLS-1$
					|| "months".equals(parts[i + 1])) //$NON-NLS-1$
				cal.add(Calendar.MONTH, -number);
			else if ("week".equals(parts[i + 1]) //$NON-NLS-1$
					|| "weeks".equals(parts[i + 1])) //$NON-NLS-1$
				cal.add(Calendar.WEEK_OF_YEAR, -number);
			else if ("day".equals(parts[i + 1]) || "days".equals(parts[i + 1])) //$NON-NLS-1$ //$NON-NLS-2$
				cal.add(Calendar.DATE, -number);
			else if ("hour".equals(parts[i + 1]) //$NON-NLS-1$
					|| "hours".equals(parts[i + 1])) //$NON-NLS-1$
				cal.add(Calendar.HOUR_OF_DAY, -number);
			else if ("minute".equals(parts[i + 1]) //$NON-NLS-1$
					|| "minutes".equals(parts[i + 1])) //$NON-NLS-1$
				cal.add(Calendar.MINUTE, -number);
			else if ("second".equals(parts[i + 1]) //$NON-NLS-1$
					|| "seconds".equals(parts[i + 1])) //$NON-NLS-1$
				cal.add(Calendar.SECOND, -number);
			else
				return null;
		}
		return cal.getTime();
	}
}
