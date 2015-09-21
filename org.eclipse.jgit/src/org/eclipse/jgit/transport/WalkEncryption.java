/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.text.MessageFormat;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.eclipse.jgit.internal.JGitText;

abstract class WalkEncryption {
	static final WalkEncryption NONE = new NoEncryption();

	static final String JETS3T_CRYPTO_VER = "jets3t-crypto-ver"; //$NON-NLS-1$

	static final String JETS3T_CRYPTO_ALG = "jets3t-crypto-alg"; //$NON-NLS-1$

	abstract OutputStream encrypt(OutputStream os) throws IOException;

	abstract InputStream decrypt(InputStream in) throws IOException;

	abstract void request(HttpURLConnection u, String prefix);

	abstract void validate(HttpURLConnection u, String prefix) throws IOException;

	// TODO mixed ciphers
	// consider permitting mixed ciphers to facilitate algorithm migration
	// i.e. user keeps the password, but changes the algorithm
	// then existing remote entries will still be readable
	protected void validateImpl(final HttpURLConnection u, final String prefix,
			final String version, final String name) throws IOException {
		String v;

		v = u.getHeaderField(prefix + JETS3T_CRYPTO_VER);
		if (v == null)
			v = ""; //$NON-NLS-1$
		if (!version.equals(v))
			throw new IOException(MessageFormat.format(JGitText.get().unsupportedEncryptionVersion, v));

		v = u.getHeaderField(prefix + JETS3T_CRYPTO_ALG);
		if (v == null)
			v = ""; //$NON-NLS-1$
		// Standard names are not case-sensitive.
		// http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
		if (!name.equalsIgnoreCase(v))
			throw new IOException(MessageFormat.format(JGitText.get().unsupportedEncryptionAlgorithm, v));
	}

	IOException error(final Throwable why) {
		final IOException e;
		e = new IOException(MessageFormat.format(JGitText.get().encryptionError, why.getMessage()));
		e.initCause(why);
		return e;
	}

	private static class NoEncryption extends WalkEncryption {
		@Override
		void request(HttpURLConnection u, String prefix) {
			// Don't store any request properties.
		}

		@Override
		void validate(final HttpURLConnection u, final String prefix)
				throws IOException {
			validateImpl(u, prefix, "", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		InputStream decrypt(InputStream in) {
			return in;
		}

		@Override
		OutputStream encrypt(OutputStream os) {
			return os;
		}
	}

	// PBEParameterSpec factory for Java (version <= 7).
	// Does not support AlgorithmParameterSpec.
	static PBEParameterSpec java7PBEParameterSpec(byte[] salt,
			int iterationCount) {
		return new PBEParameterSpec(salt, iterationCount);
	}

	// PBEParameterSpec factory for Java (version >= 8).
	// Adds support for AlgorithmParameterSpec.
	static PBEParameterSpec java8PBEParameterSpec(byte[] salt,
			int iterationCount, AlgorithmParameterSpec paramSpec) {
		try {
			@SuppressWarnings("boxing")
			PBEParameterSpec instance = PBEParameterSpec.class
					.getConstructor(byte[].class, int.class,
							AlgorithmParameterSpec.class)
					.newInstance(salt, iterationCount, paramSpec);
			return instance;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// Current runtime version.
	// https://docs.oracle.com/javase/7/docs/technotes/guides/versioning/spec/versioning2.html
	static double javaVersion() {
		return Double.parseDouble(System.getProperty("java.specification.version")); //$NON-NLS-1$
	}

	/**
	 * JetS3t compatibility reference: <a href=
	 * "https://bitbucket.org/jmurty/jets3t/src/156c00eb160598c2e9937fd6873f00d3190e28ca/src/org/jets3t/service/security/EncryptionUtil.java">
	 * EncryptionUtil.java</a>
	 * <p>
	 * Note: EncryptionUtil is inadequate:
	 * <li>EncryptionUtil.isCipherAvailableForUse checks encryption only which
	 * "always works", but in JetS3t both encryption and decryption use non-IV
	 * aware algorithm parameters for all PBE specs, which breaks in case of AES
	 * <li>that means that only non-IV algorithms will work round trip in
	 * JetS3t, such as PBEWithMD5AndDES and PBEWithSHAAndTwofish-CBC
	 * <li>any AES based algorithms such as "PBE...With...And...AES" will not
	 * work, since they need proper IV setup
	 */
	static class ObjectEncryptionJetS3tV2 extends WalkEncryption {

		static final String JETS3T_VERSION = "2"; //$NON-NLS-1$

		static final String JETS3T_ALGORITHM = "PBEWithMD5AndDES"; //$NON-NLS-1$

		static final int JETS3T_ITERATIONS = 5000;

		static final int JETS3T_KEY_SIZE = 32;

		static final byte[] JETS3T_SALT = { //
				(byte) 0xA4, (byte) 0x0B, (byte) 0xC8, (byte) 0x34, //
				(byte) 0xD6, (byte) 0x95, (byte) 0xF3, (byte) 0x13 //
		};

		// Size 16, see com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE
		static final byte[] ZERO_AES_IV = new byte[16];

		private final String cryptoVer = JETS3T_VERSION;

		private final String cryptoAlg;

		private final SecretKey secretKey;

		private final AlgorithmParameterSpec paramSpec;

		ObjectEncryptionJetS3tV2(final String algo, final String key)
				throws GeneralSecurityException {
			cryptoAlg = algo;

			// Standard names are not case-sensitive.
			// http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
			String cryptoName = cryptoAlg.toUpperCase();

			if (!cryptoName.startsWith("PBE")) //$NON-NLS-1$
				throw new GeneralSecurityException(JGitText.get().encryptionOnlyPBE);

			PBEKeySpec keySpec = new PBEKeySpec(key.toCharArray(), JETS3T_SALT, JETS3T_ITERATIONS, JETS3T_KEY_SIZE);
			secretKey = SecretKeyFactory.getInstance(algo).generateSecret(keySpec);

			// Detect algorithms which require initialization vector.
			boolean useIV = cryptoName.contains("AES"); //$NON-NLS-1$

			// PBEParameterSpec algorithm parameters are supported from Java 8.
			boolean isJava8 = javaVersion() >= 1.8;

			if (useIV && isJava8) {
				// Support IV where possible:
				// * since JCE provider uses random IV for PBE/AES
				// * and there is no place to store dynamic IV in JetS3t V2
				// * we use static IV, and tolerate increased security risk
				// TODO back port this change to JetS3t V2
				// See:
				// https://bitbucket.org/jmurty/jets3t/raw/156c00eb160598c2e9937fd6873f00d3190e28ca/src/org/jets3t/service/security/EncryptionUtil.java
				// http://cr.openjdk.java.net/~mullan/webrevs/ascarpin/webrev.00/raw_files/new/src/share/classes/com/sun/crypto/provider/PBES2Core.java
				IvParameterSpec paramIV = new IvParameterSpec(ZERO_AES_IV);
				paramSpec = java8PBEParameterSpec(JETS3T_SALT, JETS3T_ITERATIONS, paramIV);
			} else {
				// Strict legacy JetS3t V2 compatibility, with no IV support.
				paramSpec = java7PBEParameterSpec(JETS3T_SALT, JETS3T_ITERATIONS);
			}

		}

		@Override
		void request(final HttpURLConnection u, final String prefix) {
			u.setRequestProperty(prefix + JETS3T_CRYPTO_VER, cryptoVer);
			u.setRequestProperty(prefix + JETS3T_CRYPTO_ALG, cryptoAlg);
		}

		@Override
		void validate(final HttpURLConnection u, final String prefix)
				throws IOException {
			validateImpl(u, prefix, cryptoVer, cryptoAlg);
		}

		@Override
		OutputStream encrypt(final OutputStream os) throws IOException {
			try {
				final Cipher cipher = Cipher.getInstance(cryptoAlg);
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
				return new CipherOutputStream(os, cipher);
			} catch (GeneralSecurityException e) {
				throw error(e);
			}
		}

		@Override
		InputStream decrypt(final InputStream in) throws IOException {
			try {
				final Cipher cipher = Cipher.getInstance(cryptoAlg);
				cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec);
				return new CipherInputStream(in, cipher);
			} catch (GeneralSecurityException e) {
				throw error(e);
			}
		}
	}
}
