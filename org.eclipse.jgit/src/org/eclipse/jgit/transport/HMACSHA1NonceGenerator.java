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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
	 * Constructor for HMACSHA1NonceGenerator.
	 *
	 * @param seed
	 *            seed the generator
	 * @throws java.lang.IllegalStateException
	 */
	public HMACSHA1NonceGenerator(String seed) throws IllegalStateException {
		try {
			byte[] keyBytes = seed.getBytes(ISO_8859_1);
			SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1"); //$NON-NLS-1$
			mac = Mac.getInstance("HmacSHA1"); //$NON-NLS-1$
			mac.init(signingKey);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public synchronized String createNonce(Repository repo, long timestamp)
			throws IllegalStateException {
		String input = repo.getIdentifier() + ":" + String.valueOf(timestamp); //$NON-NLS-1$
		byte[] rawHmac = mac.doFinal(input.getBytes(UTF_8));
		return Long.toString(timestamp) + "-" + toHex(rawHmac); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
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
		}
		return NonceStatus.SLOP;
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
