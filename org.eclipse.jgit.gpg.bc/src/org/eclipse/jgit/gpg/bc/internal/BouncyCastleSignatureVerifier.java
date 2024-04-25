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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.bcpg.sig.IssuerFingerprint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.AbstractGpgSignatureVerifier;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignatureVerifier;
import org.eclipse.jgit.util.LRUMap;
import org.eclipse.jgit.util.StringUtils;

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
		return SignatureParserProvider.get(signatureData).verify(config, data, signatureData);
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
