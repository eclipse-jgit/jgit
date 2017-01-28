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
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.Base64;

abstract class WalkEncryption {
	static final WalkEncryption NONE = new NoEncryption();

	static final String JETS3T_CRYPTO_VER = "jets3t-crypto-ver"; //$NON-NLS-1$

	static final String JETS3T_CRYPTO_ALG = "jets3t-crypto-alg"; //$NON-NLS-1$

	// Note: encrypt -> request state machine, step 1.
	abstract OutputStream encrypt(OutputStream output) throws IOException;

	// Note: encrypt -> request state machine, step 2.
	abstract void request(HttpURLConnection conn, String prefix) throws IOException;

	// Note: validate -> decrypt state machine, step 1.
	abstract void validate(HttpURLConnection conn, String prefix) throws IOException;

	// Note: validate -> decrypt state machine, step 2.
	abstract InputStream decrypt(InputStream input) throws IOException;


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
	static class JetS3tV2 extends WalkEncryption {

		static final String VERSION = "2"; //$NON-NLS-1$

		static final String ALGORITHM = "PBEWithMD5AndDES"; //$NON-NLS-1$

		static final int ITERATIONS = 5000;

		static final int KEY_SIZE = 32;

		static final byte[] SALT = { //
				(byte) 0xA4, (byte) 0x0B, (byte) 0xC8, (byte) 0x34, //
				(byte) 0xD6, (byte) 0x95, (byte) 0xF3, (byte) 0x13 //
		};

		// Size 16, see com.sun.crypto.provider.AESConstants.AES_BLOCK_SIZE
		static final byte[] ZERO_AES_IV = new byte[16];

		private static final String CRYPTO_VER = VERSION;

		private final String cryptoAlg;

		private final SecretKey secretKey;

		private final AlgorithmParameterSpec paramSpec;

		JetS3tV2(final String algo, final String key)
				throws GeneralSecurityException {
			cryptoAlg = algo;

			// Verify if cipher is present.
			Cipher cipher = InsecureCipherFactory.create(cryptoAlg);

			// Standard names are not case-sensitive.
			// http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
			String cryptoName = cryptoAlg.toUpperCase(Locale.ROOT);

			if (!cryptoName.startsWith("PBE")) //$NON-NLS-1$
				throw new GeneralSecurityException(JGitText.get().encryptionOnlyPBE);

			PBEKeySpec keySpec = new PBEKeySpec(key.toCharArray(), SALT, ITERATIONS, KEY_SIZE);
			secretKey = SecretKeyFactory.getInstance(algo).generateSecret(keySpec);

			// Detect algorithms which require initialization vector.
			boolean useIV = cryptoName.contains("AES"); //$NON-NLS-1$

			// PBEParameterSpec algorithm parameters are supported from Java 8.
			if (useIV) {
				// Support IV where possible:
				// * since JCE provider uses random IV for PBE/AES
				// * and there is no place to store dynamic IV in JetS3t V2
				// * we use static IV, and tolerate increased security risk
				// TODO back port this change to JetS3t V2
				// See:
				// https://bitbucket.org/jmurty/jets3t/raw/156c00eb160598c2e9937fd6873f00d3190e28ca/src/org/jets3t/service/security/EncryptionUtil.java
				// http://cr.openjdk.java.net/~mullan/webrevs/ascarpin/webrev.00/raw_files/new/src/share/classes/com/sun/crypto/provider/PBES2Core.java
				IvParameterSpec paramIV = new IvParameterSpec(ZERO_AES_IV);
				paramSpec = new PBEParameterSpec(SALT, ITERATIONS, paramIV);
			} else {
				// Strict legacy JetS3t V2 compatibility, with no IV support.
				paramSpec = new PBEParameterSpec(SALT, ITERATIONS);
			}

			// Verify if cipher + key are allowed by policy.
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
			cipher.doFinal();
		}

		@Override
		void request(final HttpURLConnection u, final String prefix) {
			u.setRequestProperty(prefix + JETS3T_CRYPTO_VER, CRYPTO_VER);
			u.setRequestProperty(prefix + JETS3T_CRYPTO_ALG, cryptoAlg);
		}

		@Override
		void validate(final HttpURLConnection u, final String prefix)
				throws IOException {
			validateImpl(u, prefix, CRYPTO_VER, cryptoAlg);
		}

		@Override
		OutputStream encrypt(final OutputStream os) throws IOException {
			try {
				final Cipher cipher = InsecureCipherFactory.create(cryptoAlg);
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
				return new CipherOutputStream(os, cipher);
			} catch (GeneralSecurityException e) {
				throw error(e);
			}
		}

		@Override
		InputStream decrypt(final InputStream in) throws IOException {
			try {
				final Cipher cipher = InsecureCipherFactory.create(cryptoAlg);
				cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec);
				return new CipherInputStream(in, cipher);
			} catch (GeneralSecurityException e) {
				throw error(e);
			}
		}
	}

	/** Encryption property names. */
	interface Keys {
		// Remote S3 meta: V1 algorithm name or V2 profile name.
		String JGIT_PROFILE = "jgit-crypto-profile"; //$NON-NLS-1$

		// Remote S3 meta: JGit encryption implementation version.
		String JGIT_VERSION = "jgit-crypto-version"; //$NON-NLS-1$

		// Remote S3 meta: base-64 encoded cipher algorithm parameters.
		String JGIT_CONTEXT = "jgit-crypto-context"; //$NON-NLS-1$

		// Amazon S3 connection configuration file profile property suffixes:
		String X_ALGO = ".algo"; //$NON-NLS-1$
		String X_KEY_ALGO = ".key.algo"; //$NON-NLS-1$
		String X_KEY_SIZE = ".key.size"; //$NON-NLS-1$
		String X_KEY_ITER = ".key.iter"; //$NON-NLS-1$
		String X_KEY_SALT = ".key.salt"; //$NON-NLS-1$
	}

	/** Encryption constants and defaults. */
	interface Vals {
		// Compatibility defaults.
		String DEFAULT_VERS = "0"; //$NON-NLS-1$
		String DEFAULT_ALGO = JetS3tV2.ALGORITHM;
		String DEFAULT_KEY_ALGO = JetS3tV2.ALGORITHM;
		String DEFAULT_KEY_SIZE = Integer.toString(JetS3tV2.KEY_SIZE);
		String DEFAULT_KEY_ITER = Integer.toString(JetS3tV2.ITERATIONS);
		String DEFAULT_KEY_SALT = DatatypeConverter.printHexBinary(JetS3tV2.SALT);

		String EMPTY = ""; //$NON-NLS-1$

		// Match white space.
		String REGEX_WS = "\\s+"; //$NON-NLS-1$

		// Match PBE ciphers, i.e: PBEWithMD5AndDES
		String REGEX_PBE = "(PBE).*(WITH).+(AND).+"; //$NON-NLS-1$

		// Match transformation ciphers, i.e: AES/CBC/PKCS5Padding
		String REGEX_TRANS = "(.+)/(.+)/(.+)"; //$NON-NLS-1$
	}

	static GeneralSecurityException securityError(String message) {
		return new GeneralSecurityException(
				MessageFormat.format(JGitText.get().encryptionError, message));
	}

	/**
	 * Base implementation of JGit symmetric encryption. Supports V2 properties
	 * format.
	 */
	static abstract class SymmetricEncryption extends WalkEncryption
			implements Keys, Vals {

		/** Encryption profile, root name of group of related properties. */
		final String profile;

		/** Encryption version, reflects actual implementation class. */
		final String version;

		/** Full cipher algorithm name. */
		final String cipherAlgo;

		/** Cipher algorithm name for parameters lookup. */
		final String paramsAlgo;

		/** Generated secret key. */
		final SecretKey secretKey;

		SymmetricEncryption(Properties props) throws GeneralSecurityException {

			profile = props.getProperty(AmazonS3.Keys.CRYPTO_ALG);
			version = props.getProperty(AmazonS3.Keys.CRYPTO_VER);
			String pass = props.getProperty(AmazonS3.Keys.PASSWORD);

			cipherAlgo = props.getProperty(profile + X_ALGO, DEFAULT_ALGO);

			String keyAlgo = props.getProperty(profile + X_KEY_ALGO, DEFAULT_KEY_ALGO);
			String keySize = props.getProperty(profile + X_KEY_SIZE, DEFAULT_KEY_SIZE);
			String keyIter = props.getProperty(profile + X_KEY_ITER, DEFAULT_KEY_ITER);
			String keySalt = props.getProperty(profile + X_KEY_SALT, DEFAULT_KEY_SALT);

			// Verify if cipher is present.
			Cipher cipher = InsecureCipherFactory.create(cipherAlgo);

			// Verify if key factory is present.
			SecretKeyFactory factory = SecretKeyFactory.getInstance(keyAlgo);

			final int size;
			try {
				size = Integer.parseInt(keySize);
			} catch (Exception e) {
				throw securityError(X_KEY_SIZE + EMPTY + keySize);
			}

			final int iter;
			try {
				iter = Integer.parseInt(keyIter);
			} catch (Exception e) {
				throw securityError(X_KEY_ITER + EMPTY + keyIter);
			}

			final byte[] salt;
			try {
				salt = DatatypeConverter
						.parseHexBinary(keySalt.replaceAll(REGEX_WS, EMPTY));
			} catch (Exception e) {
				throw securityError(X_KEY_SALT + EMPTY + keySalt);
			}

			KeySpec keySpec = new PBEKeySpec(pass.toCharArray(), salt, iter, size);

			SecretKey keyBase = factory.generateSecret(keySpec);

			String name = cipherAlgo.toUpperCase(Locale.ROOT);
			Matcher matcherPBE = Pattern.compile(REGEX_PBE).matcher(name);
			Matcher matcherTrans = Pattern.compile(REGEX_TRANS).matcher(name);
			if (matcherPBE.matches()) {
				paramsAlgo = cipherAlgo;
				secretKey = keyBase;
			} else if (matcherTrans.find()) {
				paramsAlgo = matcherTrans.group(1);
				secretKey = new SecretKeySpec(keyBase.getEncoded(), paramsAlgo);
			} else {
				throw new GeneralSecurityException(MessageFormat.format(
						JGitText.get().unsupportedEncryptionAlgorithm,
						cipherAlgo));
			}

			// Verify if cipher + key are allowed by policy.
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			cipher.doFinal();

		}

		// Shared state encrypt -> request.
		volatile String context;

		@Override
		OutputStream encrypt(OutputStream output) throws IOException {
			try {
				Cipher cipher = InsecureCipherFactory.create(cipherAlgo);
				cipher.init(Cipher.ENCRYPT_MODE, secretKey);
				AlgorithmParameters params = cipher.getParameters();
				if (params == null) {
					context = EMPTY;
				} else {
					context = Base64.encodeBytes(params.getEncoded());
				}
				return new CipherOutputStream(output, cipher);
			} catch (Exception e) {
				throw error(e);
			}
		}

		@Override
		void request(HttpURLConnection conn, String prefix) throws IOException {
			conn.setRequestProperty(prefix + JGIT_PROFILE, profile);
			conn.setRequestProperty(prefix + JGIT_VERSION, version);
			conn.setRequestProperty(prefix + JGIT_CONTEXT, context);
			// No cleanup:
			// single encrypt can be followed by several request
			// from the AmazonS3.putImpl() multiple retry attempts
			// context = null; // Cleanup encrypt -> request transition.
			// TODO re-factor AmazonS3.putImpl to be more transaction-like
		}

		// Shared state validate -> decrypt.
		volatile Cipher decryptCipher;

		@Override
		void validate(HttpURLConnection conn, String prefix)
				throws IOException {
			String prof = conn.getHeaderField(prefix + JGIT_PROFILE);
			String vers = conn.getHeaderField(prefix + JGIT_VERSION);
			String cont = conn.getHeaderField(prefix + JGIT_CONTEXT);

			if (prof == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().encryptionError, JGIT_PROFILE));
			}
			if (vers == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().encryptionError, JGIT_VERSION));
			}
			if (cont == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().encryptionError, JGIT_CONTEXT));
			}
			if (!profile.equals(prof)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().unsupportedEncryptionAlgorithm, prof));
			}
			if (!version.equals(vers)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().unsupportedEncryptionVersion, vers));
			}
			try {
				decryptCipher = InsecureCipherFactory.create(cipherAlgo);
				if (cont.isEmpty()) {
					decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);
				} else {
					AlgorithmParameters params = AlgorithmParameters
							.getInstance(paramsAlgo);
					params.init(Base64.decode(cont));
					decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, params);
				}
			} catch (Exception e) {
				throw error(e);
			}
		}

		@Override
		InputStream decrypt(InputStream input) throws IOException {
			try {
				return new CipherInputStream(input, decryptCipher);
			} finally {
				decryptCipher = null; // Cleanup validate -> decrypt transition.
			}
		}
	}

	/**
	 * Provides JetS3t-like encryption with AES support. Uses V1 connection file
	 * format. For reference, see: 'jgit-s3-connection-v-1.properties'.
	 */
	static class JGitV1 extends SymmetricEncryption {

		static final String VERSION = "1"; //$NON-NLS-1$

		// Re-map connection properties V1 -> V2.
		static Properties wrap(String algo, String pass) {
			Properties props = new Properties();
			props.put(AmazonS3.Keys.CRYPTO_ALG, algo);
			props.put(AmazonS3.Keys.CRYPTO_VER, VERSION);
			props.put(AmazonS3.Keys.PASSWORD, pass);
			props.put(algo + Keys.X_ALGO, algo);
			props.put(algo + Keys.X_KEY_ALGO, algo);
			props.put(algo + Keys.X_KEY_ITER, DEFAULT_KEY_ITER);
			props.put(algo + Keys.X_KEY_SIZE, DEFAULT_KEY_SIZE);
			props.put(algo + Keys.X_KEY_SALT, DEFAULT_KEY_SALT);
			return props;
		}

		JGitV1(String algo, String pass)
				throws GeneralSecurityException {
			super(wrap(algo, pass));
			String name = cipherAlgo.toUpperCase(Locale.ROOT);
			Matcher matcherPBE = Pattern.compile(REGEX_PBE).matcher(name);
			if (!matcherPBE.matches())
				throw new GeneralSecurityException(
						JGitText.get().encryptionOnlyPBE);
		}

	}

	/**
	 * Supports both PBE and non-PBE algorithms. Uses V2 connection file format.
	 * For reference, see: 'jgit-s3-connection-v-2.properties'.
	 */
	static class JGitV2 extends SymmetricEncryption {

		static final String VERSION = "2"; //$NON-NLS-1$

		JGitV2(Properties props)
				throws GeneralSecurityException {
			super(props);
		}
	}

	/**
	 * Encryption factory.
	 *
	 * @param props
	 * @return instance
	 * @throws GeneralSecurityException
	 */
	static WalkEncryption instance(Properties props)
			throws GeneralSecurityException {

		String algo = props.getProperty(AmazonS3.Keys.CRYPTO_ALG, Vals.DEFAULT_ALGO);
		String vers = props.getProperty(AmazonS3.Keys.CRYPTO_VER, Vals.DEFAULT_VERS);
		String pass = props.getProperty(AmazonS3.Keys.PASSWORD);

		if (pass == null) // Disable encryption.
			return WalkEncryption.NONE;

		switch (vers) {
		case Vals.DEFAULT_VERS:
			return new JetS3tV2(algo, pass);
		case JGitV1.VERSION:
			return new JGitV1(algo, pass);
		case JGitV2.VERSION:
			return new JGitV2(props);
		default:
			throw new GeneralSecurityException(MessageFormat.format(
					JGitText.get().unsupportedEncryptionVersion, vers));
		}
	}
}
