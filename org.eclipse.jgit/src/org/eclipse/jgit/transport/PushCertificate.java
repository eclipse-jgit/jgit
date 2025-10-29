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
	private final List<String> pushOptions;
	private final String signature;

	PushCertificate(String version, PushCertificateIdent pusher, String pushee,
			String nonce, NonceStatus nonceStatus, List<ReceiveCommand> commands,
			List<String> pushOptions, String signature) {
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
		this.pushOptions = pushOptions;
		this.signature = signature;
	}

	/**
	 * Get the certificate version string.
	 *
	 * @return the certificate version string.
	 * @since 4.1
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Get the raw line that signed the cert, as a string.
	 *
	 * @return the raw line that signed the cert, as a string.
	 * @since 4.0
	 */
	public String getPusher() {
		return pusher.getRaw();
	}

	/**
	 * Get identity of the pusher who signed the cert.
	 *
	 * @return identity of the pusher who signed the cert.
	 * @since 4.1
	 */
	public PushCertificateIdent getPusherIdent() {
		return pusher;
	}

	/**
	 * Get URL of the repository the push was originally sent to.
	 *
	 * @return URL of the repository the push was originally sent to.
	 * @since 4.0
	 */
	public String getPushee() {
		return pushee;
	}

	/**
	 * Get the raw nonce value that was presented by the pusher.
	 *
	 * @return the raw nonce value that was presented by the pusher.
	 * @since 4.1
	 */
	public String getNonce() {
		return nonce;
	}

	/**
	 * Get verification status of the nonce embedded in the certificate.
	 *
	 * @return verification status of the nonce embedded in the certificate.
	 * @since 4.0
	 */
	public NonceStatus getNonceStatus() {
		return nonceStatus;
	}

	/**
	 * Get the list of commands as one string to be feed into the signature
	 * verifier.
	 *
	 * @return the list of commands as one string to be feed into the signature
	 *         verifier.
	 * @since 4.1
	 */
	public List<ReceiveCommand> getCommands() {
		return commands;
	}

	/**
	 * Get the list of push options that were included in the push certificate.
	 *
	 * @return the push options, or an empty list if none.
	 * @since 7.5
	 */
	public List<String> getPushOptions() {
		return pushOptions;
	}

	/**
	 * Get the raw signature
	 *
	 * @return the raw signature, consisting of the lines received between the
	 *         lines {@code "----BEGIN GPG SIGNATURE-----\n"} and
	 *         {@code "----END GPG SIGNATURE-----\n}", inclusive.
	 * @since 4.0
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * Get text payload of the certificate for the signature verifier.
	 *
	 * @return text payload of the certificate for the signature verifier.
	 * @since 4.1
	 */
	public String toText() {
		return toStringBuilder().toString();
	}

	/**
	 * Get original text payload plus signature
	 *
	 * @return original text payload plus signature; the final output will be
	 *         valid as input to
	 *         {@link org.eclipse.jgit.transport.PushCertificateParser#fromString(String)}.
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
		sb.append(NONCE).append(' ').append(nonce).append('\n');
		for (String option : pushOptions) {
			sb.append(PushCertificateParser.PUSH_OPTION).append(' ')
				.append(option).append('\n');
		}
		sb.append('\n');
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
