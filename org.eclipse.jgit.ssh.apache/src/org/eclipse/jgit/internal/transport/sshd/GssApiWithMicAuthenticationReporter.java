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

import java.util.List;

import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository.AttributeKey;

/**
 * Callback interface for recording authentication state in
 * {@link GssApiWithMicAuthentication}.
 */
public interface GssApiWithMicAuthenticationReporter {

	/**
	 * An {@link AttributeKey} for a {@link ClientSession} holding the
	 * {@link GssApiWithMicAuthenticationReporter}.
	 */
	static final AttributeKey<GssApiWithMicAuthenticationReporter> GSS_AUTHENTICATION_REPORTER = new AttributeKey<>();

	/**
	 * Called when a new authentication attempt is made.
	 *
	 * @param session
	 *            the {@link ClientSession}
	 * @param service
	 *            the name of the requesting SSH service name
	 * @param mechanism
	 *            the OID of the mechanism used
	 */
	default void signalAuthenticationAttempt(ClientSession session,
			String service, String mechanism) {
		// nothing
	}

	/**
	 * Called when there are no more mechanisms to try.
	 *
	 * @param session
	 *            the {@link ClientSession}
	 * @param service
	 *            the name of the requesting SSH service name
	 */
	default void signalAuthenticationExhausted(ClientSession session,
			String service) {
		// nothing
	}

	/**
	 * Called when authentication was succeessful.
	 *
	 * @param session
	 *            the {@link ClientSession}
	 * @param service
	 *            the name of the requesting SSH service name
	 * @param mechanism
	 *            the OID of the mechanism used
	 */
	default void signalAuthenticationSuccess(ClientSession session,
			String service, String mechanism) {
		// nothing
	}

	/**
	 * Called when the authentication was not successful.
	 *
	 * @param session
	 *            the {@link ClientSession}
	 * @param service
	 *            the name of the requesting SSH service name
	 * @param mechanism
	 *            the OID of the mechanism used
	 * @param partial
	 *            {@code true} if authentication was partially successful,
	 *            meaning one continues with additional authentication methods
	 *            given by {@code serverMethods}
	 * @param serverMethods
	 *            the {@link List} of authentication methods that can continue
	 */
	default void signalAuthenticationFailure(ClientSession session,
			String service, String mechanism, boolean partial,
			List<String> serverMethods) {
		// nothing
	}
}
