/*
 * Copyright (C) 2024 Christian Halstrick and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.EnumMap;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;

/**
 * Parses strings with time and date specifications into
 * {@link java.time.Instant}.
 *
 * When git needs to parse strings specified by the user this parser can be
 * used. One example is the parsing of the config parameter gc.pruneexpire. The
 * parser can handle only subset of what native gits approxidate parser
 * understands.
 *
 * @since 7.1
 */
public class GitTimeParser {

	private static final Map<ParseableSimpleDateFormat, DateTimeFormatter> formatCache = new EnumMap<>(
			ParseableSimpleDateFormat.class);

	// An enum of all those formats which this parser can parse with the help of
	// a DateTimeFormatter. There are other formats (e.g. the relative formats
	// like "yesterday" or "1 week ago") which this parser can parse but which
	// are not listed here because they are parsed without the help of a
	// DateTimeFormatter.
	enum ParseableSimpleDateFormat {
		ISO("yyyy-MM-dd HH:mm:ss Z"), // //$NON-NLS-1$
		RFC("EEE, dd MMM yyyy HH:mm:ss Z"), // //$NON-NLS-1$
		SHORT("yyyy-MM-dd"), // //$NON-NLS-1$
		SHORT_WITH_DOTS_REVERSE("dd.MM.yyyy"), // //$NON-NLS-1$
		SHORT_WITH_DOTS("yyyy.MM.dd"), // //$NON-NLS-1$
		SHORT_WITH_SLASH("MM/dd/yyyy"), // //$NON-NLS-1$
		DEFAULT("EEE MMM dd HH:mm:ss yyyy Z"), // //$NON-NLS-1$
		LOCAL("EEE MMM dd HH:mm:ss yyyy"); //$NON-NLS-1$

		private final String formatStr;

		ParseableSimpleDateFormat(String formatStr) {
			this.formatStr = formatStr;
		}
	}

	private GitTimeParser() {
		// This class is not supposed to be instantiated
	}

	/**
	 * Parses a string into a {@link java.time.LocalDateTime} using the default
	 * locale. Since this parser also supports relative formats (e.g.
	 * "yesterday") the caller can specify the reference date. These types of
	 * strings can be parsed:
	 * <ul>
	 * <li>"never"</li>
	 * <li>"now"</li>
	 * <li>"yesterday"</li>
	 * <li>"(x) years|months|weeks|days|hours|minutes|seconds ago"<br>
	 * Multiple specs can be combined like in "2 weeks 3 days ago". Instead of '
	 * ' one can use '.' to separate the words</li>
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
	 * @return the parsed {@link java.time.LocalDateTime}
	 * @throws java.text.ParseException
	 *             if the given dateStr was not recognized
	 */
	public static LocalDateTime parse(String dateStr) throws ParseException {
		return parse(dateStr, SystemReader.getInstance().civilNow());
	}

	/**
	 * Parses a string into a {@link java.time.Instant} using the default
	 * locale. Since this parser also supports relative formats (e.g.
	 * "yesterday") the caller can specify the reference date. These types of
	 * strings can be parsed:
	 * <ul>
	 * <li>"never"</li>
	 * <li>"now"</li>
	 * <li>"yesterday"</li>
	 * <li>"(x) years|months|weeks|days|hours|minutes|seconds ago"<br>
	 * Multiple specs can be combined like in "2 weeks 3 days ago". Instead of '
	 * ' one can use '.' to separate the words</li>
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
	 * @return the parsed {@link java.time.Instant}
	 * @throws java.text.ParseException
	 *             if the given dateStr was not recognized
	 * @since 7.2
	 */
	public static Instant parseInstant(String dateStr) throws ParseException {
		return parse(dateStr).atZone(SystemReader.getInstance().getTimeZoneId())
				.toInstant();
	}

	// Only tests seem to use this method
	static LocalDateTime parse(String dateStr, LocalDateTime now)
			throws ParseException {
		dateStr = dateStr.trim();

		if (dateStr.equalsIgnoreCase("never")) { //$NON-NLS-1$
			return LocalDateTime.MAX;
		}
		LocalDateTime ret = parseRelative(dateStr, now);
		if (ret != null) {
			return ret;
		}
		for (ParseableSimpleDateFormat f : ParseableSimpleDateFormat.values()) {
			try {
				return parseSimple(dateStr, f);
			} catch (DateTimeParseException e) {
				// simply proceed with the next parser
			}
		}
		ParseableSimpleDateFormat[] values = ParseableSimpleDateFormat.values();
		StringBuilder allFormats = new StringBuilder("\"") //$NON-NLS-1$
				.append(values[0].formatStr);
		for (int i = 1; i < values.length; i++) {
			allFormats.append("\", \"").append(values[i].formatStr); //$NON-NLS-1$
		}
		allFormats.append("\""); //$NON-NLS-1$
		throw new ParseException(
				MessageFormat.format(JGitText.get().cannotParseDate, dateStr,
						allFormats.toString()),
				0);
	}

	// tries to parse a string with the formats supported by DateTimeFormatter
	private static LocalDateTime parseSimple(String dateStr,
			ParseableSimpleDateFormat f) throws DateTimeParseException {
		DateTimeFormatter dateFormat = formatCache.computeIfAbsent(f,
				format -> DateTimeFormatter
						.ofPattern(f.formatStr)
						.withLocale(SystemReader.getInstance().getLocale()));
		TemporalAccessor parsed = dateFormat.parse(dateStr);
		return parsed.isSupported(ChronoField.HOUR_OF_DAY)
				? LocalDateTime.from(parsed)
				: LocalDate.from(parsed).atStartOfDay();
	}

	// tries to parse a string with a relative time specification
	@SuppressWarnings("nls")
	@Nullable
	private static LocalDateTime parseRelative(String dateStr,
			LocalDateTime now) {
		// check for the static words "yesterday" or "now"
		if (dateStr.equals("now")) {
			return now;
		}

		if (dateStr.equals("yesterday")) {
			return now.minusDays(1);
		}

		// parse constructs like "3 days ago", "5.week.2.day.ago"
		String[] parts = dateStr.split("\\.| ", -1);
		int partsLength = parts.length;
		// check we have an odd number of parts (at least 3) and that the last
		// part is "ago"
		if (partsLength < 3 || (partsLength & 1) == 0
				|| !parts[parts.length - 1].equals("ago")) {
			return null;
		}
		int number;
		for (int i = 0; i < parts.length - 2; i += 2) {
			try {
				number = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				return null;
			}
			if (parts[i + 1] == null) {
				return null;
			}
			switch (parts[i + 1]) {
			case "year":
			case "years":
				now = now.minusYears(number);
				break;
			case "month":
			case "months":
				now = now.minusMonths(number);
				break;
			case "week":
			case "weeks":
				now = now.minusWeeks(number);
				break;
			case "day":
			case "days":
				now = now.minusDays(number);
				break;
			case "hour":
			case "hours":
				now = now.minusHours(number);
				break;
			case "minute":
			case "minutes":
				now = now.minusMinutes(number);
				break;
			case "second":
			case "seconds":
				now = now.minusSeconds(number);
				break;
			default:
				return null;
			}
		}
		return now;
	}
}
