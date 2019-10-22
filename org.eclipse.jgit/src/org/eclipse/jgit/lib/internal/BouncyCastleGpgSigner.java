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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.GpgSignature;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * GPG Signer using BouncyCastle library
 */
public class BouncyCastleGpgSigner extends GpgSigner {

	private static void registerBouncyCastleProviderIfNecessary() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	/**
	 * Create a new instance.
	 * <p>
	 * The BounceCastleProvider will be registered if necessary.
	 * </p>
	 */
	public BouncyCastleGpgSigner() {
		registerBouncyCastleProviderIfNecessary();
	}

	@Override
	public boolean canLocateSigningKey(@Nullable String gpgSigningKey,
			PersonIdent committer, CredentialsProvider credentialsProvider)
			throws CanceledException {
		try (BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt = new BouncyCastleGpgKeyPassphrasePrompt(
				credentialsProvider)) {
			BouncyCastleGpgKey gpgKey = locateSigningKey(gpgSigningKey,
					committer, passphrasePrompt);
			return gpgKey != null;
		} catch (PGPException | IOException | NoSuchAlgorithmException
				| NoSuchProviderException | URISyntaxException e) {
			return false;
		}
	}

	private BouncyCastleGpgKey locateSigningKey(@Nullable String gpgSigningKey,
			PersonIdent committer,
			BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt)
			throws CanceledException, UnsupportedCredentialItem, IOException,
			NoSuchAlgorithmException, NoSuchProviderException, PGPException,
			URISyntaxException {
		if (gpgSigningKey == null || gpgSigningKey.isEmpty()) {
			gpgSigningKey = '<' + committer.getEmailAddress() + '>';
		}

		BouncyCastleGpgKeyLocator keyHelper = new BouncyCastleGpgKeyLocator(
				gpgSigningKey, passphrasePrompt);

		return keyHelper.findSecretKey();
	}

	@Override
	public void sign(@NonNull CommitBuilder commit,
			@Nullable String gpgSigningKey, @NonNull PersonIdent committer,
			CredentialsProvider credentialsProvider) throws CanceledException {
		try (BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt = new BouncyCastleGpgKeyPassphrasePrompt(
				credentialsProvider)) {
			BouncyCastleGpgKey gpgKey = locateSigningKey(gpgSigningKey,
					committer, passphrasePrompt);
			PGPSecretKey secretKey = gpgKey.getSecretKey();
			if (secretKey == null) {
				throw new JGitInternalException(
						JGitText.get().unableToSignCommitNoSecretKey);
			}
			char[] passphrase = passphrasePrompt.getPassphrase(
					secretKey.getPublicKey().getFingerprint(),
					gpgKey.getOrigin());
			PGPPrivateKey privateKey = secretKey
					.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
							.setProvider(BouncyCastleProvider.PROVIDER_NAME)
							.build(passphrase));
			PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
					new JcaPGPContentSignerBuilder(
							secretKey.getPublicKey().getAlgorithm(),
							HashAlgorithmTags.SHA256).setProvider(
									BouncyCastleProvider.PROVIDER_NAME));
			signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (BCPGOutputStream out = new BCPGOutputStream(
					new ArmoredOutputStream(buffer))) {
				signatureGenerator.update(commit.build());
				signatureGenerator.generate().encode(out);
			}
			commit.setGpgSignature(new GpgSignature(buffer.toByteArray()));
		} catch (PGPException | IOException | NoSuchAlgorithmException
				| NoSuchProviderException | URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
