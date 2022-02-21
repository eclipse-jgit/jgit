/*
 * Copyright (C) 2022,  Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_STRING_LENGTH;
import static org.eclipse.jgit.lib.TypedConfigGetter.UNSET_INT;

import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.internal.JGitText;

/**
 * Git configuration option <a
 * href=https://git-scm.com/docs/git-config#Documentation/git-config.txt-coreabbrev">
 * core.abbrev</a>
 *
 * @since 6.1
 */
public final class AbbrevConfig {
	private static final String VALUE_NO = "no"; //$NON-NLS-1$

	private static final String VALUE_AUTO = "auto"; //$NON-NLS-1$

	/**
	 * The minimum value of abbrev
	 */
	public static final int MIN_ABBREV = 4;

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
		return Math.min(Math.max(MIN_ABBREV, len),
				Constants.OBJECT_ID_STRING_LENGTH);
	}

	/**
	 * No abbreviation
	 */
	public final static AbbrevConfig NO = new AbbrevConfig(
			Constants.OBJECT_ID_STRING_LENGTH);

	/**
	 * Parse string value of core.abbrev git option for a given repository
	 *
	 * @param repo
	 *            repository
	 * @return the parsed AbbrevConfig
	 * @throws InvalidConfigurationException
	 *             if value of core.abbrev is invalid
	 */
	public static AbbrevConfig parseFromConfig(Repository repo)
			throws InvalidConfigurationException {
		Config config = repo.getConfig();
		String value = config.getString(ConfigConstants.CONFIG_CORE_SECTION,
				null, ConfigConstants.CONFIG_KEY_ABBREV);
		if (value == null || value.equalsIgnoreCase(VALUE_AUTO)) {
			return auto(repo);
		}
		if (value.equalsIgnoreCase(VALUE_NO)) {
			return NO;
		}
		try {
			int len = config.getIntInRange(ConfigConstants.CONFIG_CORE_SECTION,
					ConfigConstants.CONFIG_KEY_ABBREV, MIN_ABBREV,
					Constants.OBJECT_ID_STRING_LENGTH, UNSET_INT);
			return new AbbrevConfig(len);
		} catch (IllegalArgumentException e) {
			throw new InvalidConfigurationException(MessageFormat
					.format(JGitText.get().invalidCoreAbbrev, value), e);
		}
	}

	/**
	 * An appropriate value is computed based on the approximate number of
	 * packed objects in a repository, which hopefully is enough for abbreviated
	 * object names to stay unique for some time.
	 *
	 * @param repo
	 * @return appropriate value computed based on the approximate number of
	 *         packed objects in a repository
	 */
	private static AbbrevConfig auto(Repository repo) {
		// "auto": an appropriate value is computed based on the
		// approximate number of packed objects in the repository,
		// which hopefully ensures that abbreviated object names
		// stay unique for some time
		long count = repo.getObjectDatabase().getApproximateObjectCount();
		if (count == -1) {
			return new AbbrevConfig(OBJECT_ID_ABBREV_STRING_LENGTH);
		}
		// find msb, round to next power of 2
		int len = 63 - Long.numberOfLeadingZeros(count) + 1;
		// With the order of 2^len objects, we expect a collision at
		// 2^(len/2). But we also care about hex chars, not bits, and
		// there are 4 bits per hex. So all together we need to divide
		// by 2; but we also want to round odd numbers up, hence adding
		// one before dividing.
		len = (len + 1) / 2;
		// for small repos use at least fallback length
		return new AbbrevConfig(Math.max(len, OBJECT_ID_ABBREV_STRING_LENGTH));
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
	private AbbrevConfig(int abbrev) {
		this.abbrev = capAbbrev(abbrev);
	}

	/**
	 * Get the configured abbreviation length for object ids.
	 *
	 * @return the configured abbreviation length for object ids
	 */
	public int get() {
		return abbrev;
	}

	@Override
	public String toString() {
		return Integer.toString(abbrev);
	}
}