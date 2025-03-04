/*
 * Copyright (C) 2021, 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import org.eclipse.jgit.lib.GpgConfig.GpgFormat;
import org.eclipse.jgit.lib.SignatureVerifier;
import org.eclipse.jgit.lib.SignatureVerifierFactory;

/**
 * A {@link SignatureVerifierFactory} that creates {@link SignatureVerifier}
 * instances that verify GPG signatures using BouncyCastle and that do cache
 * public keys.
 */
public final class BouncyCastleGpgSignatureVerifierFactory
		implements SignatureVerifierFactory {

	@Override
	public GpgFormat getType() {
		return GpgFormat.OPENPGP;
	}

	@Override
	public SignatureVerifier create() {
		return new BouncyCastleGpgSignatureVerifier();
	}


}
