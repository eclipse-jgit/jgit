/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.transport.sshd;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.URISyntaxException;
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
 * A {@link RepeatingFilePasswordProvider} based on a
 * {@link CredentialsProvider}.
 *
 * @since 5.2
 */
public class IdentityPasswordProvider implements RepeatingFilePasswordProvider {

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
	private final Map<String, State> current = new HashMap<>();

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
		RepeatingFilePasswordProvider.super.setAttempts(
				numberOfPasswordPrompts);
		attempts = numberOfPasswordPrompts;
	}

	@Override
	public int getAttempts() {
		return Math.max(1, attempts);
	}

	@Override
	public String getPassword(String resourceKey) throws IOException {
		char[] pass = getPassword(resourceKey,
				current.computeIfAbsent(resourceKey, r -> new State()));
		if (pass == null) {
			return null;
		}
		try {
			return new String(pass);
		} finally {
			Arrays.fill(pass, '\000');
		}
	}

	/**
	 * Retrieves a password to decrypt a private key.
	 *
	 * @param resourceKey
	 *            identifying the resource to obtain a password for
	 * @param state
	 *            encapsulating state information about attempts to get the
	 *            password
	 * @return the password, or {@code null} or the empty string if none
	 *         available.
	 * @throws IOException
	 *             if an error occurs
	 */
	protected char[] getPassword(String resourceKey, @NonNull State state)
			throws IOException {
		state.setPassword(null);
		state.incCount();
		String message = state.count == 1 ? SshdText.get().keyEncryptedMsg
				: SshdText.get().keyEncryptedRetry;
		char[] pass = getPassword(resourceKey, message);
		state.setPassword(pass);
		return pass;
	}

	/**
	 * Creates a {@link URIish} from a given string. The
	 * {@link CredentialsProvider} uses uris as resource identifications.
	 *
	 * @param resourceKey
	 *            to convert
	 * @return the uri
	 */
	protected URIish toUri(String resourceKey) {
		try {
			return new URIish(resourceKey);
		} catch (URISyntaxException e) {
			return new URIish().setPath(resourceKey); // Doesn't check!!
		}
	}

	private char[] getPassword(String resourceKey, String message) {
		if (provider == null) {
			return null;
		}
		URIish file = toUri(resourceKey);
		List<CredentialItem> items = new ArrayList<>(2);
		items.add(new CredentialItem.InformationalMessage(
				format(message, resourceKey)));
		CredentialItem.Password password = new CredentialItem.Password(
				SshdText.get().keyEncryptedPrompt);
		items.add(password);
		try {
			provider.get(file, items);
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
	 * @param resourceKey
	 *            the resource key
	 * @param state
	 *            associated with this key
	 * @param password
	 *            the password that was attempted
	 * @param err
	 *            the attempt result - {@code null} for success
	 * @return how to proceed in case of error
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * @see #handleDecodeAttemptResult(String, String, Exception)
	 */
	protected ResourceDecodeResult handleDecodeAttemptResult(String resourceKey,
			State state, char[] password, Exception err)
			throws IOException, GeneralSecurityException {
		if (err == null) {
			return null;
		} else if (err instanceof GeneralSecurityException) {
			throw new InvalidKeyException(
					format(SshdText.get().identityFileCannotDecrypt,
							resourceKey),
					err);
		} else {
			// Unencrypted key (state == null && password == null), or exception
			// before having asked for the password (state != null && password
			// == null; might also be a user cancellation), or number of
			// attempts exhausted.
			if (state == null || password == null
					|| state.getCount() >= attempts) {
				return ResourceDecodeResult.TERMINATE;
			}
			return ResourceDecodeResult.RETRY;
		}
	}

	@Override
	public ResourceDecodeResult handleDecodeAttemptResult(String resourceKey,
			String password, Exception err)
			throws IOException, GeneralSecurityException {
		ResourceDecodeResult result = null;
		State state = null;
		try {
			state = current.get(resourceKey);
			result = handleDecodeAttemptResult(resourceKey, state,
					state == null ? null : state.getPassword(), err);
		} finally {
			if (state != null) {
				state.setPassword(null);
			}
			if (result != ResourceDecodeResult.RETRY) {
				current.remove(resourceKey);
			}
		}
		return result;
	}
}
