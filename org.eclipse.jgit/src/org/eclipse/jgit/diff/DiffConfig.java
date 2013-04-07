/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.StringUtils;

/** Keeps track of diff related configuration options. */
public class DiffConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<DiffConfig> KEY = new SectionParser<DiffConfig>() {
		public DiffConfig parse(final Config cfg) {
			return new DiffConfig(cfg);
		}
	};

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

	private DiffConfig(final Config rc) {
		noPrefix = rc.getBoolean(ConfigConstants.CONFIG_DIFF_SECTION,
				ConfigConstants.CONFIG_KEY_NOPREFIX, false);
		renameDetectionType = parseRenameDetectionType(rc.getString(
				ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_RENAMES));
		renameLimit = rc.getInt(ConfigConstants.CONFIG_DIFF_SECTION,
				ConfigConstants.CONFIG_KEY_RENAMELIMIT, 200);
	}

	/** @return true if the prefix "a/" and "b/" should be suppressed. */
	public boolean isNoPrefix() {
		return noPrefix;
	}

	/** @return true if rename detection is enabled by default. */
	public boolean isRenameDetectionEnabled() {
		return renameDetectionType != RenameDetectionType.FALSE;
	}

	/** @return type of rename detection to perform. */
	public RenameDetectionType getRenameDetectionType() {
		return renameDetectionType;
	}

	/** @return limit on number of paths to perform inexact rename detection. */
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
