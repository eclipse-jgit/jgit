/*
 * Copyright (C) 2021, 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.MessageFormat;

import org.bouncycastle.bcpg.ECPublicBCPGKey;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.gpg.PGPSecretKeyParser;
import org.bouncycastle.gpg.SExprParser;
import org.bouncycastle.openpgp.OpenedPGPKeyData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.operator.PBEProtectionRemoverFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEProtectionRemoverFactory;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.gpg.bc.internal.BCText;

/**
 * Utilities for reading GPG secret keys from a gpg-agent key file.
 */
public final class SecretKeys {

	// Maximum nesting depth of sub-lists in an S-Expression for a secret key.
	private static final int MAX_SEXPR_NESTING = 20;

	private SecretKeys() {
		// No instantiation.
	}

	/**
	 * Something that can supply a passphrase to decrypt an encrypted secret
	 * key.
	 */
	public interface PassphraseSupplier {

		/**
		 * Supplies a passphrase.
		 *
		 * @return the passphrase
		 * @throws PGPException
		 *             if no passphrase can be obtained
		 * @throws CanceledException
		 *             if the user canceled passphrase entry
		 * @throws UnsupportedCredentialItem
		 *             if an internal error occurred
		 * @throws URISyntaxException
		 *             if an internal error occurred
		 */
		char[] getPassphrase() throws PGPException, CanceledException,
				UnsupportedCredentialItem, URISyntaxException;
	}

	/**
	 * Reads a GPG secret key from the given stream.
	 *
	 * @param in
	 *            {@link InputStream} to read from, doesn't need to be buffered
	 * @param calculatorProvider
	 *            for checking digests
	 * @param passphraseSupplier
	 *            for decrypting encrypted keys
	 * @param publicKey
	 *            the secret key should be for
	 * @return the secret key
	 * @throws IOException
	 *             if the stream cannot be parsed
	 * @throws PGPException
	 *             if thrown by the underlying S-Expression parser, for instance
	 *             when the passphrase is wrong
	 * @throws CanceledException
	 *             if thrown by the {@code passphraseSupplier}
	 * @throws UnsupportedCredentialItem
	 *             if thrown by the {@code passphraseSupplier}
	 * @throws URISyntaxException
	 *             if thrown by the {@code passphraseSupplier}
	 */
	public static PGPSecretKey readSecretKey(InputStream in,
			PGPDigestCalculatorProvider calculatorProvider,
			PassphraseSupplier passphraseSupplier, PGPPublicKey publicKey)
			throws IOException, PGPException, CanceledException,
			UnsupportedCredentialItem, URISyntaxException {
		OpenedPGPKeyData data;
		try (InputStream keyIn = new BufferedInputStream(in)) {
			data = PGPSecretKeyParser.parse(keyIn, MAX_SEXPR_NESTING);
		}
		PBEProtectionRemoverFactory decryptor = null;
		if (isProtected(data)) {
			decryptor = new JcePBEProtectionRemoverFactory(
					passphraseSupplier.getPassphrase(), calculatorProvider);
		}
		switch (publicKey.getAlgorithm()) {
		case PublicKeyAlgorithmTags.EDDSA_LEGACY:
		case PublicKeyAlgorithmTags.Ed25519:
			// If we let Bouncy Castle check whether the secret key matches the
			// given public key it may get into trouble in some cases with
			// ed25519 keys. It appears that we may end up with secret keys
			// using the official RFC 8410 OID for ed25519, "1.3.101.112", while
			// the public key passed in may have a non-standard OpenPGP-specific
			// OID "1.3.6.1.4.1.11591.15.1", or vice versa. Bouncy Castle then
			// throws an exception because of the different OIDs.
			//
			// The work-around is to just read the secret key, and double-check
			// later that the OIDs are compatible and the curve points match.
			PGPSecretKey secret = data.getKeyData(null, calculatorProvider,
					decryptor, new JcaKeyFingerprintCalculator(),
					MAX_SEXPR_NESTING);
			PGPPublicKey pubKeyRead = secret.getPublicKey();
			int algoRead = pubKeyRead.getAlgorithm();
			if (algoRead != PublicKeyAlgorithmTags.EDDSA_LEGACY
					&& algoRead != PublicKeyAlgorithmTags.Ed25519) {
				throw new PGPException(BCText.get().keyAlgorithmMismatch);
			}
			ECPublicBCPGKey ec1 = (ECPublicBCPGKey) publicKey
					.getPublicKeyPacket().getKey();
			ECPublicBCPGKey ec2 = (ECPublicBCPGKey) pubKeyRead
					.getPublicKeyPacket().getKey();
			if (!ObjectIds.match(ec1.getCurveOID(), ec2.getCurveOID())
					|| !ec1.getEncodedPoint().equals(ec2.getEncodedPoint())) {
				throw new PGPException(
						MessageFormat.format(BCText.get().keyMismatch,
								ec1.getCurveOID(), ec1.getEncodedPoint(),
								ec2.getCurveOID(), ec2.getEncodedPoint()));
			}
			return secret;
		default:
			// For other key types let Bouncy Castle do the check.
			return data.getKeyData(publicKey, calculatorProvider, decryptor,
					null, MAX_SEXPR_NESTING);
		}
	}

	private static boolean isProtected(OpenedPGPKeyData data) {
		return SExprParser.ProtectionFormatTypeTags.PROTECTED_PRIVATE_KEY == SExprParser
				.getProtectionType(data.getKeyExpression().getString(0));
	}

}
