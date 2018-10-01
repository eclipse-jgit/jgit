/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.internal.submodule;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

/**
 * Validations for the git submodule fields (name, path, uri).
 *
 * Invalid values in these fields can cause security problems as reported in
 * CVE-2018-11235 and and CVE-2018-17456
 */
public class SubmoduleValidator {

	/**
	 * Error validating a git submodule declaration
	 */
	public static class SubmoduleValidationException extends Exception {

		/**
		 * @param message
		 *            Description of the problem
		 */
		public SubmoduleValidationException(String message) {
			super(message);
		}

		private static final long serialVersionUID = 1L;
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
					.format(JGitText.get().invalidNameContainsDotDot, name));
		}

		if (name.startsWith("-")) { //$NON-NLS-1$
			throw new SubmoduleValidationException(
					MessageFormat.format(
							JGitText.get().submoduleNameInvalid, name));
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
							JGitText.get().submoduleUrlInvalid, uri));
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
							JGitText.get().submodulePathInvalid, path));
		}
	}

	/**
	 * @param gitModulesContents
	 *            Contents of a .gitmodule file. They will be parsed internally.
	 * @throws IOException
	 *             If the contents
	 */
	public static void assertValidGitModulesFile(String gitModulesContents)
			throws IOException {
		// Validate .gitmodules file
		Config c = new Config();
		try {
			c.fromText(gitModulesContents);
			for (String subsection : c.getSubsections(
					ConfigConstants.CONFIG_SUBMODULE_SECTION)) {
				String url = c.getString(
						ConfigConstants.CONFIG_SUBMODULE_SECTION,
						subsection, ConfigConstants.CONFIG_KEY_URL);
				assertValidSubmoduleUri(url);

				assertValidSubmoduleName(subsection);

				String path = c.getString(
						ConfigConstants.CONFIG_SUBMODULE_SECTION, subsection,
						ConfigConstants.CONFIG_KEY_PATH);
				assertValidSubmodulePath(path);
			}
		} catch (ConfigInvalidException e) {
			throw new IOException(
					MessageFormat.format(
							JGitText.get().invalidGitModules,
							e));
		} catch (SubmoduleValidationException e) {
			throw new IOException(e.getMessage(), e);
		}
	}
}
