/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.eclipse.jgit.util.RawParseUtils.lastIndexOfTrim;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Identity in a push certificate.
 * <p>
 * This is similar to a {@link PersonIdent} in that it contains a name,
 * timestamp, and timezone offset, but differs in the following ways:
 * <ul>
 * <li>It is always assumed to be parsed from UTF-8, since any UTF-8 string is a
 *   valid OpenPGP User ID (5.11).</li>
 * <li>It is not guaranteed to contain a name and email portion, since any UTF-8
 *   string is a valid OpenPGP User ID (RFC4880 5.1.1). The raw User ID is
 *   always available as {@link #getUserId()}, but {@link #getName()} and {@link
 *   #getEmailAddress()} may return null.</li>
 * <li>The raw text from which the identity was parsed is available with {@link
 *   #getRaw()}. This is necessary for losslessly reconstructing the signed push
 *   certificate payload.</li>
 * <li>
 * </ul>
 *
 * @since 4.1
 */
public class PushCertificateIdent {
	/**
	 * Parse an identity from a string.
	 * <p>
	 * A valid identity must end in a timestamp and timezone offset, and
	 * optionally a newline. If a single trailing newline is present, it is
	 * stripped and will not be returned by {@link #getRaw()} on the resulting
	 * identity.
	 * <p>
	 * Spaces are trimmed when parsing the timestamp and timezone offset, with one
	 * exception. The timestamp must be preceded by a single space, and the rest
	 * of the string prior to that space (including any additional whitespace) is
	 * treated as the OpenPGP User ID.
	 *
	 * @param str
	 *            string to parse.
	 * @return identity, or null if the timestamp and timezone offset could not be
	 *         parsed.
	 * @since 4.1
	 */
	public static PushCertificateIdent parse(String str) {
		MutableInteger p = new MutableInteger();
		byte[] raw = str.getBytes(UTF_8);
		if (raw[raw.length - 1] == '\n') {
			str = str.substring(0, str.length() - 1);
		}
		int tzBegin = raw.length - 1;
		if (raw[tzBegin] == '\n') {
			tzBegin--;
		}
		tzBegin = lastIndexOfTrim(raw, ' ', tzBegin);
		if (tzBegin < 0 || raw[tzBegin] != ' ') {
			return new PushCertificateIdent(str, str, 0, 0);
		}
		int whenBegin = tzBegin++;
		int tz = RawParseUtils.parseTimeZoneOffset(raw, tzBegin, p);
		boolean hasTz = p.value != tzBegin;

		whenBegin = lastIndexOfTrim(raw, ' ', whenBegin);
		if (whenBegin < 0 || raw[whenBegin] != ' ') {
			return new PushCertificateIdent(str, str, 0, 0);
		}
		int idEnd = whenBegin++;
		long when = RawParseUtils.parseLongBase10(raw, whenBegin, p);
		boolean hasWhen = p.value != whenBegin;

		if (hasTz && hasWhen) {
			idEnd = whenBegin - 1;
		} else {
			// If either tz or when are non-numeric, mimic parsePersonIdent behavior and
			// set them both to zero.
			tz = 0;
			when = 0;
			if (hasTz && !hasWhen) {
				// Only one trailing numeric field; assume User ID ends before this
				// field, but discard its value.
				idEnd = tzBegin - 1;
			} else {
				// No trailing numeric fields; User ID is whole raw value.
				idEnd = raw.length;
			}
		}
		String id = new String(raw, 0, idEnd, UTF_8);

		return new PushCertificateIdent(str, id, when * 1000L, tz);
	}

	private final String raw;
	private final String userId;
	private final long when;
	private final int tzOffset;

	/**
	 * Construct a new identity from an OpenPGP User ID.
	 *
	 * @param userId
	 *            OpenPGP User ID; any UTF-8 string.
	 * @param when
	 *            local time.
	 * @param tzOffset
	 *            timezone offset; see {@link #getTimeZoneOffset()}.
	 */
	public PushCertificateIdent(String userId, long when, int tzOffset) {
		this.userId = userId;
		this.when = when;
		this.tzOffset = tzOffset;
		StringBuilder sb = new StringBuilder(userId).append(' ').append(when / 1000)
				.append(' ');
		PersonIdent.appendTimezone(sb, tzOffset);
		raw = sb.toString();
	}

	private PushCertificateIdent(String raw, String userId, long when,
			int tzOffset) {
		this.raw = raw;
		this.userId = userId;
		this.when = when;
		this.tzOffset = tzOffset;
	}

	/**
	 * Get the raw string from which this identity was parsed.
	 * <p>
	 * The only modification done by {@link #parse(String)} is to strip a single
	 * trailing newline if present.
	 * <p>
	 * If the string was constructed manually, a suitable canonical string is
	 * returned.
	 * <p>
	 * For the purposes of bytewise comparisons with other OpenPGP IDs, the string
	 * must be encoded as UTF-8.
	 *
	 * @return the raw string.
	 * @since 4.1
	 */
	public String getRaw() {
		return raw;
	}

	/**
	 * @return the OpenPGP User ID, which may be any string.
	 * @since 4.1
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * @return the name portion of the User ID. If no email address would be
	 *         parsed by {@link #getEmailAddress()}, returns the full User ID with
	 *         spaces trimmed.
	 * @since 4.1
	 */
	public String getName() {
		int nameEnd = userId.indexOf('<');
		if (nameEnd < 0 || userId.indexOf('>', nameEnd) < 0) {
			nameEnd = userId.length();
		}
		nameEnd--;
		while (nameEnd >= 0 && userId.charAt(nameEnd) == ' ') {
			nameEnd--;
		}
		int nameBegin = 0;
		while (nameBegin < nameEnd && userId.charAt(nameBegin) == ' ') {
			nameBegin++;
		}
		return userId.substring(nameBegin, nameEnd + 1);
	}

	/**
	 * @return the email portion of the User ID, if one was successfully parsed
	 *         from {@link #getUserId()}, or null.
	 * @since 4.1
	 */
	public String getEmailAddress() {
		int emailBegin = userId.indexOf('<');
		if (emailBegin < 0) {
			return null;
		}
		int emailEnd = userId.indexOf('>', emailBegin);
		if (emailEnd < 0) {
			return null;
		}
		return userId.substring(emailBegin + 1, emailEnd);
	}

	/**
	 * @return the timestamp of the identity.
	 * @since 4.1
	 */
	public Date getWhen() {
		return new Date(when);
	}

	/**
	 * @return this person's declared time zone; null if the timezone is unknown.
	 * @since 4.1
	 */
	public TimeZone getTimeZone() {
		return PersonIdent.getTimeZone(tzOffset);
	}

	/**
	 * @return this person's declared time zone as minutes east of UTC. If the
	 *         timezone is to the west of UTC it is negative.
	 * @since 4.1
	 */
	public int getTimeZoneOffset() {
		return tzOffset;
	}

	@Override
	public boolean equals(Object o) {
		return (o instanceof PushCertificateIdent)
			&& raw.equals(((PushCertificateIdent) o).raw);
	}

	@Override
	public int hashCode() {
		return raw.hashCode();
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		SimpleDateFormat fmt;
		fmt = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
		fmt.setTimeZone(getTimeZone());
		return getClass().getSimpleName()
			+ "[raw=\"" + raw + "\","
			+ " userId=\"" + userId + "\","
			+ " " + fmt.format(Long.valueOf(when)) + "]";
	}
}
