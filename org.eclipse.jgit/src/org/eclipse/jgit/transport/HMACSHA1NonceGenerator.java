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

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.NonceGenerator;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;

/**
 * The nonce generator which was first introduced to git-core.
 */
public class HMACSHA1NonceGenerator implements NonceGenerator {

	private String sentNonce;

	public String createNonce(String seed, final Repository repo, long timestamp)
			throws IllegalStateException {
		String path = repo.getDirectory() != null
				? repo.getDirectory().getPath()
				: /* TODO getRepositoryName for dfs here */
				""; //$NON-NLS-1$
		try {
			byte[] keyBytes = seed.getBytes("UTF-8"); //$NON-NLS-1$
			SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1"); //$NON-NLS-1$
			Mac mac = Mac.getInstance("HmacSHA1"); //$NON-NLS-1$
			mac.init(signingKey);
			String input = path + ":" + String.valueOf(timestamp); //$NON-NLS-1$
			byte[] rawHmac = mac.doFinal(input.getBytes("UTF-8")); //$NON-NLS-1$
			sentNonce = String.format("%d-%20X", new Long(timestamp), rawHmac); //$NON-NLS-1$
			return sentNonce;
		} catch (InvalidKeyException e) {
			throw new IllegalStateException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * @return a message explaining the state of the signed nonce by the pusher
	 */
	public NonceStatus verify(String received, String sent, String seed,
			Repository db, int allowedSlope) {
		if (received.isEmpty())
			return NonceStatus.MISSING;
		else if (sentNonce.isEmpty())
			return NonceStatus.UNSOLICITED;
		else if (received.equals(sentNonce))
			return NonceStatus.OK;

		// TODO: Do we have a stateless rpc for jgit?
		// Yes. !isBiDirectionalPipe() in BaseReceivePack.
		// if (!statelessRPC)
		// return NONCE_BAD;

		/* nonce is concat(<seconds-since-epoch>, "-", <hmac>) */
		int idxSent = sentNonce.indexOf('-');
		int idxRecv = received.indexOf('-');
		if (idxSent == -1 || idxRecv == -1)
			return NonceStatus.BAD;

		long signedStamp = 0;
		long advertisedStamp = 0;
		try {
			signedStamp = Integer.parseInt(received.substring(0, idxRecv));
			advertisedStamp = Integer.parseInt(sentNonce.substring(0, idxSent));
		} catch (Exception e) {
			return NonceStatus.BAD;
		}

		// what we would have signed earlier
		String expect = createNonce(seed, db, signedStamp);

		if (!expect.equals(received))
			return NonceStatus.BAD;

		long nonceStampSlop = Math.abs(advertisedStamp - signedStamp);

		if (allowedSlope != 0 && nonceStampSlop <= allowedSlope) {
			return NonceStatus.OK;
		} else {
			return NonceStatus.SLOP;
		}
	}
}
