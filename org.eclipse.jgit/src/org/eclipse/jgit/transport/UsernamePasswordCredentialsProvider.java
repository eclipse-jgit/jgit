/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Arrays;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * Simple {@link org.eclipse.jgit.transport.CredentialsProvider} that always
 * uses the same information.
 */
public class UsernamePasswordCredentialsProvider extends CredentialsProvider {
	private String username;

	private char[] password;

	/**
	 * Initialize the provider with a single username and password.
	 *
	 * @param username
	 *            user name
	 * @param password
	 *            password
	 */
	public UsernamePasswordCredentialsProvider(String username, String password) {
		this(username, password.toCharArray());
	}

	/**
	 * Initialize the provider with a single username and password.
	 *
	 * @param username
	 *            user name
	 * @param password
	 *            password
	 */
	public UsernamePasswordCredentialsProvider(String username, char[] password) {
		this.username = username;
		this.password = password;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isInteractive() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				continue;

			else if (i instanceof CredentialItem.Password)
				continue;

			else
				return false;
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				((CredentialItem.Username) i).setValue(username);
				continue;
			}
			if (i instanceof CredentialItem.Password) {
				((CredentialItem.Password) i).setValue(password);
				continue;
			}
			if (i instanceof CredentialItem.StringType) {
				if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
					((CredentialItem.StringType) i).setValue(new String(
							password));
					continue;
				}
			}
			throw new UnsupportedCredentialItem(uri, i.getClass().getName()
					+ ":" + i.getPromptText()); //$NON-NLS-1$
		}
		return true;
	}

	/**
	 * Destroy the saved username and password..
	 */
	public void clear() {
		username = null;

		if (password != null) {
			Arrays.fill(password, (char) 0);
			password = null;
		}
	}
}
