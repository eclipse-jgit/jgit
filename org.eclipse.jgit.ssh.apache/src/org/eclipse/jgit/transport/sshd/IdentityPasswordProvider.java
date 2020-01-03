/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * A {@link KeyPasswordProvider} based on a {@link CredentialsProvider}.
 *
 * @since 5.2
 */
public class IdentityPasswordProvider implements KeyPasswordProvider {

	private CredentialsProvider provider;

	/**
	 * The number of times to ask successively for a password for a given
	 * identity resource.
	 */
	private int attempts = 1;

	/**
	 * A simple state object for repeated attempts to get a password for a
	 * resource.
	 */
	protected static class State {

		private int count = 0;

		private char[] password;

		/**
		 * Obtains the current count. The initial count is zero.
		 *
		 * @return the count
		 */
		public int getCount() {
			return count;
		}

		/**
		 * Increments the current count. Should be called for each new attempt
		 * to get a password.
		 *
		 * @return the incremented count.
		 */
		public int incCount() {
			return ++count;
		}

		/**
		 * Remembers the password.
		 *
		 * @param password
		 *            the password
		 */
		public void setPassword(char[] password) {
			if (this.password != null) {
				Arrays.fill(this.password, '\000');
			}
			if (password != null) {
				this.password = password.clone();
			} else {
				this.password = null;
			}
		}

		/**
		 * Retrieves the password from the current attempt.
		 *
		 * @return the password, or {@code null} if none was obtained
		 */
		public char[] getPassword() {
			return password;
		}
	}

	/**
	 * Counts per resource key.
	 */
	private final Map<URIish, State> current = new HashMap<>();

	/**
	 * Creates a new {@link IdentityPasswordProvider} to get the passphrase for
	 * an encrypted identity.
	 *
	 * @param provider
	 *            to use
	 */
	public IdentityPasswordProvider(CredentialsProvider provider) {
		this.provider = provider;
	}

	@Override
	public void setAttempts(int numberOfPasswordPrompts) {
		if (numberOfPasswordPrompts <= 0) {
			throw new IllegalArgumentException(
					"Number of password prompts must be >= 1"); //$NON-NLS-1$
		}
		attempts = numberOfPasswordPrompts;
	}

	@Override
	public int getAttempts() {
		return Math.max(1, attempts);
	}

	@Override
	public char[] getPassphrase(URIish uri, int attempt) throws IOException {
		return getPassword(uri, attempt,
				current.computeIfAbsent(uri, r -> new State()));
	}

	/**
	 * Retrieves a password to decrypt a private key.
	 *
	 * @param uri
	 *            identifying the resource to obtain a password for
	 * @param attempt
	 *            number of previous attempts to get a passphrase
	 * @param state
	 *            encapsulating state information about attempts to get the
	 *            password
	 * @return the password, or {@code null} or the empty string if none
	 *         available.
	 * @throws IOException
	 *             if an error occurs
	 */
	protected char[] getPassword(URIish uri, int attempt, @NonNull State state)
			throws IOException {
		state.setPassword(null);
		state.incCount();
		String message = state.count == 1 ? SshdText.get().keyEncryptedMsg
				: SshdText.get().keyEncryptedRetry;
		char[] pass = getPassword(uri, message);
		state.setPassword(pass);
		return pass;
	}

	private char[] getPassword(URIish uri, String message) {
		if (provider == null) {
			return null;
		}
		List<CredentialItem> items = new ArrayList<>(2);
		items.add(new CredentialItem.InformationalMessage(
				format(message, uri)));
		CredentialItem.Password password = new CredentialItem.Password(
				SshdText.get().keyEncryptedPrompt);
		items.add(password);
		try {
			provider.get(uri, items);
			char[] pass = password.getValue();
			if (pass == null) {
				throw new CancellationException(
						SshdText.get().authenticationCanceled);
			}
			return pass.clone();
		} finally {
			password.clear();
		}
	}

	/**
	 * Invoked to inform the password provider about the decoding result.
	 *
	 * @param uri
	 *            identifying the key resource the key was attempted to be
	 *            loaded from
	 * @param state
	 *            associated with this key
	 * @param password
	 *            the password that was attempted
	 * @param err
	 *            the attempt result - {@code null} for success
	 * @return how to proceed in case of error
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	protected boolean keyLoaded(URIish uri,
			State state, char[] password, Exception err)
			throws IOException, GeneralSecurityException {
		if (err == null) {
			return false; // Success, don't retry
		} else if (err instanceof GeneralSecurityException) {
			throw new InvalidKeyException(
					format(SshdText.get().identityFileCannotDecrypt, uri), err);
		} else {
			// Unencrypted key (state == null && password == null), or exception
			// before having asked for the password (state != null && password
			// == null; might also be a user cancellation), or number of
			// attempts exhausted.
			if (state == null || password == null
					|| state.getCount() >= attempts) {
				return false;
			}
			return true;
		}
	}

	@Override
	public boolean keyLoaded(URIish uri, int attempt, Exception error)
			throws IOException, GeneralSecurityException {
		State state = null;
		boolean retry = false;
		try {
			state = current.get(uri);
			retry = keyLoaded(uri, state,
					state == null ? null : state.getPassword(), error);
		} finally {
			if (state != null) {
				state.setPassword(null);
			}
			if (!retry) {
				current.remove(uri);
			}
		}
		return retry;
	}
}
