/*
 * Copyright (C) 2018, 2021 Salesforce and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.gpg.bc.internal;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.newInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bouncycastle.gpg.keybox.BlobType;
import org.bouncycastle.gpg.keybox.KeyBlob;
import org.bouncycastle.gpg.keybox.KeyBox;
import org.bouncycastle.gpg.keybox.KeyInformation;
import org.bouncycastle.gpg.keybox.PublicKeyRingBlob;
import org.bouncycastle.gpg.keybox.UserID;
import org.bouncycastle.gpg.keybox.jcajce.JcaKeyBoxBuilder;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyFlags;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.gpg.bc.internal.keys.KeyGrip;
import org.eclipse.jgit.gpg.bc.internal.keys.SecretKeys;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locates GPG keys from either <code>~/.gnupg/private-keys-v1.d</code> or
 * <code>~/.gnupg/secring.gpg</code>
 */
public class BouncyCastleGpgKeyLocator {

	/** Thrown if a keybox file exists but doesn't contain an OpenPGP key. */
	private static class NoOpenPgpKeyException extends Exception {

		private static final long serialVersionUID = 1L;

	}

	private static final Logger log = LoggerFactory
			.getLogger(BouncyCastleGpgKeyLocator.class);

	static final Path GPG_DIRECTORY = findGpgDirectory();

	private static final Path USER_KEYBOX_PATH = GPG_DIRECTORY
			.resolve("pubring.kbx"); //$NON-NLS-1$

	private static final Path USER_SECRET_KEY_DIR = GPG_DIRECTORY
			.resolve("private-keys-v1.d"); //$NON-NLS-1$

	private static final Path USER_PGP_PUBRING_FILE = GPG_DIRECTORY
			.resolve("pubring.gpg"); //$NON-NLS-1$

	private static final Path USER_PGP_LEGACY_SECRING_FILE = GPG_DIRECTORY
			.resolve("secring.gpg"); //$NON-NLS-1$

	private final String signingKey;

	private BouncyCastleGpgKeyPassphrasePrompt passphrasePrompt;

	private static Path findGpgDirectory() {
		SystemReader system = SystemReader.getInstance();
		Function<String, Path> resolveTilde = s -> {
			if (s.startsWith("~/") || s.startsWith("~" + File.separatorChar)) { //$NON-NLS-1$ //$NON-NLS-2$
				return new File(FS.DETECTED.userHome(), s.substring(2))
						.getAbsoluteFile().toPath();
			}
			return Paths.get(s);
		};
		Path path = checkDirectory(system.getProperty("jgit.gpg.home"), //$NON-NLS-1$
				resolveTilde,
				s -> log.warn(BCText.get().logWarnGpgHomeProperty, s));
		if (path != null) {
			return path;
		}
		path = checkDirectory(system.getenv("GNUPGHOME"), resolveTilde, //$NON-NLS-1$
				s -> log.warn(BCText.get().logWarnGnuPGHome, s));
		if (path != null) {
			return path;
		}
		if (system.isWindows()) {
			// On Windows prefer %APPDATA%\gnupg if it exists, even if Cygwin is
			// used.
			path = checkDirectory(system.getenv("APPDATA"), //$NON-NLS-1$
					s -> Paths.get(s).resolve("gnupg"), null); //$NON-NLS-1$
			if (path != null) {
				return path;
			}
		}
		// All systems, including Cygwin and even Windows if
		// %APPDATA%\gnupg doesn't exist: ~/.gnupg
		return resolveTilde.apply("~/.gnupg"); //$NON-NLS-1$
	}

	private static Path checkDirectory(String dir,
			Function<String, Path> toPath, Consumer<String> warn) {
		if (!StringUtils.isEmptyOrNull(dir)) {
			try {
				Path directory = toPath.apply(dir);
				if (Files.isDirectory(directory)) {
					return directory;
				}
			} catch (SecurityException | InvalidPathException e) {
				// Ignore, warn, and try other known directories
			}
			if (warn != null) {
				warn.accept(dir);
			}
		}
		return null;
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
			SecretKeys.PassphraseSupplier passphraseSupplier,
			PGPPublicKey publicKey)
			throws IOException, PGPException, CanceledException,
			UnsupportedCredentialItem, URISyntaxException {
		try (InputStream in = newInputStream(keyFile)) {
			return SecretKeys.readSecretKey(in, calculatorProvider,
					passphraseSupplier, publicKey);
		}
	}

	/**
	 * Checks whether a given OpenPGP {@code userId} matches a given
	 * {@code signingKeySpec}, which is supposed to have one of the formats
	 * defined by GPG.
	 * <p>
	 * Not all formats are supported; only formats starting with '=', '&lt;',
	 * '@', and '*' are handled. Any other format results in a case-insensitive
	 * substring match.
	 * </p>
	 *
	 * @param userId
	 *            of a key
	 * @param signingKeySpec
	 *            GPG key identification
	 * @return whether the {@code userId} matches
	 * @see <a href=
	 *      "https://www.gnupg.org/documentation/manuals/gnupg/Specify-a-User-ID.html">GPG
	 *      Documentation: How to Specify a User ID</a>
	 */
	static boolean containsSigningKey(String userId, String signingKeySpec) {
		if (StringUtils.isEmptyOrNull(userId)
				|| StringUtils.isEmptyOrNull(signingKeySpec)) {
			return false;
		}
		String toMatch = signingKeySpec;
		if (toMatch.startsWith("0x") && toMatch.trim().length() > 2) { //$NON-NLS-1$
			return false; // Explicit fingerprint
		}
		int command = toMatch.charAt(0);
		switch (command) {
		case '=':
		case '<':
		case '@':
		case '*':
			toMatch = toMatch.substring(1);
			if (toMatch.isEmpty()) {
				return false;
			}
			break;
		default:
			break;
		}
		switch (command) {
		case '=':
			return userId.equals(toMatch);
		case '<': {
			int begin = userId.indexOf('<');
			int end = userId.indexOf('>', begin + 1);
			int stop = toMatch.indexOf('>');
			return begin >= 0 && end > begin + 1 && stop > 0
					&& userId.substring(begin + 1, end)
							.equalsIgnoreCase(toMatch.substring(0, stop));
		}
		case '@': {
			int begin = userId.indexOf('<');
			int end = userId.indexOf('>', begin + 1);
			return begin >= 0 && end > begin + 1
					&& containsIgnoreCase(userId.substring(begin + 1, end),
							toMatch);
		}
		default:
			if (toMatch.trim().isEmpty()) {
				return false;
			}
			return containsIgnoreCase(userId, toMatch);
		}
	}

	private static boolean containsIgnoreCase(String a, String b) {
		int alength = a.length();
		int blength = b.length();
		for (int i = 0; i + blength <= alength; i++) {
			if (a.regionMatches(true, i, b, 0, blength)) {
				return true;
			}
		}
		return false;
	}

	private static String toFingerprint(String keyId) {
		if (keyId.startsWith("0x")) { //$NON-NLS-1$
			return keyId.substring(2);
		}
		return keyId;
	}

	static PGPPublicKey findPublicKey(String fingerprint, String keySpec)
			throws IOException, PGPException {
		PGPPublicKey result = findPublicKeyInPubring(USER_PGP_PUBRING_FILE,
				fingerprint, keySpec);
		if (result == null && exists(USER_KEYBOX_PATH)) {
			try {
				result = findPublicKeyInKeyBox(USER_KEYBOX_PATH, fingerprint,
						keySpec);
			} catch (NoSuchAlgorithmException | NoSuchProviderException
					| IOException | NoOpenPgpKeyException e) {
				log.error(e.getMessage(), e);
			}
		}
		return result;
	}

	private static PGPPublicKey findPublicKeyByKeyId(KeyBlob keyBlob,
			String keyId)
			throws IOException {
		if (keyId.isEmpty()) {
			return null;
		}
		for (KeyInformation keyInfo : keyBlob.getKeyInformation()) {
			String fingerprint = Hex.toHexString(keyInfo.getFingerprint())
					.toLowerCase(Locale.ROOT);
			if (fingerprint.endsWith(keyId)) {
				return getPublicKey(keyBlob, keyInfo.getFingerprint());
			}
		}
		return null;
	}

	private static PGPPublicKey findPublicKeyByUserId(KeyBlob keyBlob,
			String keySpec)
			throws IOException {
		for (UserID userID : keyBlob.getUserIds()) {
			if (containsSigningKey(userID.getUserIDAsString(), keySpec)) {
				return getSigningPublicKey(keyBlob);
			}
		}
		return null;
	}

	/**
	 * Finds a public key associated with the signing key.
	 *
	 * @param keyboxFile
	 *            the KeyBox file
	 * @param keyId
	 *            to look for, may be null
	 * @param keySpec
	 *            to look for
	 * @return publicKey the public key (maybe <code>null</code>)
	 * @throws IOException
	 *             in case of problems reading the file
	 * @throws NoSuchAlgorithmException
	 *             if an algorithm isn't available
	 * @throws NoSuchProviderException
	 *             if a provider isn't available
	 * @throws NoOpenPgpKeyException
	 *             if the file does not contain any OpenPGP key
	 */
	private static PGPPublicKey findPublicKeyInKeyBox(Path keyboxFile,
			String keyId, String keySpec)
			throws IOException, NoSuchAlgorithmException,
			NoSuchProviderException, NoOpenPgpKeyException {
		KeyBox keyBox = readKeyBoxFile(keyboxFile);
		String id = keyId != null ? keyId
				: toFingerprint(keySpec).toLowerCase(Locale.ROOT);
		boolean hasOpenPgpKey = false;
		for (KeyBlob keyBlob : keyBox.getKeyBlobs()) {
			if (keyBlob.getType() == BlobType.OPEN_PGP_BLOB) {
				hasOpenPgpKey = true;
				PGPPublicKey key = findPublicKeyByKeyId(keyBlob, id);
				if (key != null) {
					return key;
				}
				key = findPublicKeyByUserId(keyBlob, keySpec);
				if (key != null) {
					return key;
				}
			}
		}
		if (!hasOpenPgpKey) {
			throw new NoOpenPgpKeyException();
		}
		return null;
	}

	/**
	 * If there is a private key directory containing keys, use pubring.kbx or
	 * pubring.gpg to find the public key; then try to find the secret key in
	 * the directory.
	 * <p>
	 * If there is no private key directory (or it doesn't contain any keys),
	 * try to find the key in secring.gpg directly.
	 * </p>
	 *
	 * @return the secret key
	 * @throws IOException
	 *             in case of issues reading key files
	 * @throws NoSuchAlgorithmException
	 *             algorithm is not available
	 * @throws NoSuchProviderException
	 *             provider is not available
	 * @throws PGPException
	 *             in case of issues finding a key, including no key found
	 * @throws CanceledException
	 *             operation was cancelled
	 * @throws URISyntaxException
	 *             URI is invalid
	 * @throws UnsupportedCredentialItem
	 *             credential item is not supported
	 */
	@NonNull
	public BouncyCastleGpgKey findSecretKey() throws IOException,
			NoSuchAlgorithmException, NoSuchProviderException, PGPException,
			CanceledException, UnsupportedCredentialItem, URISyntaxException {
		BouncyCastleGpgKey key;
		PGPPublicKey publicKey = null;
		if (hasKeyFiles(USER_SECRET_KEY_DIR)) {
			// Use pubring.kbx or pubring.gpg to find the public key, then try
			// the key files in the directory. If the public key was found in
			// pubring.gpg also try secring.gpg to find the secret key.
			if (exists(USER_KEYBOX_PATH)) {
				try {
					publicKey = findPublicKeyInKeyBox(USER_KEYBOX_PATH, null,
							signingKey);
					if (publicKey != null) {
						key = findSecretKeyForKeyBoxPublicKey(publicKey,
								USER_KEYBOX_PATH);
						if (key != null) {
							return key;
						}
						throw new PGPException(MessageFormat.format(
								BCText.get().gpgNoSecretKeyForPublicKey,
								Long.toHexString(publicKey.getKeyID())));
					}
					throw new PGPException(MessageFormat.format(
							BCText.get().gpgNoPublicKeyFound, signingKey));
				} catch (NoOpenPgpKeyException e) {
					// There are no OpenPGP keys in the keybox at all: try the
					// pubring.gpg, if it exists.
					if (log.isDebugEnabled()) {
						log.debug("{} does not contain any OpenPGP keys", //$NON-NLS-1$
								USER_KEYBOX_PATH);
					}
				}
			}
			if (exists(USER_PGP_PUBRING_FILE)) {
				publicKey = findPublicKeyInPubring(USER_PGP_PUBRING_FILE, null,
						signingKey);
				if (publicKey != null) {
					// GPG < 2.1 may have both; the agent using the directory
					// and gpg using secring.gpg. GPG >= 2.1 delegates all
					// secret key handling to the agent and doesn't use
					// secring.gpg at all, even if it exists. Which means for us
					// we have to try both since we don't know which GPG version
					// the user has.
					key = findSecretKeyForKeyBoxPublicKey(publicKey,
							USER_PGP_PUBRING_FILE);
					if (key != null) {
						return key;
					}
				}
			}
			if (publicKey == null) {
				throw new PGPException(MessageFormat.format(
						BCText.get().gpgNoPublicKeyFound, signingKey));
			}
			// We found a public key, but didn't find the secret key in the
			// private key directory. Go try the secring.gpg.
		}
		boolean hasSecring = false;
		if (exists(USER_PGP_LEGACY_SECRING_FILE)) {
			hasSecring = true;
			key = loadKeyFromSecring(USER_PGP_LEGACY_SECRING_FILE);
			if (key != null) {
				return key;
			}
		}
		if (publicKey != null) {
			throw new PGPException(MessageFormat.format(
					BCText.get().gpgNoSecretKeyForPublicKey,
					Long.toHexString(publicKey.getKeyID())));
		} else if (hasSecring) {
			// publicKey == null: user has _only_ pubring.gpg/secring.gpg.
			throw new PGPException(MessageFormat.format(
					BCText.get().gpgNoKeyInLegacySecring, signingKey));
		} else {
			throw new PGPException(BCText.get().gpgNoKeyring);
		}
	}

	private boolean hasKeyFiles(Path dir) {
		try (DirectoryStream<Path> contents = Files.newDirectoryStream(dir,
				"*.key")) { //$NON-NLS-1$
			return contents.iterator().hasNext();
		} catch (IOException e) {
			// Not a directory, or something else
			return false;
		}
	}

	private BouncyCastleGpgKey loadKeyFromSecring(Path secring)
			throws IOException, PGPException {
		PGPSecretKey secretKey = findSecretKeyInLegacySecring(signingKey,
				secring);

		if (secretKey != null) {
			if (!secretKey.isSigningKey()) {
				throw new PGPException(MessageFormat
						.format(BCText.get().gpgNotASigningKey, signingKey));
			}
			return new BouncyCastleGpgKey(secretKey, secring);
		}
		return null;
	}

	private BouncyCastleGpgKey findSecretKeyForKeyBoxPublicKey(
			PGPPublicKey publicKey, Path userKeyboxPath)
			throws PGPException, CanceledException, UnsupportedCredentialItem,
			URISyntaxException {
		byte[] keyGrip = null;
		try {
			keyGrip = KeyGrip.getKeyGrip(publicKey);
		} catch (PGPException e) {
			throw new PGPException(
					MessageFormat.format(BCText.get().gpgNoKeygrip,
							Hex.toHexString(publicKey.getFingerprint())),
					e);
		}
		String filename = Hex.toHexString(keyGrip).toUpperCase(Locale.ROOT)
				+ ".key"; //$NON-NLS-1$
		Path keyFile = USER_SECRET_KEY_DIR.resolve(filename);
		if (!Files.exists(keyFile)) {
			return null;
		}
		boolean clearPrompt = false;
		try {
			PGPDigestCalculatorProvider calculatorProvider = new JcaPGPDigestCalculatorProviderBuilder()
					.build();
			clearPrompt = true;
			PGPSecretKey secretKey = null;
			try {
				secretKey = attemptParseSecretKey(keyFile, calculatorProvider,
						() -> passphrasePrompt.getPassphrase(
								publicKey.getFingerprint(), userKeyboxPath),
						publicKey);
			} catch (PGPException e) {
				throw new PGPException(MessageFormat.format(
						BCText.get().gpgFailedToParseSecretKey,
						keyFile.toAbsolutePath()), e);
			}
			if (secretKey != null) {
				if (!secretKey.isSigningKey()) {
					throw new PGPException(MessageFormat.format(
							BCText.get().gpgNotASigningKey, signingKey));
				}
				clearPrompt = false;
				return new BouncyCastleGpgKey(secretKey, userKeyboxPath);
			}
			return null;
		} catch (RuntimeException e) {
			throw e;
		} catch (FileNotFoundException | NoSuchFileException e) {
			clearPrompt = false;
			return null;
		} catch (IOException e) {
			throw new PGPException(MessageFormat.format(
					BCText.get().gpgFailedToParseSecretKey,
					keyFile.toAbsolutePath()), e);
		} finally {
			if (clearPrompt) {
				passphrasePrompt.clear();
			}
		}
	}

	/**
	 * Return the first suitable key for signing in the key ring collection. For
	 * this case we only expect there to be one key available for signing.
	 *
	 * @param signingKeyName
	 *            the signing key
	 * @param secringFile
	 *            the secring file
	 *
	 * @return the first suitable PGP secret key found for signing
	 * @throws IOException
	 *             on I/O related errors
	 * @throws PGPException
	 *             on BouncyCastle errors
	 */
	private PGPSecretKey findSecretKeyInLegacySecring(String signingKeyName,
			Path secringFile) throws IOException, PGPException {

		try (InputStream in = newInputStream(secringFile)) {
			PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
					PGPUtil.getDecoderStream(new BufferedInputStream(in)),
					new JcaKeyFingerprintCalculator());

			String keyId = toFingerprint(signingKeyName).toLowerCase(Locale.ROOT);
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
						if (containsSigningKey(userId, signingKey)) {
							return key;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Return the first public key matching the key id ({@link #signingKey}.
	 *
	 * @param pubringFile
	 *            to search
	 * @param keyId
	 *            to look for, may be null
	 * @param keySpec
	 *            to look for
	 *
	 * @return the PGP public key, or {@code null} if none found
	 * @throws IOException
	 *             on I/O related errors
	 * @throws PGPException
	 *             on BouncyCastle errors
	 */
	private static PGPPublicKey findPublicKeyInPubring(Path pubringFile,
			String keyId, String keySpec)
			throws IOException, PGPException {
		try (InputStream in = newInputStream(pubringFile)) {
			PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
					new BufferedInputStream(in),
					new JcaKeyFingerprintCalculator());

			String id = keyId != null ? keyId
					: toFingerprint(keySpec).toLowerCase(Locale.ROOT);
			Iterator<PGPPublicKeyRing> keyrings = pgpPub.getKeyRings();
			while (keyrings.hasNext()) {
				PGPPublicKeyRing keyRing = keyrings.next();
				Iterator<PGPPublicKey> keys = keyRing.getPublicKeys();
				while (keys.hasNext()) {
					PGPPublicKey key = keys.next();
					// try key id
					String fingerprint = Hex.toHexString(key.getFingerprint())
							.toLowerCase(Locale.ROOT);
					if (fingerprint.endsWith(id)) {
						return key;
					}
					// try user id
					Iterator<String> userIDs = key.getUserIDs();
					while (userIDs.hasNext()) {
						String userId = userIDs.next();
						if (containsSigningKey(userId, keySpec)) {
							return key;
						}
					}
				}
			}
		} catch (FileNotFoundException | NoSuchFileException e) {
			// Ignore and return null
		}
		return null;
	}

	private static PGPPublicKey getPublicKey(KeyBlob blob, byte[] fingerprint)
			throws IOException {
		return ((PublicKeyRingBlob) blob).getPGPPublicKeyRing()
				.getPublicKey(fingerprint);
	}

	private static PGPPublicKey getSigningPublicKey(KeyBlob blob)
			throws IOException {
		PGPPublicKey masterKey = null;
		Iterator<PGPPublicKey> keys = ((PublicKeyRingBlob) blob)
				.getPGPPublicKeyRing().getPublicKeys();
		while (keys.hasNext()) {
			PGPPublicKey key = keys.next();
			// only consider keys that have the [S] usage flag set
			if (isSigningKey(key)) {
				if (key.isMasterKey()) {
					masterKey = key;
				} else {
					return key;
				}
			}
		}
		// return the master key if no other signing key was found or null if
		// the master key did not have the signing flag set
		return masterKey;
	}

	private static boolean isSigningKey(PGPPublicKey key) {
		Iterator signatures = key.getSignatures();
		while (signatures.hasNext()) {
			PGPSignature sig = (PGPSignature) signatures.next();
			if ((sig.getHashedSubPackets().getKeyFlags()
					& PGPKeyFlags.CAN_SIGN) > 0) {
				return true;
			}
		}
		return false;
	}

	private static KeyBox readKeyBoxFile(Path keyboxFile) throws IOException,
			NoSuchAlgorithmException, NoSuchProviderException,
			NoOpenPgpKeyException {
		if (keyboxFile.toFile().length() == 0) {
			throw new NoOpenPgpKeyException();
		}
		KeyBox keyBox;
		try (InputStream in = new BufferedInputStream(
				newInputStream(keyboxFile))) {
			keyBox = new JcaKeyBoxBuilder().build(in);
		}
		return keyBox;
	}
}
