/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * A {@code VerificationResult} describes the outcome of a signature
 * verification.
 *
 * @see VerifySignatureCommand
 *
 * @since 5.11
 */
public interface VerificationResult {

	/**
	 * If an error occurred during signature verification, this retrieves the
	 * exception.
	 *
	 * @return the exception, or {@code null} if none occurred
	 */
	Throwable getException();

	/**
	 * Retrieves the signature verification result.
	 *
	 * @return the result, or {@code null} if none was computed
	 */
	GpgSignatureVerifier.SignatureVerification getVerification();

	/**
	 * Retrieves the git object of which the signature was verified.
	 *
	 * @return the git object
	 */
	RevObject getObject();
}
