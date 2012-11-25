/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lib;

import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.SystemReader;

/** The standard "user" configuration parameters. */
public class UserConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<UserConfig> KEY = new SectionParser<UserConfig>() {
		public UserConfig parse(final Config cfg) {
			return new UserConfig(cfg);
		}
	};

	private String authorName;

	private String authorEmail;

	private String committerName;

	private String committerEmail;

	private boolean isAuthorNameImplicit;

	private boolean isAuthorEmailImplicit;

	private boolean isCommitterNameImplicit;

	private boolean isCommitterEmailImplicit;

	private UserConfig(final Config rc) {
		authorName = getNameInternal(rc, Constants.GIT_AUTHOR_NAME_KEY);
		if (authorName == null) {
			authorName = getDefaultUserName();
			isAuthorNameImplicit = true;
		}
		authorEmail = getEmailInternal(rc, Constants.GIT_AUTHOR_EMAIL_KEY);
		if (authorEmail == null) {
			authorEmail = getDefaultEmail();
			isAuthorEmailImplicit = true;
		}

		committerName = getNameInternal(rc, Constants.GIT_COMMITTER_NAME_KEY);
		if (committerName == null) {
			committerName = getDefaultUserName();
			isCommitterNameImplicit = true;
		}
		committerEmail = getEmailInternal(rc, Constants.GIT_COMMITTER_EMAIL_KEY);
		if (committerEmail == null) {
			committerEmail = getDefaultEmail();
			isCommitterEmailImplicit = true;
		}
	}

	/**
	 * @return the author name as defined in the git variables and
	 *         configurations. If no name could be found, try to use the system
	 *         user name instead.
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * @return the committer name as defined in the git variables and
	 *         configurations. If no name could be found, try to use the system
	 *         user name instead.
	 */
	public String getCommitterName() {
		return committerName;
	}

	/**
	 * @return the author email as defined in git variables and
	 *         configurations. If no email could be found, try to
	 *         propose one default with the user name and the
	 *         host name.
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * @return the committer email as defined in git variables and
	 *         configurations. If no email could be found, try to
	 *         propose one default with the user name and the
	 *         host name.
	 */
	public String getCommitterEmail() {
		return committerEmail;
	}

	/**
	 * @return true if the author name was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isAuthorNameImplicit() {
		return isAuthorNameImplicit;
	}

	/**
	 * @return true if the author email was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isAuthorEmailImplicit() {
		return isAuthorEmailImplicit;
	}

	/**
	 * @return true if the committer name was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isCommitterNameImplicit() {
		return isCommitterNameImplicit;
	}

	/**
	 * @return true if the author email was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isCommitterEmailImplicit() {
		return isCommitterEmailImplicit;
	}

	private static String getNameInternal(Config rc, String envKey) {
		// try to get the user name from the local and global configurations.
		String username = rc.getString("user", null, "name"); //$NON-NLS-1$ //$NON-NLS-2$

		if (username == null) {
			// try to get the user name for the system property GIT_XXX_NAME
			username = system().getenv(envKey);
		}

		return username;
	}

	/**
	 * @return try to get user name of the logged on user from the operating
	 *         system
	 */
	private static String getDefaultUserName() {
		// get the system user name
		String username = system().getProperty(Constants.OS_USER_NAME_KEY);
		if (username == null)
			username = Constants.UNKNOWN_USER_DEFAULT;
		return username;
	}

	private static String getEmailInternal(Config rc, String envKey) {
		// try to get the email from the local and global configurations.
		String email = rc.getString("user", null, "email"); //$NON-NLS-1$ //$NON-NLS-2$

		if (email == null) {
			// try to get the email for the system property GIT_XXX_EMAIL
			email = system().getenv(envKey);
		}

		return email;
	}

	/**
	 * @return try to construct email for logged on user using system
	 *         information
	 */
	private static String getDefaultEmail() {
		// try to construct an email
		String username = getDefaultUserName();
		return username + "@" + system().getHostname(); //$NON-NLS-1$
	}

	private static SystemReader system() {
		return SystemReader.getInstance();
	}
}
