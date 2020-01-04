/*******************************************************************************
 * Copyright (c) 2020 Julian Ruppel <julian.ruppel@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jgit.lib;

import java.util.Optional;

import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * The standard "commit" configuration parameters.
 *
 * @since 5.7
 */
public class CommitConfig {
	/**
	 * Key for {@link Config#get(SectionParser)}.
	 */
	public static final Config.SectionParser<CommitConfig> KEY = CommitConfig::new;

	private String commitTemplatePath;

	private CommitConfig(Config rc) {
		commitTemplatePath = rc.getString(ConfigConstants.CONFIG_COMMIT_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMMIT_TEMPLATE);
	}

	/**
	 * Get the commit template as defined in the git variables and
	 * configurations.
	 *
	 * @return the path to commit template.
	 */
	public Optional<String> getCommitTemplatePath() {
		return Optional.ofNullable(commitTemplatePath);
	}

}
