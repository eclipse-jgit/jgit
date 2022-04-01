/*
 * Copyright (C) 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.eclipse.jgit.internal.transport.sshd.CachingKeyPairProvider.getKeyId;

import java.security.KeyPair;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.sshd.client.auth.password.PasswordAuthenticationReporter;
import org.apache.sshd.client.auth.password.UserAuthPassword;
import org.apache.sshd.client.auth.pubkey.PublicKeyAuthenticationReporter;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;

/**
 * Provides a log of authentication attempts for a {@link ClientSession}.
 */
public class AuthenticationLogger {

	private final List<String> messages = new ArrayList<>();

	// We're interested in this log only in the failure case, so we don't need
	// to log authentication success.

	private final PublicKeyAuthenticationReporter pubkeyLogger = new PublicKeyAuthenticationReporter() {

		private boolean hasAttempts;

		@Override
		public void signalAuthenticationAttempt(ClientSession session,
				String service, KeyPair identity, String signature)
				throws Exception {
			hasAttempts = true;
			String message;
			if (identity.getPrivate() == null) {
				// SSH agent key
				message = MessageFormat.format(
						SshdText.get().authPubkeyAttemptAgent,
						UserAuthPublicKey.NAME, KeyUtils.getKeyType(identity),
						getKeyId(session, identity), signature);
			} else {
				message = MessageFormat.format(
						SshdText.get().authPubkeyAttempt,
						UserAuthPublicKey.NAME, KeyUtils.getKeyType(identity),
						getKeyId(session, identity), signature);
			}
			messages.add(message);
		}

		@Override
		public void signalAuthenticationExhausted(ClientSession session,
				String service) throws Exception {
			String message;
			if (hasAttempts) {
				message = MessageFormat.format(
						SshdText.get().authPubkeyExhausted,
						UserAuthPublicKey.NAME);
			} else {
				message = MessageFormat.format(SshdText.get().authPubkeyNoKeys,
						UserAuthPublicKey.NAME);
			}
			messages.add(message);
			hasAttempts = false;
		}

		@Override
		public void signalAuthenticationFailure(ClientSession session,
				String service, KeyPair identity, boolean partial,
				List<String> serverMethods) throws Exception {
			String message;
			if (partial) {
				message = MessageFormat.format(
						SshdText.get().authPubkeyPartialSuccess,
						UserAuthPublicKey.NAME, KeyUtils.getKeyType(identity),
						getKeyId(session, identity), serverMethods);
			} else {
				message = MessageFormat.format(
						SshdText.get().authPubkeyFailure,
						UserAuthPublicKey.NAME, KeyUtils.getKeyType(identity),
						getKeyId(session, identity));
			}
			messages.add(message);
		}
	};

	private final PasswordAuthenticationReporter passwordLogger = new PasswordAuthenticationReporter() {

		private int attempts;

		@Override
		public void signalAuthenticationAttempt(ClientSession session,
				String service, String oldPassword, boolean modified,
				String newPassword) throws Exception {
			attempts++;
			String message;
			if (modified) {
				message = MessageFormat.format(
						SshdText.get().authPasswordChangeAttempt,
						UserAuthPassword.NAME, Integer.valueOf(attempts));
			} else {
				message = MessageFormat.format(
						SshdText.get().authPasswordAttempt,
						UserAuthPassword.NAME, Integer.valueOf(attempts));
			}
			messages.add(message);
		}

		@Override
		public void signalAuthenticationExhausted(ClientSession session,
				String service) throws Exception {
			String message;
			if (attempts > 0) {
				message = MessageFormat.format(
						SshdText.get().authPasswordExhausted,
						UserAuthPassword.NAME);
			} else {
				message = MessageFormat.format(
						SshdText.get().authPasswordNotTried,
						UserAuthPassword.NAME);
			}
			messages.add(message);
			attempts = 0;
		}

		@Override
		public void signalAuthenticationFailure(ClientSession session,
				String service, String password, boolean partial,
				java.util.List<String> serverMethods) throws Exception {
			String message;
			if (partial) {
				message = MessageFormat.format(
						SshdText.get().authPasswordPartialSuccess,
						UserAuthPassword.NAME, serverMethods);
			} else {
				message = MessageFormat.format(
						SshdText.get().authPasswordFailure,
						UserAuthPassword.NAME);
			}
			messages.add(message);
		}
	};

	private final GssApiWithMicAuthenticationReporter gssReporter = new GssApiWithMicAuthenticationReporter() {

		private boolean hasAttempts;

		@Override
		public void signalAuthenticationAttempt(ClientSession session,
				String service, String mechanism) {
			hasAttempts = true;
			String message = MessageFormat.format(
					SshdText.get().authGssApiAttempt,
					GssApiWithMicAuthFactory.NAME, mechanism);
			messages.add(message);
		}

		@Override
		public void signalAuthenticationExhausted(ClientSession session,
				String service) {
			String message;
			if (hasAttempts) {
				message = MessageFormat.format(
						SshdText.get().authGssApiExhausted,
						GssApiWithMicAuthFactory.NAME);
			} else {
				message = MessageFormat.format(
						SshdText.get().authGssApiNotTried,
						GssApiWithMicAuthFactory.NAME);
			}
			messages.add(message);
			hasAttempts = false;
		}

		@Override
		public void signalAuthenticationFailure(ClientSession session,
				String service, String mechanism, boolean partial,
				java.util.List<String> serverMethods) {
			String message;
			if (partial) {
				message = MessageFormat.format(
						SshdText.get().authGssApiPartialSuccess,
						GssApiWithMicAuthFactory.NAME, mechanism,
						serverMethods);
			} else {
				message = MessageFormat.format(
						SshdText.get().authGssApiFailure,
						GssApiWithMicAuthFactory.NAME, mechanism);
			}
			messages.add(message);
		}
	};

	/**
	 * Creates a new {@link AuthenticationLogger} and configures the
	 * {@link ClientSession} to report authentication attempts through this
	 * instance.
	 *
	 * @param session
	 *            to configure
	 */
	public AuthenticationLogger(ClientSession session) {
		session.setPublicKeyAuthenticationReporter(pubkeyLogger);
		session.setPasswordAuthenticationReporter(passwordLogger);
		session.setAttribute(
				GssApiWithMicAuthenticationReporter.GSS_AUTHENTICATION_REPORTER,
				gssReporter);
		// TODO: keyboard-interactive? sshd 2.8.0 has no callback
		// interface for it.
	}

	/**
	 * Retrieves the log messages for the authentication attempts.
	 *
	 * @return the messages as an unmodifiable list
	 */
	public List<String> getLog() {
		return Collections.unmodifiableList(messages);
	}

	/**
	 * Drops all previously recorded log messages.
	 */
	public void clear() {
		messages.clear();
	}
}
