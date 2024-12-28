/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.text.MessageFormat;
import java.util.Locale;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.SignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.SignatureVerifier.TrustLevel;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Utilities for signature verification.
 *
 * @since 5.11
 */
public final class SignatureUtils {

	private SignatureUtils() {
		// No instantiation
	}

	/**
	 * Writes information about a signature verification to a string.
	 *
	 * @param verification
	 *            to show
	 * @param creator
	 *            of the object verified; used for time zone information
	 * @param formatter
	 *            to use for dates
	 * @return a textual representation of the {@link SignatureVerification},
	 *         using LF as line separator
	 *
	 * @since 7.0
	 */
	public static String toString(SignatureVerification verification,
			PersonIdent creator, GitDateFormatter formatter) {
		StringBuilder result = new StringBuilder();
		if (verification.creationDate() != null) {
			// Use the creator's timezone for the signature date
			PersonIdent dateId = new PersonIdent(creator,
					verification.creationDate().toInstant());
			result.append(
					MessageFormat.format(JGitText.get().verifySignatureMade,
							formatter.formatDate(dateId)));
			result.append('\n');
		}
		result.append(MessageFormat.format(
				JGitText.get().verifySignatureKey,
				verification.keyFingerprint().toUpperCase(Locale.ROOT)));
		result.append('\n');
		if (!StringUtils.isEmptyOrNull(verification.signer())) {
			result.append(
					MessageFormat.format(JGitText.get().verifySignatureIssuer,
							verification.signer()));
			result.append('\n');
		}
		String msg;
		if (verification.verified()) {
			if (verification.expired()) {
				msg = JGitText.get().verifySignatureExpired;
			} else {
				msg = JGitText.get().verifySignatureGood;
			}
		} else {
			msg = JGitText.get().verifySignatureBad;
		}
		result.append(MessageFormat.format(msg, verification.keyUser()));
		if (!TrustLevel.UNKNOWN.equals(verification.trustLevel())) {
			result.append(' ' + MessageFormat
					.format(JGitText.get().verifySignatureTrust, verification
							.trustLevel().name().toLowerCase(Locale.ROOT)));
		}
		result.append('\n');
		msg = verification.message();
		if (!StringUtils.isEmptyOrNull(msg)) {
			result.append(msg);
			result.append('\n');
		}
		return result.toString();
	}
}
