/*
 * Copyright (C) 2015, Google Inc.
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
	 * @param db
	 *            The repository which should be used to obtain a unique String
	 *            such that the pusher cannot forge nonces by pushing to another
	 *            repository at the same time as well and reusing the nonce.
	 * @param timestamp
	 *            The current time in seconds.
	 * @return The nonce to be signed by the pusher
	 * @throws IllegalStateException
	 */
	public String createNonce(Repository db, long timestamp)
			throws IllegalStateException;

	/**
	 * @param received
	 *            The nonce which was received from the server
	 * @param sent
	 *            The nonce which was originally sent out to the client.
	 * @param db
	 *            The repository which should be used to obtain a unique String
	 *            such that the pusher cannot forge nonces by pushing to another
	 *            repository at the same time as well and reusing the nonce.
	 *
	 * @param allowSlop
	 *            If the receiving backend is is able to generate slop. This is
	 *            the case for serving via http protocol using more than one
	 *            http frontend. The client would talk to different http
	 *            frontends, which may have a slight difference of time due to
	 * @param slop
	 *            If `allowSlop` is true, this specifies the number of seconds
	 *            which we allow as slop.
	 *
	 * @return a NonceStatus indicating the trustworthiness of the received
	 *         nonce.
	 */
	public NonceStatus verify(String received, String sent,
			Repository db, boolean allowSlop, int slop);
}
