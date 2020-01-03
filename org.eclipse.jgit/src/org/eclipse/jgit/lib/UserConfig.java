/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.util.SystemReader;

/**
 * The standard "user" configuration parameters.
 */
public class UserConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<UserConfig> KEY = UserConfig::new;

	private String authorName;

	private String authorEmail;

	private String committerName;

	private String committerEmail;

	private boolean isAuthorNameImplicit;

	private boolean isAuthorEmailImplicit;

	private boolean isCommitterNameImplicit;

	private boolean isCommitterEmailImplicit;

	private UserConfig(Config rc) {
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
	 * Get the author name as defined in the git variables and configurations.
	 *
	 * @return the author name as defined in the git variables and
	 *         configurations. If no name could be found, try to use the system
	 *         user name instead.
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * Get the committer name as defined in the git variables and
	 * configurations.
	 *
	 * @return the committer name as defined in the git variables and
	 *         configurations. If no name could be found, try to use the system
	 *         user name instead.
	 */
	public String getCommitterName() {
		return committerName;
	}

	/**
	 * Get the author email as defined in git variables and configurations.
	 *
	 * @return the author email as defined in git variables and configurations.
	 *         If no email could be found, try to propose one default with the
	 *         user name and the host name.
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * Get the committer email as defined in git variables and configurations.
	 *
	 * @return the committer email as defined in git variables and
	 *         configurations. If no email could be found, try to propose one
	 *         default with the user name and the host name.
	 */
	public String getCommitterEmail() {
		return committerEmail;
	}

	/**
	 * Whether the author name was not explicitly configured but constructed
	 * from information the system has about the logged on user
	 *
	 * @return true if the author name was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isAuthorNameImplicit() {
		return isAuthorNameImplicit;
	}

	/**
	 * Whether the author email was not explicitly configured but constructed
	 * from information the system has about the logged on user
	 *
	 * @return true if the author email was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isAuthorEmailImplicit() {
		return isAuthorEmailImplicit;
	}

	/**
	 * Whether the committer name was not explicitly configured but constructed
	 * from information the system has about the logged on user
	 *
	 * @return true if the committer name was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isCommitterNameImplicit() {
		return isCommitterNameImplicit;
	}

	/**
	 * Whether the author email was not explicitly configured but constructed
	 * from information the system has about the logged on user
	 *
	 * @return true if the author email was not explicitly configured but
	 *         constructed from information the system has about the logged on
	 *         user
	 */
	public boolean isCommitterEmailImplicit() {
		return isCommitterEmailImplicit;
	}

	private static String getNameInternal(Config rc, String envKey) {
		// try to get the user name for the system property GIT_XXX_NAME
		String username = system().getenv(envKey);

		if (username == null) {
			// try to get the user name from the local and global
			// configurations.
			username = rc.getString("user", null, "name"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return stripInvalidCharacters(username);
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
		// try to get the email for the system property GIT_XXX_EMAIL
		String email = system().getenv(envKey);

		if (email == null) {
			// try to get the email from the local and global configurations.
			email = rc.getString("user", null, "email"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return stripInvalidCharacters(email);
	}

	private static String stripInvalidCharacters(String s) {
		return s == null ? null : s.replaceAll("<|>|\n", ""); //$NON-NLS-1$//$NON-NLS-2$
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
