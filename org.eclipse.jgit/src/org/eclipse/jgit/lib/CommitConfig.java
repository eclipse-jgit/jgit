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

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The standard "commit" configuration parameters.
 *
 * @since 5.7
 */
public class CommitConfig {

	private static final Logger LOG = LoggerFactory
			.getLogger(CommitConfig.class);

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
	 * Get the path to the commit template as defined in the git variables and
	 * configurations.
	 *
	 * @return the path to commit template.
	 */
	public Optional<String> getCommitTemplatePath() {
		return Optional.ofNullable(commitTemplatePath);
	}

	/**
	 * Get the content to the commit template as defined in the git variables
	 * and configurations.
	 *
	 * @return content of the commit template
	 */
	public Optional<String> getCommitTemplateContent() {
		if (commitTemplatePath != null) {
			File commitTemplateFile = new File(commitTemplatePath);

			try {
				return Optional.ofNullable(
						RawParseUtils.decode(IO.readFully(commitTemplateFile)));
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}
		return Optional.empty();
	}

}
