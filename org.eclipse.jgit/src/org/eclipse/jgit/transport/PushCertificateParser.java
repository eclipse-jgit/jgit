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

import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_PUSH_CERT;

import java.io.EOFException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.BaseReceivePack.ReceiveConfig;

/**
 * Parser for Push certificates
 */
class PushCertificateParser extends PushCertificate {

	private static final String VERSION = "version "; //$NON-NLS-1$

	private static final String PUSHER = "pusher"; //$NON-NLS-1$

	private static final String PUSHEE = "pushee"; //$NON-NLS-1$

	private static final String NONCE = "nonce"; //$NON-NLS-1$

	/** The individual certificate which is presented to the client */
	private String sentNonce;

	/**
	 * The nonce the pusher signed. This may vary from pushCertNonce See
	 * git-core documentation for reasons.
	 */
	private String receivedNonce;

	/**
	 * The maximum time difference which is acceptable between advertised nonce
	 * and received signed nonce.
	 */
	private int nonceSlopLimit;

	NonceGenerator nonceGenerator;

	/**
	 * used to build up commandlist
	 */
	StringBuilder commandlistBuilder;

	/** Database we write the push certificate into. */
	private Repository db;

	PushCertificateParser(Repository into, ReceiveConfig cfg) {
		nonceSlopLimit = cfg.certNonceSlopLimit;
		nonceGenerator = cfg.certNonceSeed != null
				? new HMACSHA1NonceGenerator(cfg.certNonceSeed)
				: null;
		db = into;
	}

	/**
	 * @return if the server is configured to use signed pushes.
	 */
	public boolean enabled() {
		return nonceGenerator != null;
	}

	/**
	 * @return the whole string for the nonce to be included into the capability
	 *         advertisement.
	 */
	public String getAdvertiseNonce() {
		sentNonce = nonceGenerator.createNonce(db,
				TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
		return CAPABILITY_PUSH_CERT + "=" + sentNonce; //$NON-NLS-1$
	}

	private String parseNextLine(PacketLineIn pckIn, String startingWith)
			throws IOException {
		String s = pckIn.readString();
		if (!s.startsWith(startingWith))
			throw new IOException(MessageFormat.format(
					JGitText.get().errorInvalidPushCert,
					"expected " + startingWith)); //$NON-NLS-1$
		return s.substring(startingWith.length());
	}

	/**
	 * Receive a list of commands from the input encapsulated in a push
	 * certificate. This method doesn't parse the first line "push-cert \NUL
	 * &lt;capabilities&gt;", but assumes the first line including the
	 * capabilities has already been handled by the caller.
	 *
	 * @param pckIn
	 *            where we take the push certificate header from.
	 * @param stateless
	 *            affects nonce verification. When {@code stateless = true} the
	 *            {@code NonceGenerator} will allow for some time skew caused by
	 *            clients disconnected and reconnecting in the stateless smart
	 *            HTTP protocol.
	 * @throws IOException
	 *             if the certificate from the client is badly malformed or the
	 *             client disconnects before sending the entire certificate.
	 */
	public void receiveHeader(PacketLineIn pckIn, boolean stateless)
			throws IOException {
		try {
			String version = parseNextLine(pckIn, VERSION);
			if (!version.equals("0.1")) { //$NON-NLS-1$
				throw new IOException(MessageFormat.format(
						JGitText.get().errorInvalidPushCert,
						"version not supported")); //$NON-NLS-1$
			}
			pusher = parseNextLine(pckIn, PUSHER);
			pushee = parseNextLine(pckIn, PUSHEE);
			receivedNonce = parseNextLine(pckIn, NONCE);
			// an empty line
			if (!pckIn.readString().isEmpty()) {
				throw new IOException(MessageFormat.format(
						JGitText.get().errorInvalidPushCert,
						"expected empty line after header")); //$NON-NLS-1$
			}
		} catch (EOFException eof) {
			throw new IOException(MessageFormat.format(
					JGitText.get().errorInvalidPushCert,
					"broken push certificate header")); //$NON-NLS-1$
		}
		nonceStatus = nonceGenerator.verify(receivedNonce, sentNonce, db,
				stateless, nonceSlopLimit);
	}

	/**
	 * Reads the gpg signature. This method assumes the line "-----BEGIN PGP
	 * SIGNATURE-----\n" has already been parsed and continues parsing until an
	 * "-----END PGP SIGNATURE-----\n" is found.
	 *
	 * @param pckIn
	 *            where we read the signature from.
	 * @throws IOException
	 */
	public void receiveSignature(PacketLineIn pckIn) throws IOException {
		try {
			StringBuilder sig = new StringBuilder();
			String line = pckIn.readStringRaw();
			while (!line.equals("-----END PGP SIGNATURE-----\n")) //$NON-NLS-1$
				sig.append(line);
			signature = sig.toString();
			commandList = commandlistBuilder.toString();
		} catch (EOFException eof) {
			throw new IOException(MessageFormat.format(
					JGitText.get().errorInvalidPushCert,
					"broken push certificate signature")); //$NON-NLS-1$
		}
	}

	/**
	 * @param rawLine
	 */
	public void addCommand(String rawLine) {
		commandlistBuilder.append(rawLine);
	}
}
