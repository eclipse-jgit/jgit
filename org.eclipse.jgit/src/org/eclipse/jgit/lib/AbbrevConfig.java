/*
 * Copyright (C) 2022, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_STRING_LENGTH;

/**
 * Git configuration option
 * <a href=https://git-scm.com/docs/git-config#Documentation/git-config.txt-coreabbrev">
 * core.abbrev</a>
 *
 * @since 6.1
 */
public class AbbrevConfig {
	private static final String VALUE_NO = "no"; //$NON-NLS-1$

	private static final String VALUE_AUTO = "auto"; //$NON-NLS-1$

	/**
	 * An appropriate value is computed based on the approximate number of
	 * packed objects in your repository, which hopefully is enough for
	 * abbreviated object names to stay unique for some time.
	 */
	public final static AbbrevConfig AUTO = new AbbrevConfig(-1);

	/**
	 * Cap configured core.abbrev to range between minimum of 4 and number of
	 * hex-digits of a full object id.
	 *
	 * @param len
	 *            configured number of hex-digits to abbreviate object ids to
	 * @return core.abbrev capped to range between minimum of 4 and number of
	 *         hex-digits of a full object id
	 */
	public static int capAbbrev(int len) {
		return Math.min(Math.max(4, len), Constants.OBJECT_ID_STRING_LENGTH);
	}

	/**
	 * No abbreviation
	 */
	public final static AbbrevConfig NO = new AbbrevConfig(
			Constants.OBJECT_ID_STRING_LENGTH);

	/**
	 * Parse string value of core.abbrev git option
	 *
	 * @param config
	 *            git config to parse core.abbrev git option from
	 * @return the parsed AbbrevConfig
	 */
	public static AbbrevConfig parseFromConfig(Config config) {
		String value = config.getString(ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_ABBREV, VALUE_AUTO);
		if (value == null || value.equalsIgnoreCase(VALUE_AUTO)) {
			return AUTO;
		}
		if (value.equalsIgnoreCase(VALUE_NO)) {
			return NO;
		}
		int len = Integer.parseInt(value);
		return new AbbrevConfig(len);
	}

	/**
	 * All other possible abbreviation lengths. Valid range 4 to number of
	 * hex-digits of an unabbreviated object id (40 for SHA1 object ids, jgit
	 * doesn't support SHA256 yet).
	 */
	private int abbrev;

	/**
	 * @param abbrev
	 */
	public AbbrevConfig(int abbrev) {
		if (abbrev == -1) {
			this.abbrev = abbrev;
		} else {
			this.abbrev = capAbbrev(abbrev);
		}
	}

	/**
	 * Get the configured abbreviation length for object ids.
	 *
	 * @param repo
	 *            repository for which to get the objectid abbreviation
	 *            length
	 *
	 * @return the configured abbreviation length for object ids
	 */
	public int get(Repository repo) {
		if (abbrev < 0) {
			// "auto": an appropriate value is computed based on the
			// approximate number of packed objects in the repository,
			// which hopefully ensures that abbreviated object names
			// stay unique for some time
			long count = repo.getObjectDatabase()
					.getApproximateObjectCount();
			// find msb, round to next power of 2
			int len = 63 - Long.numberOfLeadingZeros(count) + 1;
			// With the order of 2^len objects, we expect a collision at
			// 2^(len/2). But we also care about hex chars, not bits, and
			// there are 4 bits per hex. So all together we need to divide
			// by 2; but we also want to round odd numbers up, hence adding
			// one before dividing.
			len = (len + 1) / 2;
			// for small repos use at least fallback length
			return Math.max(len, OBJECT_ID_ABBREV_STRING_LENGTH);
		}
		return abbrev;
	}

	@Override
	public String toString() {
		if (abbrev == Short.MIN_VALUE) {
			return VALUE_AUTO;
		}
		if (abbrev == Constants.OBJECT_ID_STRING_LENGTH) {
			return VALUE_NO;
		}
		return Integer.toString(abbrev);
	}
}