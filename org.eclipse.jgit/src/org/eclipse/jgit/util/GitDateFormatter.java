/*
 * Copyright (C) 2011, 2012 Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.eclipse.jgit.lib.PersonIdent;

/**
 * A utility for formatting dates according to the Git log.date formats plus
 * extensions.
 * <p>
 * The enum {@link org.eclipse.jgit.util.GitDateFormatter.Format} defines the
 * available types.
 */
public class GitDateFormatter {

	private DateTimeFormatter dateTimeFormat;

	private DateTimeFormatter dateTimeFormat2;

	private final Format format;

	/**
	 * Git and JGit formats
	 */
	public enum Format {

		/**
		 * Git format: Time and original time zone
		 */
		DEFAULT,

		/**
		 * Git format: Relative time stamp
		 */
		RELATIVE,

		/**
		 * Git format: Date and time in local time zone
		 */
		LOCAL,

		/**
		 * Git format: ISO 8601 plus time zone
		 */
		ISO,

		/**
		 * Git formt: RFC 2822 plus time zone
		 */
		RFC,

		/**
		 * Git format: YYYY-MM-DD
		 */
		SHORT,

		/**
		 * Git format: Seconds size 1970 in UTC plus time zone
		 */
		RAW,

		/**
		 * Locale dependent formatting with original time zone
		 */
		LOCALE,

		/**
		 * Locale dependent formatting in local time zone
		 */
		LOCALELOCAL
	}

	/**
	 * Create a new Git oriented date formatter
	 *
	 * @param format
	 *            a {@link org.eclipse.jgit.util.GitDateFormatter.Format}
	 *            object.
	 */
	public GitDateFormatter(Format format) {
		this.format = format;
		switch (format) {
		default:
			break;
		case DEFAULT: // Not default:
			dateTimeFormat = DateTimeFormatter.ofPattern(
					"EEE MMM dd HH:mm:ss yyyy Z", Locale.US); //$NON-NLS-1$
			break;
		case ISO:
			dateTimeFormat = DateTimeFormatter.ofPattern(
					"yyyy-MM-dd HH:mm:ss Z", //$NON-NLS-1$
					Locale.US);
			break;
		case LOCAL:
			dateTimeFormat = DateTimeFormatter.ofPattern(
					"EEE MMM dd HH:mm:ss yyyy", //$NON-NLS-1$
					Locale.US);
			break;
		case RFC:
			dateTimeFormat = DateTimeFormatter.ofPattern(
					"EEE, dd MMM yyyy HH:mm:ss Z", Locale.US); //$NON-NLS-1$
			break;
		case SHORT:
			dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", //$NON-NLS-1$
					Locale.US);
			break;
		case LOCALE:
		case LOCALELOCAL:
			dateTimeFormat = DateTimeFormatter
					.ofLocalizedDateTime(FormatStyle.MEDIUM)
					.withLocale(Locale.US);
			dateTimeFormat2 = DateTimeFormatter.ofPattern("Z", //$NON-NLS-1$
					Locale.US);
			break;
		}
	}

	/**
	 * Format committer, author or tagger ident according to this formatter's
	 * specification.
	 *
	 * @param ident
	 *            a {@link org.eclipse.jgit.lib.PersonIdent} object.
	 * @return formatted version of date, time and time zone
	 */
	@SuppressWarnings("boxing")
	public String formatDate(PersonIdent ident) {
		switch (format) {
		case RAW: {
			int offset = ident.getZoneOffset().getTotalSeconds();
			String sign = offset < 0 ? "-" : "+"; //$NON-NLS-1$ //$NON-NLS-2$
			int offset2;
			if (offset < 0) {
				offset2 = -offset;
			} else {
				offset2 = offset;
			}
			int minutes = (offset2 / 60) % 60;
			int hours = offset2 / 60 / 60;
			return String.format("%d %s%02d%02d", //$NON-NLS-1$
					ident.getWhenAsInstant().getEpochSecond(), sign, hours,
					minutes);
		}
		case RELATIVE:
			return RelativeDateFormatter.format(ident.getWhenAsInstant());
		case LOCALELOCAL:
		case LOCAL:
			return dateTimeFormat
					.withZone(SystemReader.getInstance().getTimeZoneId())
					.format(ident.getWhenAsInstant());
		case LOCALE: {
			ZoneId tz = ident.getZoneId();
			if (tz == null) {
				tz = SystemReader.getInstance().getTimeZoneId();
			}
			return dateTimeFormat.withZone(tz).format(ident.getWhenAsInstant())
					+ " " //$NON-NLS-1$
					+ dateTimeFormat2.withZone(tz)
							.format(ident.getWhenAsInstant());
		}
		default: {
			ZoneId tz = ident.getZoneId();
			if (tz == null) {
				tz = SystemReader.getInstance().getTimeZoneId();
			}
			return dateTimeFormat.withZone(tz).format(ident.getWhenAsInstant());
		}
		}
	}
}
