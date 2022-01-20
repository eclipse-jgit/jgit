/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.internal;

import java.io.IOException;

import org.eclipse.jgit.lib.GpgSignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.SignatureUtils;
import org.eclipse.jgit.util.stream.ThrowingPrintWriter;

/**
 * Utilities for signature verification.
 */
public final class VerificationUtils {

	private VerificationUtils() {
		// No instantiation
	}

	/**
	 * Writes information about a signature verification to the given writer.
	 *
	 * @param out
	 *            to write to
	 * @param verification
	 *            to show
	 * @param name
	 *            of the verifier used
	 * @param creator
	 *            of the object verified; used for time zone information
	 * @throws IOException
	 *             if writing fails
	 */
	public static void writeVerification(ThrowingPrintWriter out,
			SignatureVerification verification, String name,
			PersonIdent creator) throws IOException {
		String[] text = SignatureUtils
				.toString(verification, creator,
						new GitDateFormatter(GitDateFormatter.Format.LOCALE))
				.split("\n"); //$NON-NLS-1$
		for (String line : text) {
			out.print(name);
			out.print(": "); //$NON-NLS-1$
			out.println(line);
		}
	}
}
