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

import static java.nio.file.Files.exists;
import static java.nio.file.Files.newInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bouncycastle.gpg.SExprParser;
import org.bouncycastle.gpg.keybox.BlobType;
import org.bouncycastle.gpg.keybox.KeyBlob;
import org.bouncycastle.gpg.keybox.KeyBox;
import org.bouncycastle.gpg.keybox.KeyInformation;
import org.bouncycastle.gpg.keybox.PublicKeyRingBlob;
import org.bouncycastle.gpg.keybox.UserID;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBEProtectionRemoverFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEProtectionRemoverFactory;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates GPG keys from either <code>~/.gnupg/private-keys-v1.d</code> or
 * <code>~/.gnupg/secring.gpg</code>
 */
class BouncyCastleGpgKeyLocator {

	private static final Logger log = LoggerFactory
			.getLogger(BouncyCastleGpgKeyLocator.class);

	private static final Path GPG_DIRECTORY = findGpgDirectory();

	private static final Path USER_KEYBOX_PATH = GPG_DIRECTORY
			.resolve("pubring.kbx"); //$NON-NLS-1$

	private static final Path USER_SECRET_KEY_DIR = GPG_DIRECTORY
			.resolve("private-keys-v1.d"); //$NON-NLS-1$

	private static final Path USER_PGP_LEGACY_SECRING_FILE = GPG_DIRECTORY
			.resolve("secring.gpg"); //$NON-NLS-1$

	private final String signingKey;

	private BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt;

	private static Path findGpgDirectory() {
		SystemReader system = SystemReader.getInstance();
		if (system.isWindows()) {
			// On Windows prefer %APPDATA%\gnupg if it exists, even if Cygwin is
			// used.
			String appData = system.getenv("APPDATA"); //$NON-NLS-1$
			if (appData != null && !appData.isEmpty()) {
				try {
					Path directory = Paths.get(appData).resolve("gnupg"); //$NON-NLS-1$
					if (Files.isDirectory(directory)) {
						return directory;
					}
				} catch (SecurityException | InvalidPathException e) {
					// Ignore and return the default location below.
				}
			}
		}
		// All systems, including Cygwin and even Windows if
		// %APPDATA%\gnupg doesn't exist: ~/.gnupg
		File home = FS.DETECTED.userHome();
		if (home == null) {
			// Oops. What now?
			home = new File(".").getAbsoluteFile(); //$NON-NLS-1$
		}
		return home.toPath().resolve(".gnupg"); //$NON-NLS-1$
	}

	/**
	 * Create a new key locator for the specified signing key.
	 * <p>
	 * The signing key must either be a hex representation of a specific key or
	 * a user identity substring (eg., email address). All keys in the KeyBox
	 * will be looked up in the order as returned by the KeyBox. A key id will
	 * be searched before attempting to find a key by user id.
	 * </p>
	 *
	 * @param signingKey
	 *            the signing key to search for
	 * @param passphrasePrompt
	 *            the provider to use when asking for key passphrase
	 */
	public BouncyCastleGpgKeyLocator(String signingKey,
			@NonNull BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt) {
		this.signingKey = signingKey;
		this.passphrasePrompt = passphrasePrompt;
	}

	private PGPSecretKey attemptParseSecretKey(Path keyFile,
			PGPDigestCalculatorProvider calculatorProvider,
			PBEProtectionRemoverFactory passphraseProvider,
			PGPPublicKey publicKey) {
		try (InputStream in = newInputStream(keyFile)) {
			return new SExprParser(calculatorProvider).parseSecretKey(
					new BufferedInputStream(in), passphraseProvider, publicKey);
		} catch (IOException | PGPException | ClassCastException e) {
			if (log.isDebugEnabled())
				log.debug("Ignoring unreadable file '{}': {}", keyFile, //$NON-NLS-1$
						e.getMessage(), e);
			return null;
		}
	}

	private boolean containsSigningKey(String userId) {
		return userId.toLowerCase(Locale.ROOT)
				.contains(signingKey.toLowerCase(Locale.ROOT));
	}

	private PGPPublicKey findPublicKeyByKeyId(KeyBlob keyBlob)
			throws IOException {
		String keyId = signingKey.toLowerCase(Locale.ROOT);
		for (KeyInformation keyInfo : keyBlob.getKeyInformation()) {
			String fingerprint = Hex.toHexString(keyInfo.getFingerprint())
					.toLowerCase(Locale.ROOT);
			if (fingerprint.endsWith(keyId)) {
				return getFirstPublicKey(keyBlob);
			}
		}
		return null;
	}

	private PGPPublicKey findPublicKeyByUserId(KeyBlob keyBlob)
			throws IOException {
		for (UserID userID : keyBlob.getUserIds()) {
			if (containsSigningKey(userID.getUserIDAsString())) {
				return getFirstPublicKey(keyBlob);
			}
		}
		return null;
	}

	/**
	 * Finds a public key associated with the signing key.
	 *
	 * @param keyboxFile
	 *            the KeyBox file
	 * @return publicKey the public key (maybe <code>null</code>)
	 * @throws IOException
	 *             in case of problems reading the file
	 */
	private PGPPublicKey findPublicKeyInKeyBox(Path keyboxFile)
			throws IOException {
		KeyBox keyBox = readKeyBoxFile(keyboxFile);
		for (KeyBlob keyBlob : keyBox.getKeyBlobs()) {
			if (keyBlob.getType() == BlobType.OPEN_PGP_BLOB) {
				PGPPublicKey key = findPublicKeyByKeyId(keyBlob);
				if (key != null) {
					return key;
				}
				key = findPublicKeyByUserId(keyBlob);
				if (key != null) {
					return key;
				}
			}
		}
		return null;
	}

	/**
	 * Use pubring.kbx when available, if not fallback to secring.gpg or secret
	 * key path provided to parse and return secret key
	 *
	 * @return the secret key
	 * @throws IOException
	 *             in case of issues reading key files
	 * @throws PGPException
	 *             in case of issues finding a key
	 * @throws CanceledException
	 * @throws URISyntaxException
	 * @throws UnsupportedCredentialItem
	 */
	public BouncyCastleGpgKey findSecretKey()
			throws IOException, PGPException, CanceledException,
			UnsupportedCredentialItem, URISyntaxException {
		if (exists(USER_KEYBOX_PATH)) {
			PGPPublicKey publicKey = //
					findPublicKeyInKeyBox(USER_KEYBOX_PATH);

			if (publicKey != null) {
				return findSecretKeyForKeyBoxPublicKey(publicKey,
						USER_KEYBOX_PATH);
			}

			throw new PGPException(MessageFormat
					.format(JGitText.get().gpgNoPublicKeyFound, signingKey));
		} else if (exists(USER_PGP_LEGACY_SECRING_FILE)) {
			PGPSecretKey secretKey = findSecretKeyInLegacySecring(signingKey,
					USER_PGP_LEGACY_SECRING_FILE);

			if (secretKey != null) {
				return new BouncyCastleGpgKey(secretKey, USER_PGP_LEGACY_SECRING_FILE);
			}

			throw new PGPException(MessageFormat.format(
					JGitText.get().gpgNoKeyInLegacySecring, signingKey));
		}

		throw new PGPException(JGitText.get().gpgNoKeyring);
	}

	private BouncyCastleGpgKey findSecretKeyForKeyBoxPublicKey(
			PGPPublicKey publicKey, Path userKeyboxPath)
			throws PGPException, CanceledException, UnsupportedCredentialItem,
			URISyntaxException {
		/*
		 * this is somewhat brute-force but there doesn't seem to be another
		 * way; we have to walk all private key files we find and try to open
		 * them
		 */

		PGPDigestCalculatorProvider calculatorProvider = new JcaPGPDigestCalculatorProviderBuilder()
				.build();

		PBEProtectionRemoverFactory passphraseProvider = new JcePBEProtectionRemoverFactory(
				passphrasePrompt.getPassphrase(publicKey.getFingerprint(),
						userKeyboxPath));

		try (Stream<Path> keyFiles = Files.walk(USER_SECRET_KEY_DIR)) {
			for (Path keyFile : keyFiles.filter(Files::isRegularFile)
					.collect(Collectors.toList())) {
				PGPSecretKey secretKey = attemptParseSecretKey(keyFile,
						calculatorProvider, passphraseProvider, publicKey);
				if (secretKey != null) {
					return new BouncyCastleGpgKey(secretKey, userKeyboxPath);
				}
			}

			passphrasePrompt.clear();
			throw new PGPException(MessageFormat.format(
					JGitText.get().gpgNoSecretKeyForPublicKey,
					Long.toHexString(publicKey.getKeyID())));
		} catch (RuntimeException e) {
			passphrasePrompt.clear();
			throw e;
		} catch (IOException e) {
			passphrasePrompt.clear();
			throw new PGPException(MessageFormat.format(
					JGitText.get().gpgFailedToParseSecretKey,
					USER_SECRET_KEY_DIR.toAbsolutePath()), e);
		}
	}

	/**
	 * Return the first suitable key for signing in the key ring collection. For
	 * this case we only expect there to be one key available for signing.
	 * </p>
	 *
	 * @param signingkey
	 * @param secringFile
	 *
	 * @return the first suitable PGP secret key found for signing
	 * @throws IOException
	 *             on I/O related errors
	 * @throws PGPException
	 *             on BouncyCastle errors
	 */
	private PGPSecretKey findSecretKeyInLegacySecring(String signingkey,
			Path secringFile) throws IOException, PGPException {

		try (InputStream in = newInputStream(secringFile)) {
			PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
					PGPUtil.getDecoderStream(new BufferedInputStream(in)),
					new JcaKeyFingerprintCalculator());

			String keyId = signingkey.toLowerCase(Locale.ROOT);
			Iterator<PGPSecretKeyRing> keyrings = pgpSec.getKeyRings();
			while (keyrings.hasNext()) {
				PGPSecretKeyRing keyRing = keyrings.next();
				Iterator<PGPSecretKey> keys = keyRing.getSecretKeys();
				while (keys.hasNext()) {
					PGPSecretKey key = keys.next();
					// try key id
					String fingerprint = Hex
							.toHexString(key.getPublicKey().getFingerprint())
							.toLowerCase(Locale.ROOT);
					if (fingerprint.endsWith(keyId)) {
						return key;
					}
					// try user id
					Iterator<String> userIDs = key.getUserIDs();
					while (userIDs.hasNext()) {
						String userId = userIDs.next();
						if (containsSigningKey(userId)) {
							return key;
						}
					}
				}
			}
		}
		return null;
	}

	private PGPPublicKey getFirstPublicKey(KeyBlob keyBlob) throws IOException {
		return ((PublicKeyRingBlob) keyBlob).getPGPPublicKeyRing()
				.getPublicKey();
	}

	private KeyBox readKeyBoxFile(Path keyboxFile) throws IOException {
		KeyBox keyBox;
		try (InputStream in = new BufferedInputStream(
				newInputStream(keyboxFile))) {
			// note: KeyBox constructor reads in the whole InputStream at once
			// this code will change in 1.61 to
			// either 'new BcKeyBox(in)' or 'new JcaKeyBoxBuilder().build(in)'
			keyBox = new KeyBox(in, new JcaKeyFingerprintCalculator());
		}
		return keyBox;
	}
}
