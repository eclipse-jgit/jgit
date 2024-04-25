/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import java.io.IOException;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.AbstractGpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;

/**
 * A {@link GpgSignatureVerifier} to verify GPG signatures using BouncyCastle.
 */
public class BouncyCastleSignatureVerifier
	extends AbstractGpgSignatureVerifier {

	private static void registerBouncyCastleProviderIfNecessary() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	/**
	 * Creates a new instance and registers the BouncyCastle security provider
	 * if needed.
	 */
	public BouncyCastleSignatureVerifier() {
		registerBouncyCastleProviderIfNecessary();
	}

	@Override
	public String getName() {
		return "bc"; //$NON-NLS-1$
	}

	@Override
	public SignatureVerification verify(@NonNull GpgConfig config, byte[] data,
					    byte[] signatureData)
		throws IOException {
		SignatureParser signatureParser = SignatureParserProvider.get(signatureData);
		if(signatureParser != null) {
			return signatureParser.verify(config, data, signatureData);
		}
		throw new JGitInternalException(BCText.get().nonSignatureError);
	}

	@Override
	public SignatureVerification verify(byte[] data, byte[] signatureData)
		throws IOException {
		throw new UnsupportedOperationException(
			"Call verify(GpgConfig, byte[], byte[]) instead."); //$NON-NLS-1$
	}

	@Override
	public void clear() {
		SignatureParserProvider.clear();
	}
}
