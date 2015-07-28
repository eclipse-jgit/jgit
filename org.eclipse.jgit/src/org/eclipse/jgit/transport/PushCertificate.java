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

import static org.eclipse.jgit.transport.PushCertificateParser.NONCE;
import static org.eclipse.jgit.transport.PushCertificateParser.PUSHEE;
import static org.eclipse.jgit.transport.PushCertificateParser.PUSHER;
import static org.eclipse.jgit.transport.PushCertificateParser.VERSION;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.internal.JGitText;

/**
 * The required information to verify the push.
 * <p>
 * A valid certificate will not return null from any getter methods; callers may
 * assume that any null value indicates a missing or invalid certificate.
 *
 * @since 4.0
 */
public class PushCertificate {
	/** Verification result of the nonce returned during push. */
	public enum NonceStatus {
		/** Nonce was not expected, yet client sent one anyway. */
		UNSOLICITED,
		/** Nonce is invalid and did not match server's expectations. */
		BAD,
		/** Nonce is required, but was not sent by client. */
		MISSING,
		/**
		 * Received nonce matches sent nonce, or is valid within the accepted slop
		 * window.
		 */
		OK,
		/** Received nonce is valid, but outside the accepted slop window. */
		SLOP
	}

	private final String version;
	private final PushCertificateIdent pusher;
	private final String pushee;
	private final String nonce;
	private final NonceStatus nonceStatus;
	private final List<ReceiveCommand> commands;
	private final String signature;

	PushCertificate(String version, PushCertificateIdent pusher, String pushee,
			String nonce, NonceStatus nonceStatus, List<ReceiveCommand> commands,
			String signature) {
		if (version == null || version.isEmpty()) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField, VERSION));
		}
		if (pusher == null) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField, PUSHER));
		}
		if (nonce == null || nonce.isEmpty()) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField, NONCE));
		}
		if (nonceStatus == null) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField,
					"nonce status")); //$NON-NLS-1$
		}
		if (commands == null || commands.isEmpty()) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().pushCertificateInvalidField,
					"command")); //$NON-NLS-1$
		}
		if (signature == null || signature.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().pushCertificateInvalidSignature);
		}
		if (!signature.startsWith(PushCertificateParser.BEGIN_SIGNATURE)
				|| !signature.endsWith(PushCertificateParser.END_SIGNATURE + '\n')) {
			throw new IllegalArgumentException(
					JGitText.get().pushCertificateInvalidSignature);
		}
		this.version = version;
		this.pusher = pusher;
		this.pushee = pushee;
		this.nonce = nonce;
		this.nonceStatus = nonceStatus;
		this.commands = commands;
		this.signature = signature;
	}

	/**
	 * @return the certificate version string.
	 * @since 4.1
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @return the raw line that signed the cert, as a string.
	 * @since 4.0
	 */
	public String getPusher() {
		return pusher.getRaw();
	}

	/**
	 * @return identity of the pusher who signed the cert.
	 * @since 4.1
	 */
	public PushCertificateIdent getPusherIdent() {
		return pusher;
	}

	/**
	 * @return URL of the repository the push was originally sent to.
	 * @since 4.0
	 */
	public String getPushee() {
		return pushee;
	}

	/**
	 * @return the raw nonce value that was presented by the pusher.
	 * @since 4.1
	 */
	public String getNonce() {
		return nonce;
	}

	/**
	 * @return verification status of the nonce embedded in the certificate.
	 * @since 4.0
	 */
	public NonceStatus getNonceStatus() {
		return nonceStatus;
	}

	/**
	 * @return the list of commands as one string to be feed into the signature
	 *         verifier.
	 * @since 4.1
	 */
	public List<ReceiveCommand> getCommands() {
		return commands;
	}

	/**
	 * @return the raw signature, consisting of the lines received between the
	 *     lines {@code "----BEGIN GPG SIGNATURE-----\n"} and
	 *     {@code "----END GPG SIGNATURE-----\n}", inclusive.
	 * @since 4.0
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * @return text payload of the certificate for the signature verifier.
	 * @since 4.1
	 */
	public String toText() {
		return toStringBuilder().toString();
	}

	/**
	 * @return original text payload plus signature; the final output will be
	 *     valid as input to {@link PushCertificateParser#fromString(String)}.
	 * @since 4.1
	 */
	public String toTextWithSignature() {
		return toStringBuilder().append(signature).toString();
	}

	private StringBuilder toStringBuilder() {
		StringBuilder sb = new StringBuilder()
				.append(VERSION).append(' ').append(version).append('\n')
				.append(PUSHER).append(' ').append(getPusher())
				.append('\n');
		if (pushee != null) {
			sb.append(PUSHEE).append(' ').append(pushee).append('\n');
		}
		sb.append(NONCE).append(' ').append(nonce).append('\n')
				.append('\n');
		for (ReceiveCommand cmd : commands) {
			sb.append(cmd.getOldId().name())
				.append(' ').append(cmd.getNewId().name())
				.append(' ').append(cmd.getRefName()).append('\n');
		}
		return sb;
	}

	@Override
	public int hashCode() {
		return signature.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PushCertificate)) {
			return false;
		}
		PushCertificate p = (PushCertificate) o;
		return version.equals(p.version)
				&& pusher.equals(p.pusher)
				&& Objects.equals(pushee, p.pushee)
				&& nonceStatus == p.nonceStatus
				&& signature.equals(p.signature)
				&& commandsEqual(this, p);
	}

	private static boolean commandsEqual(PushCertificate c1, PushCertificate c2) {
		if (c1.commands.size() != c2.commands.size()) {
			return false;
		}
		for (int i = 0; i < c1.commands.size(); i++) {
			ReceiveCommand cmd1 = c1.commands.get(i);
			ReceiveCommand cmd2 = c2.commands.get(i);
			if (!cmd1.getOldId().equals(cmd2.getOldId())
					|| !cmd1.getNewId().equals(cmd2.getNewId())
					|| !cmd1.getRefName().equals(cmd2.getRefName())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + '['
				 + toTextWithSignature() + ']';
	}
}
