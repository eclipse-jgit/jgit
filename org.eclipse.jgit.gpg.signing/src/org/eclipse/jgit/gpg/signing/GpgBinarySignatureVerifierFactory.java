/*
 * Copyright (C) 2024, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.signing;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.lib.SignatureVerifierFactory;

/**
 * A {@link SignatureVerifierFactory} that creates
 * {@link GpgBinarySignatureVerifier} instances.
 */
public final class GpgBinarySignatureVerifierFactory
		implements SignatureVerifierFactory {

	@Override
	@NonNull
	public GpgFormat getType() {
		return GpgFormat.OPENPGP;
	}

	@Override
	@NonNull
	public SignatureVerifier create() {
		return new GpgBinarySignatureVerifier();
	}
}
