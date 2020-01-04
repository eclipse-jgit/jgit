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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

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

	private Charset commitMessageEncoding = StandardCharsets.UTF_8;

	private CommitConfig(Config rc) {
		commitTemplatePath = rc.getString(ConfigConstants.CONFIG_COMMIT_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMMIT_TEMPLATE);

		String i18nCommitEncoding = rc.getString(
				ConfigConstants.CONFIG_SECTION_I18N, null,
				ConfigConstants.CONFIG_KEY_COMMIT_ENCODING);

		if (i18nCommitEncoding != null) {
			commitMessageEncoding = Charset.forName(i18nCommitEncoding);
		}
	}

	/**
	 * Get the path to the commit template as defined in the git variables and
	 * configurations.
	 *
	 * @return the path to commit template or {@code null} if not present.
	 */
	@Nullable
	public String getCommitTemplatePath() {
		return commitTemplatePath;
	}

	/**
	 * Get the content to the commit template as defined in the git variables
	 * and configurations.
	 *
	 * @return content of the commit template or {@code null} if not present.
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	@Nullable
	public String getCommitTemplateContent()
			throws FileNotFoundException, IOException {
		if (commitTemplatePath != null) {
			File commitTemplateFile = new File(
					resolveHomePath(commitTemplatePath));

			return RawParseUtils.decode(commitMessageEncoding,
					IO.readFully(commitTemplateFile));
		}
		return null;
	}

	private String resolveHomePath(String path) {
		return path.replaceFirst(
				"^~", //$NON-NLS-1$
				Matcher.quoteReplacement(System.getProperty("user.home"))); //$NON-NLS-1$
	}

}
