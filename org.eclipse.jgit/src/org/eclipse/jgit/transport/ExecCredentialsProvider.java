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

import java.io.IOException;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.util.SystemReader;

/**
 * A {@link CredentialsProvider} that obtains credentials by running one
 * configured git credential helper (git {@code credential.c}). The helper value
 * follows git's rules: a {@code !snippet}, an absolute path, or a {@code <name>}
 * fragment resolved to {@code git credential-<name>}, run through the shell via
 * {@link org.eclipse.jgit.util.FS}.
 * <p>
 * The helper answers {@code get} with a {@code username}/{@code password} (git's
 * traditional output) or, when {@link #setUseBearer(boolean) opted in}, an
 * {@code authtype=Bearer} {@code credential}.
 * <p>
 * After a successful or rejected authentication the transport calls
 * {@link #store(URIish, CredentialItem...)} / {@link #erase(URIish)}, which run
 * the helper's {@code store} / {@code erase} actions.
 *
 * @since 7.9
 */
public class ExecCredentialsProvider extends CredentialsProvider {

	/** git's {@code authtype} value for a bearer token (RFC 6750). */
	private static final String AUTHTYPE_BEARER = "Bearer"; //$NON-NLS-1$

	private final CredentialHelper helper;

	private boolean useBearer;

	/**
	 * Create a provider for a single helper.
	 *
	 * @param helper
	 *            the {@code credential.helper} value (without the action): a
	 *            {@code !snippet}, an absolute path, or a {@code <name> [args…]}
	 *            fragment
	 */
	public ExecCredentialsProvider(String helper) {
		this.helper = new CredentialHelper(helper);
	}

	/**
	 * Whether to send the {@code path} attribute to the helper (git's
	 * {@code credential.useHttpPath}).
	 *
	 * @param use
	 *            {@code true} to include the path
	 * @return {@code this}
	 */
	public ExecCredentialsProvider setUseHttpPath(boolean use) {
		helper.setUseHttpPath(use);
		return this;
	}

	/**
	 * Opt in to Bearer credentials: advertise git's {@code authtype} capability
	 * and answer {@link CredentialItem.Bearer} when the helper returns
	 * {@code authtype=Bearer} with a {@code credential}. Off by default.
	 *
	 * @param use
	 *            {@code true} to accept helper-supplied Bearer tokens
	 * @return {@code this}
	 */
	public ExecCredentialsProvider setUseBearer(boolean use) {
		this.useBearer = use;
		helper.setAdvertiseAuthtype(use);
		return this;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem item : items) {
			if (!isSupported(item)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialItem item : items) {
			if (!isSupported(item)) {
				throw new UnsupportedCredentialItem(uri, item.getClass().getName()
						+ ':' + item.getPromptText());
			}
		}
		if (uri.getHost() == null) {
			return false;
		}
		CredentialHelper.Answer answer;
		try {
			answer = helper.get(uri.getScheme(), hostWithPort(uri),
					uri.getPath(), uri.getUser());
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		try {
			if (answer.isExpired(
					SystemReader.getInstance().now().getEpochSecond())) {
				// git discards an expired helper answer and moves on.
				return false;
			}
			boolean satisfied = true;
			for (CredentialItem item : items) {
				if (item instanceof CredentialItem.Username username) {
					if (answer.getUsername() != null) {
						username.setValue(answer.getUsername());
					} else {
						satisfied = false;
					}
				} else if (item instanceof CredentialItem.Password password) {
					if (answer.getPassword() != null) {
						password.setValue(answer.getPassword());
					} else {
						satisfied = false;
					}
				} else if (item instanceof CredentialItem.Bearer bearer) {
					if (answer.getCredential() != null && AUTHTYPE_BEARER
							.equalsIgnoreCase(answer.getAuthType())) {
						bearer.setValue(answer.getCredential());
					} else {
						satisfied = false;
					}
				}
			}
			return satisfied;
		} finally {
			answer.clear();
		}
	}

	@Override
	public void reset(URIish uri) {
		// Stateless: nothing is cached between calls.
	}

	@Override
	public void store(URIish uri, CredentialItem... items) {
		if (uri.getHost() == null) {
			return;
		}
		String username = null;
		char[] password = null;
		String authtype = null;
		char[] credential = null;
		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.Username u) {
				username = u.getValue();
			} else if (item instanceof CredentialItem.Password p) {
				password = p.getValue();
			} else if (item instanceof CredentialItem.Bearer b) {
				credential = b.getValue();
				authtype = AUTHTYPE_BEARER;
			}
		}
		try {
			helper.store(uri.getScheme(), hostWithPort(uri), uri.getPath(),
					username, password, authtype, credential);
		} catch (IOException e) {
			// store is best-effort; a failing helper must not break the fetch.
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void erase(URIish uri) {
		if (uri.getHost() == null) {
			return;
		}
		try {
			helper.erase(uri.getScheme(), hostWithPort(uri), uri.getPath(),
					uri.getUser());
		} catch (IOException e) {
			// erase is best-effort; a failing helper must not break the fetch.
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean isSupported(CredentialItem item) {
		if (item instanceof CredentialItem.Username
				|| item instanceof CredentialItem.Password
				|| item instanceof CredentialItem.InformationalMessage) {
			return true;
		}
		return useBearer && item instanceof CredentialItem.Bearer;
	}

	private static String hostWithPort(URIish uri) {
		String host = uri.getHost();
		int port = uri.getPort();
		if (port <= 0 || port == defaultPort(uri.getScheme())) {
			return host; // git omits the scheme's default port
		}
		return host + ':' + port;
	}

	private static int defaultPort(String scheme) {
		if ("https".equalsIgnoreCase(scheme)) { //$NON-NLS-1$
			return 443;
		}
		if ("http".equalsIgnoreCase(scheme)) { //$NON-NLS-1$
			return 80;
		}
		return -1;
	}
}
