/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.util.SystemReader;

/**
 * A combination of a person identity and time in Git.
 *
 * Git combines Name + email + time + time zone to specify who wrote or
 * committed something.
 */
public class PersonIdent implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String name;

	private final String emailAddress;

	private final long when;

	private final int tzOffset;

	/**
	 * Creates new PersonIdent from config info in repository, with current time.
	 * This new PersonIdent gets the info from the default committer as available
	 * from the configuration.
	 *
	 * @param repo
	 */
	public PersonIdent(final Repository repo) {
		final UserConfig config = repo.getConfig().get(UserConfig.KEY);
		name = config.getCommitterName();
		emailAddress = config.getCommitterEmail();
		when = SystemReader.getInstance().getCurrentTime();
		tzOffset = SystemReader.getInstance().getTimezone(when);
	}

	/**
	 * Copy a {@link PersonIdent}.
	 *
	 * @param pi
	 *            Original {@link PersonIdent}
	 */
	public PersonIdent(final PersonIdent pi) {
		this(pi.getName(), pi.getEmailAddress());
	}

	/**
	 * Construct a new {@link PersonIdent} with current time.
	 *
	 * @param aName
	 * @param aEmailAddress
	 */
	public PersonIdent(final String aName, final String aEmailAddress) {
		name = aName;
		emailAddress = aEmailAddress;
		when = SystemReader.getInstance().getCurrentTime();
		tzOffset = SystemReader.getInstance().getTimezone(when);
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 *
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param when
	 *            local time
	 * @param tz
	 *            time zone
	 */
	public PersonIdent(final PersonIdent pi, final Date when, final TimeZone tz) {
		this(pi.getName(), pi.getEmailAddress(), when, tz);
	}

	/**
	 * Copy a {@link PersonIdent}, but alter the clone's time stamp
	 *
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param aWhen
	 *            local time
	 */
	public PersonIdent(final PersonIdent pi, final Date aWhen) {
		name = pi.getName();
		emailAddress = pi.getEmailAddress();
		when = aWhen.getTime();
		tzOffset = pi.tzOffset;
	}

	/**
	 * Construct a PersonIdent from simple data
	 *
	 * @param aName
	 * @param aEmailAddress
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final Date aWhen, final TimeZone aTZ) {
		name = aName;
		emailAddress = aEmailAddress;
		when = aWhen.getTime();
		tzOffset = aTZ.getOffset(when) / (60 * 1000);
	}

	/**
	 * Construct a {@link PersonIdent}
	 *
	 * @param aName
	 * @param aEmailAddress
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final String aName, final String aEmailAddress,
			final long aWhen, final int aTZ) {
		name = aName;
		emailAddress = aEmailAddress;
		when = aWhen;
		tzOffset = aTZ;
	}

	/**
	 * Copy a PersonIdent, but alter the clone's time stamp
	 *
	 * @param pi
	 *            original {@link PersonIdent}
	 * @param aWhen
	 *            local time stamp
	 * @param aTZ
	 *            time zone
	 */
	public PersonIdent(final PersonIdent pi, final long aWhen, final int aTZ) {
		name = pi.getName();
		emailAddress = pi.getEmailAddress();
		when = aWhen;
		tzOffset = aTZ;
	}

	/**
	 * @return Name of person
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return email address of person
	 */
	public String getEmailAddress() {
		return emailAddress;
	}

	/**
	 * @return timestamp
	 */
	public Date getWhen() {
		return new Date(when);
	}

	/**
	 * @return this person's declared time zone; null if time zone is unknown.
	 */
	public TimeZone getTimeZone() {
		StringBuilder tzId = new StringBuilder(8);
		tzId.append("GMT");
		appendTimezone(tzId);
		return TimeZone.getTimeZone(tzId.toString());
	}

	/**
	 * @return this person's declared time zone as minutes east of UTC. If the
	 *         timezone is to the west of UTC it is negative.
	 */
	public int getTimeZoneOffset() {
		return tzOffset;
	}

	public int hashCode() {
		int hc = getEmailAddress().hashCode();
		hc *= 31;
		hc += (int) (when / 1000L);
		return hc;
	}

	public boolean equals(final Object o) {
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
		r.append(getName());
		r.append(" <");
		r.append(getEmailAddress());
		r.append("> ");
		r.append(when / 1000);
		r.append(' ');
		appendTimezone(r);
		return r.toString();
	}

	private void appendTimezone(final StringBuilder r) {
		int offset = tzOffset;
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
