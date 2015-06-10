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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.BaseReceivePack.ReceiveConfig;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Parser for signed push certificates.
 *
 * <p><strong>NOTE</strong>: The fact that this class extends {@link
 * PushCertificate} should be considered deprecated.
 *
 * @since 4.0
 */
public class PushCertificateParser extends PushCertificate {
	static final String BEGIN_SIGNATURE =
			"-----BEGIN PGP SIGNATURE-----\n"; //$NON-NLS-1$
	static final String END_SIGNATURE =
			"-----END PGP SIGNATURE-----\n"; //$NON-NLS-1$

	static final String VERSION = "certificate version"; //$NON-NLS-1$

	static final String PUSHER = "pusher"; //$NON-NLS-1$

	static final String PUSHEE = "pushee"; //$NON-NLS-1$

	static final String NONCE = "nonce"; //$NON-NLS-1$

	private static final String VERSION_0_1 = "0.1"; //$NON-NLS-1$

	private static final String END_CERT = "push-cert-end\n"; //$NON-NLS-1$

	private String version;
	private PersonIdent pusher;
	private String pushee;

	/** The nonce that was sent to the client. */
	private String sentNonce;

	/**
	 * The nonce the pusher signed.
	 * <p>
	 * This may vary from {@link #sentNonce}; see git-core documentation for
	 * reasons.
	 */
	private String receivedNonce;

	private NonceStatus nonceStatus;
	private String signature;

	/** Database we write the push certificate into. */
	private final Repository db;

	/**
	 * The maximum time difference which is acceptable between advertised nonce
	 * and received signed nonce.
	 */
	private final int nonceSlopLimit;

	private final NonceGenerator nonceGenerator;
	private final List<ReceiveCommand> commands;

	@SuppressWarnings("deprecation")
	PushCertificateParser(Repository into, ReceiveConfig cfg) {
		nonceSlopLimit = cfg.certNonceSlopLimit;
		nonceGenerator = cfg.certNonceSeed != null
				? new HMACSHA1NonceGenerator(cfg.certNonceSeed)
				: null;
		db = into;
		commands = new ArrayList<>();
	}

	/**
	 * Create a certificate with all null values.
	 *
	 * @deprecated will be removed in a future version; use
	 *             {@link BaseReceivePack#getPushCertificate()}.
	 * @since 4.0
	 */
	@Deprecated
	public PushCertificateParser() {
		super();
		db = null;
		nonceSlopLimit = 0;
		nonceGenerator = null;
		commands = null;
	}

	/**
	 * @deprecated use <code>{@link #build()}.getSignature()</code>.
	 * @since 4.0
	 */
	@Deprecated
	@Override
	public String getSignature() {
		return signature;
	}

	/**
	 * @deprecated use <code>{@link #build()}.getCommands()</code>.
	 * @since 4.0
	 */
	@Deprecated
	@Override
	public String getCommandList() {
		return getCommandBuilder(commands).toString();
	}

	/**
	 * @deprecated use <code>{@link #build()}.getPusher()</code>.
	 * @since 4.0
	 */
	@Deprecated
	@Override
	public String getPusher() {
		return pusher.toExternalString();
	}

	/**
	 * @deprecated use <code>{@link #build()}.getPushee()</code>.
	 * @since 4.0
	 */
	@Deprecated
	@Override
	public String getPushee() {
		return pushee;
	}

	/**
	 * @deprecated use <code>{@link #build()}.getNonceStatus()</code>.
	 * @since 4.0
	 */
	@Deprecated
	@Override
	public NonceStatus getNonceStatus() {
		return nonceStatus;
	}

	/**
	 * @return the parsed certificate, or null if push certificates are disabled.
	 * @throws IOException
	 *             if the push certificate has missing or invalid fields.
	 * @since 4.1
	 */
	public PushCertificate build() throws IOException {
		if (nonceGenerator == null) {
			return null;
		}
		try {
			return new PushCertificate(version, pusher, pushee, receivedNonce,
					nonceStatus, Collections.unmodifiableList(commands), signature);
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	/**
	 * @return if the server is configured to use signed pushes.
	 * @since 4.0
	 */
	public boolean enabled() {
		return nonceGenerator != null;
	}

	/**
	 * @return the whole string for the nonce to be included into the capability
	 *         advertisement, or null if push certificates are disabled.
	 * @since 4.0
	 */
	public String getAdvertiseNonce() {
		String nonce = sentNonce();
		if (nonce == null) {
			return null;
		}
		return CAPABILITY_PUSH_CERT + '=' + nonce;
	}

	private String sentNonce() {
		if (sentNonce == null && nonceGenerator != null) {
			sentNonce = nonceGenerator.createNonce(db,
					TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
		}
		return sentNonce;
	}

	private static String parseHeader(PacketLineIn pckIn, String header)
			throws IOException {
		String s = pckIn.readString();
		if (s.length() <= header.length()
				|| !s.startsWith(header)
				|| s.charAt(header.length()) != ' ') {
			throw new IOException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidHeader, header));
		}
		return s.substring(header.length() + 1);
	}

	/**
	 * Receive a list of commands from the input encapsulated in a push
	 * certificate.
	 * <p>
	 * This method doesn't parse the first line {@code "push-cert \NUL
	 * &lt;capabilities&gt;"}, but assumes the first line including the
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
	 * @since 4.0
	 */
	public void receiveHeader(PacketLineIn pckIn, boolean stateless)
			throws IOException {
		try {
			version = parseHeader(pckIn, VERSION);
			if (!version.equals(VERSION_0_1)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().pushCertificateInvalidFieldValue, VERSION, version));
			}
			String pusherStr = parseHeader(pckIn, PUSHER);
			pusher = RawParseUtils.parsePersonIdent(pusherStr);
			if (pusher == null) {
				throw new IOException(MessageFormat.format(
						JGitText.get().pushCertificateInvalidFieldValue,
						PUSHER, pusherStr));
			}
			pushee = parseHeader(pckIn, PUSHEE);
			receivedNonce = parseHeader(pckIn, NONCE);
			// An empty line.
			if (!pckIn.readString().isEmpty()) {
				throw new IOException(
						JGitText.get().pushCertificateInvalidHeader);
			}
		} catch (EOFException eof) {
			throw new IOException(
					JGitText.get().pushCertificateInvalidHeader, eof);
		}
		nonceStatus = nonceGenerator != null
				? nonceGenerator.verify(
					receivedNonce, sentNonce(), db, stateless, nonceSlopLimit)
				: NonceStatus.UNSOLICITED;
	}

	/**
	 * Read the PGP signature.
	 * <p>
	 * This method assumes the line
	 * {@code "-----BEGIN PGP SIGNATURE-----\n"} has already been parsed,
	 * and continues parsing until an {@code "-----END PGP SIGNATURE-----\n"} is
	 * found, followed by {@code "push-cert-end\n"}.
	 *
	 * @param pckIn
	 *            where we read the signature from.
	 * @throws IOException
	 *             if the signature is invalid.
	 * @since 4.0
	 */
	public void receiveSignature(PacketLineIn pckIn) throws IOException {
		try {
			StringBuilder sig = new StringBuilder();
			String line;
			while (!(line = pckIn.readStringRaw()).equals(END_SIGNATURE)) {
				sig.append(line);
			}
			signature = sig.toString();
			if (!pckIn.readStringRaw().equals(END_CERT)) {
				throw new IOException(JGitText.get().pushCertificateInvalidSignature);
			}
		} catch (EOFException eof) {
			throw new IOException(
					JGitText.get().pushCertificateInvalidSignature, eof);
		}
	}

	/**
	 * Add a command to the signature.
	 *
	 * @param cmd the command.
	 * @since 4.1
	 */
	public void addCommand(ReceiveCommand cmd) {
		commands.add(cmd);
	}

	/**
	 * @param rawLine raw command line from the wire protocol.
	 * @since 4.0
	 * @deprecated use {@link #addCommand(ReceiveCommand)}.
	 */
	public void addCommand(String rawLine) {
		commands.add(BaseReceivePack.parseCommand(rawLine));
	}
}
