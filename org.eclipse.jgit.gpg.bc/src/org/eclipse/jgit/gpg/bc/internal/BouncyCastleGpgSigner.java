/*
 * Copyright (C) 2018, 2020, Salesforce and others
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
import java.util.Iterator;

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
import org.eclipse.jgit.util.StringUtils;

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
			JcePBESecretKeyDecryptorBuilder decryptorBuilder = new JcePBESecretKeyDecryptorBuilder()
					.setProvider(BouncyCastleProvider.PROVIDER_NAME);
			PGPPrivateKey privateKey = null;
			if (!passphrasePrompt.hasPassphrase()) {
				// Either the key is not encrypted, or it was read from the
				// legacy secring.gpg. Try getting the private key without
				// passphrase first.
				try {
					privateKey = secretKey.extractPrivateKey(
							decryptorBuilder.build(new char[0]));
				} catch (PGPException e) {
					// Ignore and try again with passphrase below
				}
			}
			if (privateKey == null) {
				// Try using a passphrase
				char[] passphrase = passphrasePrompt.getPassphrase(
						secretKey.getPublicKey().getFingerprint(),
						gpgKey.getOrigin());
				privateKey = secretKey
						.extractPrivateKey(decryptorBuilder.build(passphrase));
			}
			PGPPublicKey publicKey = secretKey.getPublicKey();
			PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
					new JcaPGPContentSignerBuilder(
							publicKey.getAlgorithm(),
							HashAlgorithmTags.SHA256).setProvider(
									BouncyCastleProvider.PROVIDER_NAME));
			signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
			PGPSignatureSubpacketGenerator subpackets = new PGPSignatureSubpacketGenerator();
			subpackets.setIssuerFingerprint(false, publicKey);
			// Also add the signer's user ID. Note that GPG uses only the e-mail
			// address part.
			String userId = committer.getEmailAddress();
			Iterator<String> userIds = publicKey.getUserIDs();
			if (userIds.hasNext()) {
				String keyUserId = userIds.next();
				if (!StringUtils.isEmptyOrNull(keyUserId)
						&& (userId == null || !keyUserId.contains(userId))) {
					// Not the committer's key?
					userId = extractSignerId(keyUserId);
				}
			}
			if (userId != null) {
				subpackets.setSignerUserID(false, userId);
			}
			signatureGenerator
					.setHashedSubpackets(subpackets.generate());
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

	private String extractSignerId(String pgpUserId) {
		int from = pgpUserId.indexOf('<');
		if (from >= 0) {
			int to = pgpUserId.indexOf('>', from + 1);
			if (to > from + 1) {
				return pgpUserId.substring(from + 1, to);
			}
		}
		return pgpUserId;
	}
}
