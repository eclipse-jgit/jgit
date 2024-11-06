/*
 * Copyright (C) 2018, 2024, Salesforce and others
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
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignature;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.StringUtils;

/**
 * GPG Signer using the BouncyCastle library.
 */
public class BouncyCastleGpgSigner implements Signer {

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
	public GpgSignature sign(Repository repository, GpgConfig config,
			byte[] data, PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider) throws CanceledException,
			IOException, UnsupportedSigningFormatException {
		String gpgSigningKey = signingKey;
		if (gpgSigningKey == null) {
			gpgSigningKey = config.getSigningKey();
		}
		try (BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt = new BouncyCastleGpgKeyPassphrasePrompt(
				credentialsProvider)) {
			BouncyCastleGpgKey gpgKey = locateSigningKey(gpgSigningKey,
					committer, passphrasePrompt);
			PGPSecretKey secretKey = gpgKey.getSecretKey();
			if (secretKey == null) {
				throw new JGitInternalException(
						BCText.get().unableToSignCommitNoSecretKey);
			}
			JcePBESecretKeyDecryptorBuilder decryptorBuilder = new JcePBESecretKeyDecryptorBuilder();
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
							HashAlgorithmTags.SHA256),
					publicKey);
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
				subpackets.addSignerUserID(false, userId);
			}
			signatureGenerator
					.setHashedSubpackets(subpackets.generate());
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (BCPGOutputStream out = new BCPGOutputStream(
					new ArmoredOutputStream(buffer))) {
				signatureGenerator.update(data);
				signatureGenerator.generate().encode(out);
			}
			return new GpgSignature(buffer.toByteArray());
		} catch (PGPException | NoSuchAlgorithmException
				| NoSuchProviderException | URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	@Override
	public boolean canLocateSigningKey(Repository repository, GpgConfig config,
			PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider) throws CanceledException {
		String gpgSigningKey = signingKey;
		if (gpgSigningKey == null) {
			gpgSigningKey = config.getSigningKey();
		}
		try (BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt = new BouncyCastleGpgKeyPassphrasePrompt(
				credentialsProvider)) {
			BouncyCastleGpgKey gpgKey = locateSigningKey(gpgSigningKey,
					committer, passphrasePrompt);
			return gpgKey != null;
		} catch (CanceledException e) {
			throw e;
		} catch (Exception e) {
			return false;
		}
	}

	static String extractSignerId(String pgpUserId) {
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
