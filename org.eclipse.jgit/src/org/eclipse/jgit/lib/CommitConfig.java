/*
 * Copyright (c) 2020 Julian Ruppel <julian.ruppel@sap.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.MessageFormat;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * The standard "commit" configuration parameters.
 *
 * @since 5.13
 */
public class CommitConfig {
	/**
	 * Key for {@link Config#get(SectionParser)}.
	 */
	public static final Config.SectionParser<CommitConfig> KEY = CommitConfig::new;

	private final static Charset DEFAULT_COMMIT_MESSAGE_ENCODING = StandardCharsets.UTF_8;

	private String i18nCommitEncoding;

	private String commitTemplatePath;

	private CommitConfig(Config rc) {
		commitTemplatePath = rc.getString(ConfigConstants.CONFIG_COMMIT_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMMIT_TEMPLATE);
		i18nCommitEncoding = rc.getString(ConfigConstants.CONFIG_SECTION_I18N,
				null, ConfigConstants.CONFIG_KEY_COMMIT_ENCODING);
	}

	/**
	 * Get the path to the commit template as defined in the git
	 * {@code commit.template} property.
	 *
	 * @return the path to commit template or {@code null} if not present.
	 */
	@Nullable
	public String getCommitTemplatePath() {
		return commitTemplatePath;
	}

	/**
	 * Get the encoding of the commit as defined in the git
	 * {@code i18n.commitEncoding} property.
	 *
	 * @return the encoding or {@code null} if not present.
	 */
	@Nullable
	public String getCommitEncoding() {
		return i18nCommitEncoding;
	}

	/**
	 * Get the content to the commit template as defined in
	 * {@code commit.template}. If no {@code i18n.commitEncoding} is specified,
	 * UTF-8 fallback is used.
	 *
	 * @return content of the commit template or {@code null} if not present.
	 * @throws IOException
	 *             if the template file can not be read
	 * @throws FileNotFoundException
	 *             if the template file does not exists
	 * @throws ConfigInvalidException
	 *             if a {@code commitEncoding} is specified and is invalid
	 */
	@Nullable
	public String getCommitTemplateContent()
			throws FileNotFoundException, IOException, ConfigInvalidException {

		if (commitTemplatePath == null) {
			return null;
		}

		File commitTemplateFile;
		if (commitTemplatePath.startsWith("~/")) { //$NON-NLS-1$
			commitTemplateFile = FS.DETECTED.resolve(FS.DETECTED.userHome(),
					commitTemplatePath.substring(2));
		} else {
			commitTemplateFile = FS.DETECTED.resolve(null, commitTemplatePath);
		}

		Charset commitMessageEncoding = getEncoding();
		return RawParseUtils.decode(commitMessageEncoding,
				IO.readFully(commitTemplateFile));

	}

	private Charset getEncoding() throws ConfigInvalidException {
		Charset commitMessageEncoding = DEFAULT_COMMIT_MESSAGE_ENCODING;

		if (i18nCommitEncoding == null) {
			return null;
		}

		try {
			commitMessageEncoding = Charset.forName(i18nCommitEncoding);
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			throw new ConfigInvalidException(MessageFormat.format(
					JGitText.get().invalidEncoding, i18nCommitEncoding), e);
		}

		return commitMessageEncoding;
	}
}