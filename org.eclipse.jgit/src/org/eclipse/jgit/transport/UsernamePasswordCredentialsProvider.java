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

package org.eclipse.jgit.transport;

import java.util.Arrays;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * Simple {@link CredentialsProvider} that always uses the same information.
 */
public class UsernamePasswordCredentialsProvider extends CredentialsProvider {
	private String username;

	private char[] password;

	private char[] certPassword;

	/**
	 * Initialize the provider with a single username and password.
	 *
	 * @param username
	 * @param password
	 */
	public UsernamePasswordCredentialsProvider(String username, String password) {
		this(username, password.toCharArray());
	}

	/**
	 * Initialize the provider with a single username and password.
	 *
	 * @param username
	 * @param password
	 */
	public UsernamePasswordCredentialsProvider(String username, char[] password) {
		this.username = username;
		this.password = password;
	}

	/**
	 * Initialize the provider with a username, password and passwords for the
	 * personal certificate/key.
	 *
	 * @param username
	 * @param password
	 * @param certPassword
	 */
	public UsernamePasswordCredentialsProvider(String username,
			String password, String certPassword) {
		this(username, (password == null) ? null : password.toCharArray(),
				(certPassword == null) ? null : certPassword.toCharArray());
	}

	/**
	 * Initialize the provider with a single username and password.
	 *
	 * @param username
	 * @param password
	 * @param certPassword
	 */
	public UsernamePasswordCredentialsProvider(String username,
			char[] password, char[] certPassword) {
		this.username = username;
		this.password = password;
		this.certPassword = certPassword;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				continue;

			else if (i instanceof CredentialItem.Password)
				continue;

			else if (i instanceof CredentialItem.CertPassword)
				continue;

			else
				return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				((CredentialItem.Username) i).setValue(username);

			else if (i instanceof CredentialItem.Password)
				((CredentialItem.Password) i).setValue(password);

			else if (i instanceof CredentialItem.CertPassword)
				((CredentialItem.CertPassword) i).setValue(certPassword);

			else
				throw new UnsupportedCredentialItem(uri, i.getPromptText());
		}
		return true;
	}

	/** Destroy the saved username and password.. */
	public void clear() {
		username = null;

		if (password != null) {
			Arrays.fill(password, (char) 0);
			password = null;
		}
		if (certPassword != null) {
			Arrays.fill(certPassword, (char) 0);
			certPassword = null;
		}
	}
}
