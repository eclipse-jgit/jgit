/*
 * Copyright (C) 2018, Salesforce.
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
package org.eclipse.jgit.lib.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Security;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.GpgSignature;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.transport.CredentialItem.CharArrayType;

/**
 * GPG Signer using Bouncycastle library
 *
 * @since 5.2
 */
public class BouncyCastleGpgSigner extends GpgSigner {

	private static void registerBouncyCastleProviderIfNecessary() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
			Security.addProvider(new BouncyCastleProvider());
	}

	/**
	 * Creates a new instance.
	 * <p>
	 * The BounceCastleProvider will be registered if necessary.
	 * </p>
	 */
	public BouncyCastleGpgSigner() {
		registerBouncyCastleProviderIfNecessary();
	}

	@Override
	public void sign(CommitBuilder commit, String gpgSigningKey) {
		BouncyCastleGpgKeyHelper keyHelper = //
				new BouncyCastleGpgKeyHelper(gpgSigningKey);

		try {
			PGPSecretKey secretKey = keyHelper.findSecretKey();
			CharArrayType passphrase = keyHelper.getCachedPassphrase();

			PGPPrivateKey privateKey = secretKey
					.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
							.setProvider(BouncyCastleProvider.PROVIDER_NAME)
							.build(passphrase.getValue()));
			PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
					new JcaPGPContentSignerBuilder(
							secretKey.getPublicKey().getAlgorithm(),
							HashAlgorithmTags.SHA256).setProvider(
									BouncyCastleProvider.PROVIDER_NAME));
			signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (ArmoredOutputStream aOut = new ArmoredOutputStream(buffer);
					BCPGOutputStream bOut = new BCPGOutputStream(aOut)) {
				signatureGenerator.update(commit.build());
				signatureGenerator.generate().encode(bOut);
			}

			commit.setGpgSignature(new GpgSignature(buffer.toByteArray()));

		} catch (PGPException | IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			if (keyHelper.getCachedPassphrase() != null)
				keyHelper.getCachedPassphrase().clear();
		}
	}

	/**
	 * Verify the signed data by using public key and original content
	 *
	 * @param signedData
	 * @param publicKey
	 * @param originalData
	 * @return isSigned
	 */
	public boolean verifySignature(byte[] signedData, PGPPublicKey publicKey,
			byte[] originalData) {
		try {
			JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(PGPUtil
					.getDecoderStream(new ByteArrayInputStream(signedData)));
			PGPSignature signature = ((PGPSignatureList) pgpFact.nextObject())
					.get(0);
			signature.init(new JcaPGPContentVerifierBuilderProvider()
					.setProvider(BouncyCastleProvider.PROVIDER_NAME),
					publicKey);

			signature.update(originalData, 0, originalData.length);
			return signature.verify();
		} catch (PGPException | IOException e) {
			return false;
		}
	}
}
