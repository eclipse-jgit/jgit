/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;

/**
 * A {@link CredentialsProvider} that authenticates with an OAuth 2.0 bearer token
 * (RFC 6750), sent as {@code Authorization: Bearer <token>}.
 * <p>
 * A bearer token carries no user name, so this provider answers only
 * {@link CredentialItem.Bearer} items; the HTTP transport applies the token
 * preemptively.
 *
 * @since 7.9
 */
public class BearerCredentialsProvider extends CredentialsProvider {

	private char[] token;

	/**
	 * Create a provider for the given bearer token.
	 *
	 * @param token
	 *            the bearer token to send; must not be {@code null} or empty
	 */
	public BearerCredentialsProvider(String token) {
		this(Objects.requireNonNull(token, "token must not be null") //$NON-NLS-1$
				.toCharArray());
	}

	/**
	 * Create a provider for the given bearer token.
	 *
	 * @param token
	 *            the bearer token to send; the array is copied. Must not be
	 *            {@code null} or empty
	 */
	public BearerCredentialsProvider(char[] token) {
		Objects.requireNonNull(token, "token must not be null"); //$NON-NLS-1$
		if (token.length == 0) {
			throw new IllegalArgumentException("token must not be empty"); //$NON-NLS-1$
		}
		for (char c : token) {
			if (c == '\r' || c == '\n') {
				throw new IllegalArgumentException(
						"token must not contain CR or LF"); //$NON-NLS-1$
			}
		}
		this.token = token.clone();
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.Bearer
					|| item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage) {
				continue;
			}
			if (item instanceof CredentialItem.Bearer) {
				if (token == null) {
					return false;
				}
				((CredentialItem.Bearer) item).setValue(token);
				continue;
			}
			throw new UnsupportedCredentialItem(uri,
					item.getClass().getName() + ":" + item.getPromptText()); //$NON-NLS-1$
		}
		return true;
	}

	/** Destroys the stored token, zeroing the internal array. */
	public void clear() {
		if (token != null) {
			Arrays.fill(token, (char) 0);
			token = null;
		}
	}
}
