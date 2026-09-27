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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * A single configured credential helper that runs {@code get}/{@code store}/
 * {@code erase} actions, porting native git's {@code credential_do} (git
 * {@code credential.c:484-502}).
 * <p>
 * git's three rules for a {@code credential.helper} value:
 * <ol>
 * <li>{@code !snippet} &rarr; a shell snippet;</li>
 * <li>an absolute path &rarr; run verbatim;</li>
 * <li>otherwise &rarr; {@code git credential-<name>}.</li>
 * </ol>
 * The action is passed as the helper's argument and, like git
 * ({@code use_shell = 1}), runs through the shell via
 * {@link FS#runInShell(String, String[])}; the process runs via
 * {@link FS#execute(ProcessBuilder, InputStream)}, which handles
 * stdin/stdout/stderr without deadlock.
 * <p>
 * Handles username/password and, when the {@code authtype} capability is
 * advertised, an {@code authtype}/{@code credential} pair. Multistage
 * (NTLM/Negotiate) auth is not handled.
 * <p>
 * Bundle-internal, not public API.
 */
final class CredentialHelper {

	private final String helper;

	private boolean useHttpPath;

	private boolean advertiseAuthtype;

	private boolean protectProtocol = true;

	/**
	 * @param helper
	 *            the {@code credential.helper} value: a {@code !snippet}, an
	 *            absolute path, or a {@code <name> [args…]} fragment (without the
	 *            action)
	 */
	CredentialHelper(String helper) {
		this.helper = helper;
	}

	/**
	 * @param use
	 *            whether to send the {@code path} attribute (git's
	 *            {@code credential.useHttpPath})
	 * @return {@code this}
	 */
	CredentialHelper setUseHttpPath(boolean use) {
		this.useHttpPath = use;
		return this;
	}

	/**
	 * Advertise git's {@code authtype} capability to the helper, so it may return
	 * an {@code authtype}/{@code credential} pair (e.g. Bearer) instead of a
	 * username/password.
	 *
	 * @param advertise
	 *            whether to send {@code capability[]=authtype}
	 * @return {@code this}
	 */
	CredentialHelper setAdvertiseAuthtype(boolean advertise) {
		this.advertiseAuthtype = advertise;
		return this;
	}

	/**
	 * Run the helper's {@code get} action.
	 *
	 * @param protocol
	 *            the URL scheme (e.g. {@code https})
	 * @param host
	 *            the host, including port when non-default
	 * @param path
	 *            the repository path, or {@code null}; only sent when
	 *            {@code useHttpPath} is set
	 * @param username
	 *            a known username to constrain the lookup, or {@code null}
	 * @return the helper's answer
	 * @throws IOException
	 *             if the helper cannot be run or emits a malformed response
	 * @throws InterruptedException
	 *             if interrupted while waiting for the helper
	 */
	Answer get(String protocol, String host, String path,
			String username) throws IOException, InterruptedException {
		Credential resp = new Credential();
		run("get", request(protocol, host, path, username), resp, true); //$NON-NLS-1$
		return new Answer(resp);
	}

	/**
	 * Run the helper's {@code store} action, asking it to persist the given
	 * credentials.
	 *
	 * @param protocol
	 *            the URL scheme
	 * @param host
	 *            the host, including port when non-default
	 * @param path
	 *            the repository path, or {@code null}
	 * @param username
	 *            the username, or {@code null}
	 * @param password
	 *            the password to store, or {@code null}
	 * @param authtype
	 *            the authentication scheme (e.g. {@code Bearer}), or {@code null}
	 * @param credential
	 *            the credential paired with {@code authtype}, or {@code null}
	 * @throws IOException
	 *             if the helper cannot be run
	 * @throws InterruptedException
	 *             if interrupted while waiting for the helper
	 */
	void store(String protocol, String host, String path, String username,
			char[] password, String authtype, char[] credential)
			throws IOException, InterruptedException {
		Credential req = request(protocol, host, path, username);
		req.password = password;
		req.authtype = authtype;
		req.credential = credential;
		run("store", req, new Credential(), false); //$NON-NLS-1$
	}

	/**
	 * Run the helper's {@code erase} action for the given identity.
	 *
	 * @param protocol
	 *            the URL scheme
	 * @param host
	 *            the host, including port when non-default
	 * @param path
	 *            the repository path, or {@code null}
	 * @param username
	 *            the username, or {@code null}
	 * @throws IOException
	 *             if the helper cannot be run
	 * @throws InterruptedException
	 *             if interrupted while waiting for the helper
	 */
	void erase(String protocol, String host, String path,
			String username) throws IOException, InterruptedException {
		run("erase", request(protocol, host, path, username), new Credential(), //$NON-NLS-1$
				false);
	}

	private void run(String operation, Credential req, Credential resp,
			boolean wantOutput) throws IOException, InterruptedException {
		ProcessBuilder pb = process(operation);
		byte[] stdin = req.write(protectProtocol);
		try {
			ExecutionResult r = FS.DETECTED.execute(pb,
					new ByteArrayInputStream(stdin));
			TemporaryBuffer out = r.getStdout();
			TemporaryBuffer err = r.getStderr();
			try {
				if (wantOutput) {
					try (InputStream in = out.openInputStream()) {
						resp.read(in);
					}
				}
			} finally {
				out.destroy();
				err.destroy();
			}
		} finally {
			Arrays.fill(stdin, (byte) 0);
		}
	}

	ProcessBuilder process(String operation) {
		String v = helper.trim();
		String[] action = { operation };
		if (v.startsWith("!")) { //$NON-NLS-1$
			return FS.DETECTED.runInShell(v.substring(1), action);
		}
		if (new File(v).isAbsolute()) {
			return FS.DETECTED.runInShell(v, action);
		}
		// git dispatches "git credential-<name>", which resolves the helper
		// from the git exec-path (not the caller's PATH).
		return FS.DETECTED.runInShell("git credential-" + v, action); //$NON-NLS-1$
	}

	private Credential request(String protocol, String host, String path,
			String username) {
		Credential c = new Credential();
		c.protocol = protocol;
		c.host = host;
		if (useHttpPath && path != null) {
			c.path = path.startsWith("/") ? path.substring(1) : path; //$NON-NLS-1$
		}
		c.username = username;
		c.capaAuthtype = advertiseAuthtype;
		return c;
	}

	/** The username/password a helper returned; secrets must be cleared. */
	static final class Answer {

		private final String username;

		private final char[] password;

		private final String authType;

		private final char[] credential;

		private final long passwordExpiryUtc;

		Answer(Credential c) {
			this.username = c.username;
			this.password = c.password == null ? null : c.password.clone();
			this.authType = c.authtype;
			this.credential = c.credential == null ? null : c.credential.clone();
			this.passwordExpiryUtc = c.passwordExpiryUtc;
			c.clear();
		}

		/**
		 * @param nowUtcSeconds
		 *            the current time in seconds since the epoch
		 * @return whether the credential's expiry has passed
		 */
		boolean isExpired(long nowUtcSeconds) {
			return passwordExpiryUtc != Credential.NO_EXPIRY
					&& passwordExpiryUtc < nowUtcSeconds;
		}

		/**
		 * @return the username, or {@code null}
		 */
		String getUsername() {
			return username;
		}

		/**
		 * @return the password (caller owns the array and must clear it), or
		 *         {@code null}
		 */
		char[] getPassword() {
			return password;
		}

		/**
		 * @return the pre-encoded authentication scheme (e.g. {@code Bearer}), or
		 *         {@code null}
		 */
		String getAuthType() {
			return authType;
		}

		/**
		 * @return the pre-encoded credential paired with {@link #getAuthType()}
		 *         (caller owns the array and must clear it), or {@code null}
		 */
		char[] getCredential() {
			return credential;
		}

		/** Zeroes the secrets. */
		void clear() {
			if (password != null) {
				Arrays.fill(password, (char) 0);
			}
			if (credential != null) {
				Arrays.fill(credential, (char) 0);
			}
		}
	}
}
