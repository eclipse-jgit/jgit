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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;

/**
 * The nonce generator which was first introduced to git-core.
 *
 * @since 4.0
 */
public class HMACSHA1NonceGenerator implements NonceGenerator {

	private Mac mac;

	/**
	 * @param seed
	 * @throws IllegalStateException
	 */
	public HMACSHA1NonceGenerator(String seed) throws IllegalStateException {
		try {
			byte[] keyBytes = seed.getBytes(ISO_8859_1);
			SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1"); //$NON-NLS-1$
			mac = Mac.getInstance("HmacSHA1"); //$NON-NLS-1$
			mac.init(signingKey);
		} catch (InvalidKeyException e) {
			throw new IllegalStateException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public synchronized String createNonce(Repository repo, long timestamp)
			throws IllegalStateException {
		String path;
		if (repo instanceof DfsRepository) {
			path = ((DfsRepository) repo).getDescription().getRepositoryName();
		} else {
			File directory = repo.getDirectory();
			if (directory != null) {
				path = directory.getPath();
			} else {
				throw new IllegalStateException();
			}
		}

		String input = path + ":" + String.valueOf(timestamp); //$NON-NLS-1$
		byte[] rawHmac = mac.doFinal(input.getBytes(UTF_8));
		return Long.toString(timestamp) + "-" + toHex(rawHmac); //$NON-NLS-1$
	}

	@Override
	public NonceStatus verify(String received, String sent,
			Repository db, boolean allowSlop, int slop) {
		if (received.isEmpty()) {
			return NonceStatus.MISSING;
		} else if (sent.isEmpty()) {
			return NonceStatus.UNSOLICITED;
		} else if (received.equals(sent)) {
			return NonceStatus.OK;
		}

		if (!allowSlop) {
			return NonceStatus.BAD;
		}

		/* nonce is concat(<seconds-since-epoch>, "-", <hmac>) */
		int idxSent = sent.indexOf('-');
		int idxRecv = received.indexOf('-');
		if (idxSent == -1 || idxRecv == -1) {
			return NonceStatus.BAD;
		}

		String signedStampStr = received.substring(0, idxRecv);
		String advertisedStampStr = sent.substring(0, idxSent);
		long signedStamp;
		long advertisedStamp;
		try {
			signedStamp = Long.parseLong(signedStampStr);
			advertisedStamp = Long.parseLong(advertisedStampStr);
		} catch (IllegalArgumentException e) {
			return NonceStatus.BAD;
		}

		// what we would have signed earlier
		String expect = createNonce(db, signedStamp);

		if (!expect.equals(received)) {
			return NonceStatus.BAD;
		}

		long nonceStampSlop = Math.abs(advertisedStamp - signedStamp);

		if (nonceStampSlop <= slop) {
			return NonceStatus.OK;
		} else {
			return NonceStatus.SLOP;
		}
	}

	private static final String HEX = "0123456789ABCDEF"; //$NON-NLS-1$

	private static String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(2 * bytes.length);
		for (byte b : bytes) {
			builder.append(HEX.charAt((b & 0xF0) >> 4));
			builder.append(HEX.charAt(b & 0xF));
		}
		return builder.toString();
	}
}
