/*
 * Copyright (C) 2018, Salesforce. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

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
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
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
						BCText.get().unableToSignCommitNoSecretKey);
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
			PGPSignatureSubpacketGenerator subpacketGenerator = new PGPSignatureSubpacketGenerator();
			subpacketGenerator.setIssuerFingerprint(false,
					secretKey.getPublicKey());
			signatureGenerator
					.setHashedSubpackets(subpacketGenerator.generate());
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
