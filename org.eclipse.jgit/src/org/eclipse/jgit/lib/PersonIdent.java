/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * A combination of a person identity and time in Git.
 *
 * Git combines Name + email + time + time zone to specify who wrote or
 * committed something.
 */
public class PersonIdent implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Get timezone object for the given offset.
	 *
	 * @param tzOffset
	 *            timezone offset as in {@link #getTimeZoneOffset()}.
	 * @return time zone object for the given offset.
	 * @since 4.1
	 */
	public static TimeZone getTimeZone(int tzOffset) {
		StringBuilder tzId = new StringBuilder(8);
		tzId.append("GMT"); //$NON-NLS-1$
		appendTimezone(tzId, tzOffset);
		return TimeZone.getTimeZone(tzId.toString());
	}

	/**
	 * Format a timezone offset.
	 *
	 * @param r
	 *            string builder to append to.
	 * @param offset
	 *            timezone offset as in {@link #getTimeZoneOffset()}.
	 * @since 4.1
	 */
	public static void appendTimezone(StringBuilder r, int offset) {
		final char sign;
		final int offsetHours;
		final int offsetMins;

		if (offset < 0) {
			sign = '-';
			offset = -offset;
		} else {
			sign = '+';
		}

		offsetHours = offset / 60;
		offsetMins = offset % 60;

		r.append(sign);
		if (offsetHours < 10) {
			r.append('0');
		}
		r.append(offsetHours);
		if (offsetMins < 10) {
			r.append('0');
		}
		r.append(offsetMins);
	}

	/**
	 * Sanitize the given string for use in an identity and append to output.
	 * <p>
	 * Trims whitespace from both ends and special characters {@code \n < >} that
	 * interfere with parsing; appends all other characters to the output.
	 * Analogous to the C git function {@code strbuf_addstr_without_crud}.
	 *
	 * @param r
	 *            string builder to append to.
	 * @param str
	 *            input string.
	 * @since 4.4
	 */
	public static void appendSanitized(StringBuilder r, String str) {
		// Trim any whitespace less than \u0020 as in String#trim().
		int i = 0;
		while (i < str.length() && str.charAt(i) <= ' ') {
			i++;
		}
		int end = str.length();
		while (end > i && str.charAt(end - 1) <= ' ') {
			end--;
		}

		for (; i < end; i++) {
			char c = str.charAt(i);
			switch (c) {
				case '\n':
				case '<':
				case '>':
					continue;
				default:
					r.append(c);
					break;
			}
		}
	}

	private final String name;

	private final String emailAddress;

	private final long when;

	private final int tzOffset;

	/**
	 * Creates new PersonIdent from config info in repository, with current time.
	 * This new PersonIdent gets the info from the default committer as available
	 * from the configuration.
	 *
	 * @param repo a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public PersonIdent(Repository repo) {
		this(repo.getConfig().get(UserConfig.KEY));
	}

	/**
	 * Copy a {@link org.eclipse.jgit.lib.PersonIdent}.
	 *
	 * @param pi
	 *            Original {@link org.eclipse.jgit.lib.PersonIdent}
	 */
	public PersonIdent(PersonIdent pi) {
		this(pi.getName(), pi.getEmailAddress());
	}

	/**
	 * Construct a new {@link org.eclipse.jgit.lib.PersonIdent} with current
	 * time.
	 *
	 * @param aName
	 *            a {@link java.lang.String} object.
	 * @param aEmailAddress
	 *            a {@link java.lang.String} object.
	 */
	public PersonIdent(String aName, String aEmailAddress) {
		this(aName, aEmailAddress, SystemReader.getInstance().getCurrentTime());
	}

	/**
	 * Construct a new {@link org.eclipse.jgit.lib.PersonIdent} with current
	 * time.
	 *
	 * @param aName
	 *            a {@link java.lang.String} object.
	 * @param aEmailAddress
	 *            a {@link java.lang.String} object.
	 * @param when
	 *            a {@link org.eclipse.jgit.util.time.ProposedTimestamp} object.
	 * @since 4.6
	 */
	public PersonIdent(String aName, String aEmailAddress,
			ProposedTimestamp when) {
		this(aName, aEmailAddress, when.millis());
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 *
	 * @param pi
	 *            original {@link org.eclipse.jgit.lib.PersonIdent}
	 * @param when
	 *            local time
	 * @param tz
	 *            time zone
	 */
	public PersonIdent(PersonIdent pi, Date when, TimeZone tz) {
		this(pi.getName(), pi.getEmailAddress(), when, tz);
	}

	/**
	 * Copy a {@link org.eclipse.jgit.lib.PersonIdent}, but alter the clone's
	 * time stamp
	 *
	 * @param pi
	 *            original {@link org.eclipse.jgit.lib.PersonIdent}
	 * @param aWhen
	 *            local time
	 */
	public PersonIdent(PersonIdent pi, Date aWhen) {
		this(pi.getName(), pi.getEmailAddress(), aWhen.getTime(), pi.tzOffset);
	}

	/**
	 * Construct a PersonIdent from simple data
	 *
	 * @param aName a {@link java.lang.String} object.
	 * @param aEmailAddress a {@link java.lang.String} object.
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final Date aWhen, final TimeZone aTZ) {
		this(aName, aEmailAddress, aWhen.getTime(), aTZ.getOffset(aWhen
				.getTime()) / (60 * 1000));
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 *
	 * @param pi
	 *            original {@link org.eclipse.jgit.lib.PersonIdent}
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(PersonIdent pi, long aWhen, int aTZ) {
		this(pi.getName(), pi.getEmailAddress(), aWhen, aTZ);
	}

	private PersonIdent(final String aName, final String aEmailAddress,
			long when) {
		this(aName, aEmailAddress, when, SystemReader.getInstance()
				.getTimezone(when));
	}

	private PersonIdent(UserConfig config) {
		this(config.getCommitterName(), config.getCommitterEmail());
	}

	/**
	 * Construct a {@link org.eclipse.jgit.lib.PersonIdent}.
	 * <p>
	 * Whitespace in the name and email is preserved for the lifetime of this
	 * object, but are trimmed by {@link #toExternalString()}. This means that
	 * parsing the result of {@link #toExternalString()} may not return an
	 * equivalent instance.
	 *
	 * @param aName
	 *            a {@link java.lang.String} object.
	 * @param aEmailAddress
	 *            a {@link java.lang.String} object.
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final long aWhen, final int aTZ) {
		if (aName == null)
			throw new IllegalArgumentException(
					JGitText.get().personIdentNameNonNull);
		if (aEmailAddress == null)
			throw new IllegalArgumentException(
					JGitText.get().personIdentEmailNonNull);
		name = aName;
		emailAddress = aEmailAddress;
		when = aWhen;
		tzOffset = aTZ;
	}

	/**
	 * Get name of person
	 *
	 * @return Name of person
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get email address of person
	 *
	 * @return email address of person
	 */
	public String getEmailAddress() {
		return emailAddress;
	}

	/**
	 * Get timestamp
	 *
	 * @return timestamp
	 */
	public Date getWhen() {
		return new Date(when);
	}

	/**
	 * Get this person's declared time zone
	 *
	 * @return this person's declared time zone; null if time zone is unknown.
	 */
	public TimeZone getTimeZone() {
		return getTimeZone(tzOffset);
	}

	/**
	 * Get this person's declared time zone as minutes east of UTC.
	 *
	 * @return this person's declared time zone as minutes east of UTC. If the
	 *         timezone is to the west of UTC it is negative.
	 */
	public int getTimeZoneOffset() {
		return tzOffset;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Hashcode is based only on the email address and timestamp.
	 */
	@Override
	public int hashCode() {
		int hc = getEmailAddress().hashCode();
		hc *= 31;
		hc += (int) (when / 1000L);
		return hc;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object o) {
		if (o instanceof PersonIdent) {
			final PersonIdent p = (PersonIdent) o;
			return getName().equals(p.getName())
					&& getEmailAddress().equals(p.getEmailAddress())
					&& when / 1000L == p.when / 1000L;
		}
		return false;
	}

	/**
	 * Format for Git storage.
	 *
	 * @return a string in the git author format
	 */
	public String toExternalString() {
		final StringBuilder r = new StringBuilder();
		appendSanitized(r, getName());
		r.append(" <"); //$NON-NLS-1$
		appendSanitized(r, getEmailAddress());
		r.append("> "); //$NON-NLS-1$
		r.append(when / 1000);
		r.append(' ');
		appendTimezone(r, tzOffset);
		return r.toString();
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("nls")
	public String toString() {
		final StringBuilder r = new StringBuilder();
		final SimpleDateFormat dtfmt;
		dtfmt = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
		dtfmt.setTimeZone(getTimeZone());

		r.append("PersonIdent[");
		r.append(getName());
		r.append(", ");
		r.append(getEmailAddress());
		r.append(", ");
		r.append(dtfmt.format(Long.valueOf(when)));
		r.append("]");

		return r.toString();
	}
}

