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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.eclipse.jgit.internal.JGitText;

abstract class WalkEncryption {
	static final WalkEncryption NONE = new NoEncryption();

	static final String JETS3T_CRYPTO_VER = "jets3t-crypto-ver";

	static final String JETS3T_CRYPTO_ALG = "jets3t-crypto-alg";

	abstract OutputStream encrypt(OutputStream os) throws IOException;

	abstract InputStream decrypt(InputStream in) throws IOException;

	abstract void request(HttpURLConnection u, String prefix);

	abstract void validate(HttpURLConnection u, String p) throws IOException;

	protected void validateImpl(final HttpURLConnection u, final String p,
			final String version, final String name) throws IOException {
		String v;

		v = u.getHeaderField(p + JETS3T_CRYPTO_VER);
		if (v == null)
			v = "";
		if (!version.equals(v))
			throw new IOException(MessageFormat.format(JGitText.get().unsupportedEncryptionVersion, v));

		v = u.getHeaderField(p + JETS3T_CRYPTO_ALG);
		if (v == null)
			v = "";
		if (!name.equals(v))
			throw new IOException(JGitText.get().unsupportedEncryptionAlgorithm + v);
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
		void validate(final HttpURLConnection u, final String p)
				throws IOException {
			validateImpl(u, p, "", "");
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

	static class ObjectEncryptionV2 extends WalkEncryption {
		private static int ITERATION_COUNT = 5000;

		private static byte[] salt = { (byte) 0xA4, (byte) 0x0B, (byte) 0xC8,
				(byte) 0x34, (byte) 0xD6, (byte) 0x95, (byte) 0xF3, (byte) 0x13 };

		private final String algorithmName;

		private final SecretKey skey;

		private final PBEParameterSpec aspec;

		ObjectEncryptionV2(final String algo, final String key)
				throws InvalidKeySpecException, NoSuchAlgorithmException {
			algorithmName = algo;

			final PBEKeySpec s;
			s = new PBEKeySpec(key.toCharArray(), salt, ITERATION_COUNT, 32);
			skey = SecretKeyFactory.getInstance(algo).generateSecret(s);
			aspec = new PBEParameterSpec(salt, ITERATION_COUNT);
		}

		@Override
		void request(final HttpURLConnection u, final String prefix) {
			u.setRequestProperty(prefix + JETS3T_CRYPTO_VER, "2");
			u.setRequestProperty(prefix + JETS3T_CRYPTO_ALG, algorithmName);
		}

		@Override
		void validate(final HttpURLConnection u, final String p)
				throws IOException {
			validateImpl(u, p, "2", algorithmName);
		}

		@Override
		OutputStream encrypt(final OutputStream os) throws IOException {
			try {
				final Cipher c = Cipher.getInstance(algorithmName);
				c.init(Cipher.ENCRYPT_MODE, skey, aspec);
				return new CipherOutputStream(os, c);
			} catch (NoSuchAlgorithmException e) {
				throw error(e);
			} catch (NoSuchPaddingException e) {
				throw error(e);
			} catch (InvalidKeyException e) {
				throw error(e);
			} catch (InvalidAlgorithmParameterException e) {
				throw error(e);
			}
		}

		@Override
		InputStream decrypt(final InputStream in) throws IOException {
			try {
				final Cipher c = Cipher.getInstance(algorithmName);
				c.init(Cipher.DECRYPT_MODE, skey, aspec);
				return new CipherInputStream(in, c);
			} catch (NoSuchAlgorithmException e) {
				throw error(e);
			} catch (NoSuchPaddingException e) {
				throw error(e);
			} catch (InvalidKeyException e) {
				throw error(e);
			} catch (InvalidAlgorithmParameterException e) {
				throw error(e);
			}
		}
	}
}
