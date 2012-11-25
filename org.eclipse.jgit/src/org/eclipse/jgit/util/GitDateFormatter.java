/*
 * Copyright (C) 2011, 2012 Robin Rosenberg
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;

/**
 * A utility for formatting dates according to the Git log.date formats plus
 * extensions.
 * <p>
 * The enum {@link Format} defines the available types.
 */
public class GitDateFormatter {

	private DateFormat dateTimeInstance;

	private DateFormat dateTimeInstance2;

	private final Format format;

	/**
	 * Git and JGit formats
	 */
	static public enum Format {

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
			dateTimeInstance = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy",
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
	 * @return formatted version of date, time and time zone
	 */
	@SuppressWarnings("boxing")
	public String formatDate(PersonIdent ident) {
		switch (format) {
		case RAW:
			int offset = ident.getTimeZoneOffset();
			String sign = offset < 0 ? "-" : "+"; //$NON-NLS-1$
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
