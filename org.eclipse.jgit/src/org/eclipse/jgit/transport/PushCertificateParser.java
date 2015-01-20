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
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.BaseReceivePack.ReceiveConfig;

/**
 * @author sbeller
 *
 */
public class PushCertificateParser extends PushCertificate {

	private static final String NONCE_UNSOLICITED = "UNSOLICITED"; //$NON-NLS-1$

	private static final String NONCE_BAD = "BAD"; //$NON-NLS-1$

	private static final String NONCE_MISSING = "MISSING"; //$NON-NLS-1$

	private static final String NONCE_OK = "OK"; //$NON-NLS-1$

	private static final String NONCE_SLOP = "SLOP"; //$NON-NLS-1$

	private final Pattern versionPattern = Pattern.compile("version\\s(\\S)$"); //$NON-NLS-1$

	private final Pattern pusherPattern = Pattern.compile("pusher\\s(\\S)$"); //$NON-NLS-1$

	private final Pattern pusheePattern = Pattern.compile("pushee\\s(\\S)$"); //$NON-NLS-1$

	private final Pattern noncePattern = Pattern.compile("nonce\\s(\\S)$"); //$NON-NLS-1$

	/** The seed this server is using */
	private String seed;

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
	 * a unique String for the repository, so you cannot forge a push
	 * certificate for a different repository on a server.
	 */
	protected String dirPath;

	/**
	 * used to build up commandlist
	 */
	protected StringBuilder commandlistBuilder;

	/** Database we write the push certificate into. */
	private final Repository db;

	PushCertificateParser(final Repository into, ReceiveConfig cfg,
			String dirPath) {
		seed = cfg.certNonceSeed;
		if (seed != null)
			sentNonce = nonceGenerator.createNonce(seed,
					dirPath, TimeUnit.MILLISECONDS.toSeconds(
							System.currentTimeMillis()));

		nonceSlopLimit = cfg.certNonceSlopLimit;
		this.dirPath = dirPath;
		nonceGenerator = new HMACSHA1NonceGenerator();
		db = into;
	}

	/**
	 * @return if the server is configured to use signed pushes.
	 */
	public boolean enabled() {
		return seed != null;
	}

	/**
	 * @return the whole string for the nonce to be included into the capability
	 *         advertisement.
	 */
	public String getAdvertiseNonce() {
		return CAPABILITY_PUSH_CERT + "=" + //$NON-NLS-1$
				nonceGenerator.createNonce(seed, dirPath, TimeUnit.MILLISECONDS
						.toSeconds(System.currentTimeMillis()));
	}

	/**
	 * Receive a list of commands from the input encapsulated in a push
	 * certificate. This method doesn't deal with the first line "push-cert \NUL
	 * <capabilities>", but assumes the first line including the capabilities
	 * has already been dealt with.
	 *
	 * @param pckIn
	 *            where we take the push certificate header from.
	 *
	 * @throws IOException
	 */
	public void receiveHeader(PacketLineIn pckIn) throws IOException {
		try {
			String version = versionPattern.matcher(pckIn.readStringRaw())
					.group(1);
			if (version.equals("0.1")) { //$NON-NLS-1$
				pusher = pusherPattern.matcher(pckIn.readStringRaw()).group(1);
				pushee = pusheePattern.matcher(pckIn.readStringRaw()).group(1);
				receivedNonce = noncePattern.matcher(pckIn.readStringRaw())
						.group(1);
				// todo: readStringRaw() once more for an LF
			} else {
				throw new IOException(MessageFormat.format(
						JGitText.get().errorInvalidPushCert,
						"version not supported")); //$NON-NLS-1$
			}
		} catch (EOFException eof) {
			throw new IOException(MessageFormat.format(
					JGitText.get().errorInvalidPushCert,
					"broken push certificate Header")); //$NON-NLS-1$
		}
		nonceStatus = checkNonce();
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
			while (!line.equals("-----END PGP SIGNATURE-----")) //$NON-NLS-1$
				sig.append(line);
			signature = sig.toString();
			commandList = commandlistBuilder.toString();
		} catch (EOFException eof) {
			throw new IOException(MessageFormat.format(
					JGitText.get().errorInvalidPushCert,
					"broken push certificate signature")); //$NON-NLS-1$
		}
		writePushCertificate();
		verifySignedPushCommands(signature); // todo: move somewhere else?
	}

	/**
	 * @param rawLine
	 */
	public void addCommand(String rawLine) {
		commandlistBuilder.append(rawLine);
	}

	/**
	 * @return a message explaining the state of the signed nonce by the pusher
	 */
	protected String checkNonce() {
		if (receivedNonce.isEmpty())
			return NONCE_MISSING;
		else if (sentNonce.isEmpty())
			return NONCE_UNSOLICITED;
		else if (receivedNonce.equals(sentNonce))
			return NONCE_OK;

		// TODO: Do we have a stateless rpc for jgit?
		// if (!statelessRPC)
		// return NONCE_BAD;

		/* nonce is concat(<seconds-since-epoch>, "-", <hmac>) */
		int idxSent = sentNonce.indexOf('-');
		int idxRecv = receivedNonce.indexOf('-');
		if (idxSent == -1 || idxRecv == -1)
			return NONCE_BAD;

		long signedStamp = 0;
		long advertisedStamp = 0;
		try {
			signedStamp = Integer.parseInt(receivedNonce.substring(0, idxRecv));
			advertisedStamp = Integer.parseInt(sentNonce.substring(0, idxSent));
		} catch (Exception e) {
			return NONCE_BAD;
		}

		// what we would have signed earlier
		String expect = nonceGenerator.createNonce(seed, dirPath, signedStamp);

		if (!expect.equals(receivedNonce))
			return NONCE_BAD;

		long nonceStampSlop = Math.abs(advertisedStamp - signedStamp);

		if (nonceSlopLimit != 0 && nonceStampSlop <= nonceSlopLimit) {
			return NONCE_OK;
		} else {
			return NONCE_SLOP;
		}
	}

	/**
	 * @param commandList
	 * @return if the signature could be verified.
	 */
	protected boolean verifySignedPushCommands(String commandList) {
		// commandlist and signature are available here. We also need a
		// public key to have all required data for verification.
		/**
		 * crypto is hard, if you don't have a library for it. We cannot check
		 * if the signature is good, so let's always fail.
		 */
		return false;
	}

	/**
	 * Writes all the lines between push-cert-begin and push-cert-end to a file.
	 *
	 * @throws IOException
	 */
	protected void writePushCertificate() throws IOException {
		ObjectInserter ins = db.newObjectInserter();

		// todo: also include the signature here
		byte[] data = commandList.getBytes();
		try {
			ins.insert(Constants.OBJ_BLOB, data);
			ins.flush();
		} finally {
			ins.release();
		}
	}
}
