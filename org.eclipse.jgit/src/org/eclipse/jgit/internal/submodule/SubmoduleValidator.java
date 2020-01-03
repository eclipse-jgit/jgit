/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.submodule;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_SUBMODULE_SECTION;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.GITMODULES_NAME;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.GITMODULES_PARSE;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.GITMODULES_PATH;
import static org.eclipse.jgit.lib.ObjectChecker.ErrorType.GITMODULES_URL;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectChecker;

/**
 * Validations for the git submodule fields (name, path, uri).
 *
 * Invalid values in these fields can cause security problems as reported in
 * CVE-2018-11235 and CVE-2018-17456
 */
public class SubmoduleValidator {

	/**
	 * Error validating a git submodule declaration
	 */
	public static class SubmoduleValidationException extends Exception {

		private static final long serialVersionUID = 1L;

		private final ObjectChecker.ErrorType fsckMessageId;

		/**
		 * @param message
		 *            Description of the problem
		 * @param fsckMessageId
		 *            Error identifier, following the git fsck fsck.<msg-id>
		 *            format
		 */
		SubmoduleValidationException(String message,
				ObjectChecker.ErrorType fsckMessageId) {
			super(message);
			this.fsckMessageId = fsckMessageId;
		}


		/**
		 * @return the error identifier
		 */
		public ObjectChecker.ErrorType getFsckMessageId() {
			return fsckMessageId;
		}
	}

	/**
	 * Validate name for a submodule
	 *
	 * @param name
	 *            name of a submodule
	 * @throws SubmoduleValidationException
	 *             name doesn't seem valid (detail in message)
	 */
	public static void assertValidSubmoduleName(String name)
			throws SubmoduleValidationException {
		if (name.contains("/../") || name.contains("\\..\\") //$NON-NLS-1$ //$NON-NLS-2$
				|| name.startsWith("../") || name.startsWith("..\\") //$NON-NLS-1$ //$NON-NLS-2$
				|| name.endsWith("/..") || name.endsWith("\\..")) { //$NON-NLS-1$ //$NON-NLS-2$
			// Submodule names are used to store the submodule repositories
			// under $GIT_DIR/modules. Having ".." in submodule names makes a
			// vulnerability (CVE-2018-11235
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=535027#c0)
			// Reject names containing ".." path segments. We don't
			// automatically replace these characters or canonicalize by
			// regarding the name as a file path.
			// Since Path class is platform dependent, we manually check '/' and
			// '\\' patterns here.
			throw new SubmoduleValidationException(MessageFormat
					.format(JGitText.get().invalidNameContainsDotDot, name),
					GITMODULES_NAME);
		}

		if (name.startsWith("-")) { //$NON-NLS-1$
			throw new SubmoduleValidationException(
					MessageFormat.format(
							JGitText.get().submoduleNameInvalid, name),
					GITMODULES_NAME);
		}
	}

	/**
	 * Validate URI for a submodule
	 *
	 * @param uri
	 *            uri of a submodule
	 * @throws SubmoduleValidationException
	 *             uri doesn't seem valid
	 */
	public static void assertValidSubmoduleUri(String uri)
			throws SubmoduleValidationException {
		if (uri.startsWith("-")) { //$NON-NLS-1$
			throw new SubmoduleValidationException(
					MessageFormat.format(
							JGitText.get().submoduleUrlInvalid, uri),
					GITMODULES_URL);
		}
	}

	/**
	 * Validate path for a submodule
	 *
	 * @param path
	 *            path of a submodule
	 * @throws SubmoduleValidationException
	 *             path doesn't look right
	 */
	public static void assertValidSubmodulePath(String path)
			throws SubmoduleValidationException {
		if (path.startsWith("-")) { //$NON-NLS-1$
			throw new SubmoduleValidationException(
					MessageFormat.format(
							JGitText.get().submodulePathInvalid, path),
					GITMODULES_PATH);
		}
	}

	/**
	 * Validate a .gitmodules file
	 *
	 * @param gitModulesContents
	 *            Contents of a .gitmodule file. They will be parsed internally.
	 * @throws SubmoduleValidationException
	 *             if the contents don't look like a configuration file or field
	 *             values are not valid
	 */
	public static void assertValidGitModulesFile(String gitModulesContents)
			throws SubmoduleValidationException {
		Config c = new Config();
		try {
			c.fromText(gitModulesContents);
			for (String subsection :
					c.getSubsections(CONFIG_SUBMODULE_SECTION)) {
				assertValidSubmoduleName(subsection);

				String url = c.getString(
						CONFIG_SUBMODULE_SECTION, subsection, CONFIG_KEY_URL);
				if (url != null) {
					assertValidSubmoduleUri(url);
				}

				String path = c.getString(
						CONFIG_SUBMODULE_SECTION, subsection, CONFIG_KEY_PATH);
				if (path != null) {
					assertValidSubmodulePath(path);
				}
			}
		} catch (ConfigInvalidException e) {
			throw new SubmoduleValidationException(
					JGitText.get().invalidGitModules,
					GITMODULES_PARSE);
		}
	}
}
