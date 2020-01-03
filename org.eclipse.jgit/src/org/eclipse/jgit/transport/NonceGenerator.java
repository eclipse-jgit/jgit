/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;

/**
 * A NonceGenerator is used to create a nonce to be sent out to the pusher who
 * will sign the nonce to prove it is not a replay attack on the push
 * certificate.
 *
 * @since 4.0
 */
public interface NonceGenerator {

	/**
	 * Create nonce to be signed by the pusher
	 *
	 * @param db
	 *            The repository which should be used to obtain a unique String
	 *            such that the pusher cannot forge nonces by pushing to another
	 *            repository at the same time as well and reusing the nonce.
	 * @param timestamp
	 *            The current time in seconds.
	 * @return The nonce to be signed by the pusher
	 * @throws java.lang.IllegalStateException
	 */
	String createNonce(Repository db, long timestamp)
			throws IllegalStateException;

	/**
	 * Verify trustworthiness of the received nonce.
	 *
	 * @param received
	 *            The nonce which was received from the server
	 * @param sent
	 *            The nonce which was originally sent out to the client.
	 * @param db
	 *            The repository which should be used to obtain a unique String
	 *            such that the pusher cannot forge nonces by pushing to another
	 *            repository at the same time as well and reusing the nonce.
	 * @param allowSlop
	 *            If the receiving backend is able to generate slop. This is
	 *            the case for serving via http protocol using more than one
	 *            http frontend. The client would talk to different http
	 *            frontends, which may have a slight difference of time due to
	 * @param slop
	 *            If `allowSlop` is true, this specifies the number of seconds
	 *            which we allow as slop.
	 * @return a NonceStatus indicating the trustworthiness of the received
	 *         nonce.
	 */
	NonceStatus verify(String received, String sent,
			Repository db, boolean allowSlop, int slop);
}
