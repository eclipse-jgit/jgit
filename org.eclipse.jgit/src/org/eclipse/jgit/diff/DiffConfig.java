/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.StringUtils;

/**
 * Keeps track of diff related configuration options.
 */
public class DiffConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<DiffConfig> KEY = DiffConfig::new;

	/** Permissible values for {@code diff.renames}. */
	public static enum RenameDetectionType {
		/** Rename detection is disabled. */
		FALSE,

		/** Rename detection is enabled. */
		TRUE,

		/** Copies should be detected too. */
		COPY
	}

	private final boolean noPrefix;

	private final RenameDetectionType renameDetectionType;

	private final int renameLimit;

	private DiffConfig(Config rc) {
		noPrefix = rc.getBoolean(ConfigConstants.CONFIG_DIFF_SECTION,
				ConfigConstants.CONFIG_KEY_NOPREFIX, false);
		renameDetectionType = parseRenameDetectionType(rc.getString(
				ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_RENAMES));
		renameLimit = rc.getInt(ConfigConstants.CONFIG_DIFF_SECTION,
				ConfigConstants.CONFIG_KEY_RENAMELIMIT, 400);
	}

	/**
	 * If prefix should be suppressed
	 *
	 * @return true if the prefix "a/" and "b/" should be suppressed
	 */
	public boolean isNoPrefix() {
		return noPrefix;
	}

	/**
	 * If rename detection is enabled
	 *
	 * @return true if rename detection is enabled by default
	 */
	public boolean isRenameDetectionEnabled() {
		return renameDetectionType != RenameDetectionType.FALSE;
	}

	/**
	 * Get the rename detection type
	 *
	 * @return type of rename detection to perform
	 */
	public RenameDetectionType getRenameDetectionType() {
		return renameDetectionType;
	}

	/**
	 * Get the rename limit
	 *
	 * @return limit on number of paths to perform inexact rename detection
	 */
	public int getRenameLimit() {
		return renameLimit;
	}

	private static RenameDetectionType parseRenameDetectionType(
			final String renameString) {
		if (renameString == null)
			return RenameDetectionType.FALSE;
		else if (StringUtils.equalsIgnoreCase(
				ConfigConstants.CONFIG_RENAMELIMIT_COPY, renameString)
				|| StringUtils
						.equalsIgnoreCase(
								ConfigConstants.CONFIG_RENAMELIMIT_COPIES,
								renameString))
			return RenameDetectionType.COPY;
		else {
			final Boolean renameBoolean = StringUtils
					.toBooleanOrNull(renameString);
			if (renameBoolean == null)
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().enumValueNotSupported2,
						ConfigConstants.CONFIG_DIFF_SECTION,
						ConfigConstants.CONFIG_KEY_RENAMES, renameString));
			else if (renameBoolean.booleanValue())
				return RenameDetectionType.TRUE;
			else
				return RenameDetectionType.FALSE;
		}
	}
}
