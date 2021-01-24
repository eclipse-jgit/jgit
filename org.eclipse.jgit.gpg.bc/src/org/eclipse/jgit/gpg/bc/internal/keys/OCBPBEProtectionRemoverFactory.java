/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal.keys;

import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBEProtectionRemoverFactory;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.util.Arrays;
import org.eclipse.jgit.gpg.bc.internal.BCText;

/**
 * A {@link PBEProtectionRemoverFactory} using AES/OCB/NoPadding for decryption.
 * It accepts an AAD in the factory's constructor, so the factory can be used to
 * create a {@link PBESecretKeyDecryptor} only for a particular input.
 * <p>
 * For JGit's needs, this is sufficient, but for a general upstream
 * implementation that limitation might not be acceptable.
 * </p>
 */
class OCBPBEProtectionRemoverFactory
		implements PBEProtectionRemoverFactory {

	private final PGPDigestCalculatorProvider calculatorProvider;

	private final char[] passphrase;

	private final byte[] aad;

	/**
	 * Creates a new factory instance with the given parameters.
	 * <p>
	 * Because the AAD is given at factory level, the {@link PBESecretKeyDecryptor}s
	 * created by the factory can be used to decrypt only a particular input
	 * matching this AAD.
	 * </p>
	 *
	 * @param passphrase         to use for secret key derivation
	 * @param calculatorProvider for computing digests
	 * @param aad                for the OCB decryption
	 */
	OCBPBEProtectionRemoverFactory(char[] passphrase,
			PGPDigestCalculatorProvider calculatorProvider, byte[] aad) {
		this.calculatorProvider = calculatorProvider;
		this.passphrase = passphrase;
		this.aad = aad;
	}

	@Override
	public PBESecretKeyDecryptor createDecryptor(String protection)
			throws PGPException {
		return new PBESecretKeyDecryptor(passphrase, calculatorProvider) {

			@Override
			public byte[] recoverKeyData(int encAlgorithm, byte[] key,
					byte[] iv, byte[] encrypted, int encryptedOffset,
					int encryptedLength) throws PGPException {
				String algorithmName = PGPUtil
						.getSymmetricCipherName(encAlgorithm);
				byte[] decrypted = null;
				try {
					Cipher c = Cipher
							.getInstance(algorithmName + "/OCB/NoPadding"); //$NON-NLS-1$
					SecretKey secretKey = new SecretKeySpec(key, algorithmName);
					c.init(Cipher.DECRYPT_MODE, secretKey,
							new IvParameterSpec(iv));
					c.updateAAD(aad);
					decrypted = new byte[c.getOutputSize(encryptedLength)];
					int decryptedLength = c.update(encrypted, encryptedOffset,
							encryptedLength, decrypted);
					// doFinal() for OCB will check the MAC and throw an
					// exception if it doesn't match
					decryptedLength += c.doFinal(decrypted, decryptedLength);
					if (decryptedLength != decrypted.length) {
						throw new PGPException(MessageFormat.format(
								BCText.get().cryptWrongDecryptedLength,
								Integer.valueOf(decryptedLength),
								Integer.valueOf(decrypted.length)));
					}
					byte[] result = decrypted;
					decrypted = null; // Don't clear in finally
					return result;
				} catch (NoClassDefFoundError e) {
					String msg = MessageFormat.format(
							BCText.get().gpgNoSuchAlgorithm,
							algorithmName + "/OCB"); //$NON-NLS-1$
					throw new PGPException(msg,
							new NoSuchAlgorithmException(msg, e));
				} catch (PGPException e) {
					throw e;
				} catch (Exception e) {
					throw new PGPException(
							MessageFormat.format(BCText.get().cryptCipherError,
									e.getLocalizedMessage()),
							e);
				} finally {
					if (decrypted != null) {
						// Prevent halfway decrypted data leaking.
						Arrays.fill(decrypted, (byte) 0);
					}
				}
			}
		};
	}
}