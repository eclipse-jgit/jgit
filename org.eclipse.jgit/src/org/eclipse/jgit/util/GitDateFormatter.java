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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;

/**
 * A utility for formatting dates according to the Git log.date formats plus
 * extensions.
 * <p>
 * The enum {@link org.eclipse.jgit.util.GitDateFormatter.Format} defines the
 * available types.
 */
public class GitDateFormatter {

	private DateFormat dateTimeInstance;

	private DateFormat dateTimeInstance2;

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
			dateTimeInstance = new SimpleDateFormat(
					"EEE MMM dd HH:mm:ss yyyy Z", Locale.US); //$NON-NLS-1$
			break;
		case ISO:
			dateTimeInstance = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", //$NON-NLS-1$
					Locale.US);
			break;
		case LOCAL:
			dateTimeInstance = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", //$NON-NLS-1$
					Locale.US);
			break;
		case RFC:
			dateTimeInstance = new SimpleDateFormat(
					"EEE, dd MMM yyyy HH:mm:ss Z", Locale.US); //$NON-NLS-1$
			break;
		case SHORT:
			dateTimeInstance = new SimpleDateFormat("yyyy-MM-dd", Locale.US); //$NON-NLS-1$
			break;
		case LOCALE:
		case LOCALELOCAL:
			SystemReader systemReader = SystemReader.getInstance();
			dateTimeInstance = systemReader.getDateTimeInstance(
					DateFormat.DEFAULT, DateFormat.DEFAULT);
			dateTimeInstance2 = systemReader.getSimpleDateFormat("Z"); //$NON-NLS-1$
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
		case RAW:
			int offset = ident.getTimeZoneOffset();
			String sign = offset < 0 ? "-" : "+"; //$NON-NLS-1$ //$NON-NLS-2$
			int offset2;
			if (offset < 0)
				offset2 = -offset;
			else
				offset2 = offset;
			int hours = offset2 / 60;
			int minutes = offset2 % 60;
			return String.format("%d %s%02d%02d", //$NON-NLS-1$
					ident.getWhen().getTime() / 1000, sign, hours, minutes);
		case RELATIVE:
			return RelativeDateFormatter.format(ident.getWhen());
		case LOCALELOCAL:
		case LOCAL:
			dateTimeInstance.setTimeZone(SystemReader.getInstance()
					.getTimeZone());
			return dateTimeInstance.format(ident.getWhen());
		case LOCALE:
			TimeZone tz = ident.getTimeZone();
			if (tz == null)
				tz = SystemReader.getInstance().getTimeZone();
			dateTimeInstance.setTimeZone(tz);
			dateTimeInstance2.setTimeZone(tz);
			return dateTimeInstance.format(ident.getWhen()) + " " //$NON-NLS-1$
					+ dateTimeInstance2.format(ident.getWhen());
		default:
			tz = ident.getTimeZone();
			if (tz == null)
				tz = SystemReader.getInstance().getTimeZone();
			dateTimeInstance.setTimeZone(ident.getTimeZone());
			return dateTimeInstance.format(ident.getWhen());
		}
	}
}
