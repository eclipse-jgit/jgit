/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamCorruptedException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceParser;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.UnsupportedSigningFormatException;
import org.eclipse.jgit.internal.transport.sshd.AuthenticationCanceledException;
import org.eclipse.jgit.internal.transport.sshd.PasswordProviderWrapper;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.internal.transport.sshd.agent.SshAgentClient;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.GpgSignature;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Signer;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.KeyPasswordProviderFactory;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Signer} to create SSH signatures.
 *
 * @see <a href=
 *      "https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.sshsig">PROTOCOL.sshsig</a>
 */
public class SshSigner implements Signer {

	private static final Logger LOG = LoggerFactory.getLogger(SshSigner.class);

	private static final String GIT_KEY_PREFIX = "key::"; //$NON-NLS-1$

	// Base64 encoded lines should not be longer than 75 characters, plus the
	// newline.
	private static final int LINE_LENGTH = 75;

	@Override
	public GpgSignature sign(Repository repository, GpgConfig config,
			byte[] data, PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider) throws CanceledException,
			IOException, UnsupportedSigningFormatException {
		byte[] hash;
		try {
			hash = MessageDigest.getInstance("SHA512").digest(data); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			throw new UnsupportedSigningFormatException(
					MessageFormat.format(
							SshdText.get().signUnknownHashAlgorithm, "SHA512"), //$NON-NLS-1$
					e);
		}
		Buffer toSign = new ByteArrayBuffer();
		toSign.putRawBytes(SshSignatureConstants.MAGIC);
		toSign.putString(SshSignatureConstants.NAMESPACE);
		toSign.putUInt(0); // reserved: zero-length string
		toSign.putString("sha512"); //$NON-NLS-1$
		toSign.putBytes(hash);
		String key = signingKey;
		if (StringUtils.isEmptyOrNull(key)) {
			key = config.getSigningKey();
		}
		if (StringUtils.isEmptyOrNull(key)) {
			key = defaultKeyCommand(repository, config);
			// According to documentation, this is supposed to return a
			// valid SSH public key prefixed with "key::". We don't enforce
			// this: there might be older command implementations (like just
			// calling "ssh-add -L") that return keys without prefix.
		}
		PublicKeyIdentity identity;
		try {
			identity = getIdentity(key, committer, credentialsProvider);
		} catch (GeneralSecurityException e) {
			throw new UnsupportedSigningFormatException(MessageFormat
					.format(SshdText.get().signPublicKeyError, key), e);
		}
		String algorithm = KeyUtils
				.getKeyType(identity.getKeyIdentity().getPublic());
		switch (algorithm) {
		case KeyPairProvider.SSH_DSS:
		case KeyPairProvider.SSH_DSS_CERT:
			throw new UnsupportedSigningFormatException(
					SshdText.get().signInvalidKeyDSA);
		case KeyPairProvider.SSH_RSA:
			algorithm = KeyUtils.RSA_SHA512_KEY_TYPE_ALIAS;
			break;
		case KeyPairProvider.SSH_RSA_CERT:
			algorithm = KeyUtils.RSA_SHA512_CERT_TYPE_ALIAS;
			break;
		default:
			break;
		}

		Map.Entry<String, byte[]> rawSignature;
		try {
			rawSignature = identity.sign(null, algorithm,
					toSign.getCompactData());
		} catch (Exception e) {
			throw new UnsupportedSigningFormatException(
					SshdText.get().signSignatureError, e);
		}
		algorithm = rawSignature.getKey();
		Buffer signature = new ByteArrayBuffer();
		signature.putRawBytes(SshSignatureConstants.MAGIC);
		signature.putUInt(SshSignatureConstants.VERSION);
		signature.putPublicKey(identity.getKeyIdentity().getPublic());
		signature.putString(SshSignatureConstants.NAMESPACE);
		signature.putUInt(0); // reserved: zero-length string
		signature.putString("sha512"); //$NON-NLS-1$
		Buffer sig = new ByteArrayBuffer();
		sig.putString(KeyUtils.getSignatureAlgorithm(algorithm,
				identity.getKeyIdentity().getPublic()));
		sig.putBytes(rawSignature.getValue());
		signature.putBytes(sig.getCompactData());
		return armor(signature.getCompactData());
	}

	private static String defaultKeyCommand(@NonNull Repository repository,
			@NonNull GpgConfig config) throws IOException {
		String command = config.getSshDefaultKeyCommand();
		if (StringUtils.isEmptyOrNull(command)) {
			return null;
		}
		FS fileSystem = repository.getFS();
		if (fileSystem == null) {
			fileSystem = FS.DETECTED;
		}
		ProcessBuilder builder = fileSystem.runInShell(command,
				new String[] {});
		ExecutionResult result = null;
		try {
			result = fileSystem.execute(builder, null);
			int exitCode = result.getRc();
			if (exitCode == 0) {
				// The command is supposed to return a public key in its first
				// line on stdout.
				try (BufferedReader r = new BufferedReader(
						new InputStreamReader(
								result.getStdout().openInputStream(),
								SystemReader.getInstance()
										.getDefaultCharset()))) {
					String line = r.readLine();
					if (line != null) {
						line = line.strip();
					}
					if (StringUtils.isEmptyOrNull(line)) {
						throw new IOException(MessageFormat.format(
								SshdText.get().signDefaultKeyEmpty, command));
					}
					return line;
				}
			}
			TemporaryBuffer stderr = result.getStderr();
			throw new IOException(MessageFormat.format(
					SshdText.get().signDefaultKeyFailed, command,
					Integer.toString(exitCode), toString(stderr)));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(
					MessageFormat.format(
							SshdText.get().signDefaultKeyInterrupted, command),
					e);
		} finally {
			if (result != null) {
				if (result.getStderr() != null) {
					result.getStderr().destroy();
				}
				if (result.getStdout() != null) {
					result.getStdout().destroy();
				}
			}
		}
	}

	private static String toString(TemporaryBuffer b) {
		if (b != null) {
			try {
				return new String(b.toByteArray(4000),
						SystemReader.getInstance().getDefaultCharset());
			} catch (IOException e) {
				LOG.warn("{}", SshdText.get().signStderr, e); //$NON-NLS-1$
			}
		}
		return ""; //$NON-NLS-1$
	}

	private static PublicKeyIdentity getIdentity(String signingKey,
			PersonIdent committer, CredentialsProvider credentials)
			throws CanceledException, GeneralSecurityException, IOException {
		if (StringUtils.isEmptyOrNull(signingKey)) {
			throw new IllegalArgumentException(SshdText.get().signNoSigningKey);
		}
		PublicKey publicKey = null;
		PrivateKey privateKey = null;
		File keyFile = null;
		if (signingKey.startsWith(GIT_KEY_PREFIX)) {
			try (StringReader r = new StringReader(
					signingKey.substring(GIT_KEY_PREFIX.length()))) {
				publicKey = fromEntry(
						AuthorizedKeyEntry.readAuthorizedKeys(r, true));
			}
		} else if (signingKey.startsWith("~/") //$NON-NLS-1$
				|| signingKey.startsWith('~' + File.separator)) {
			keyFile = new File(FS.DETECTED.userHome(), signingKey.substring(2));
		} else {
			try (StringReader r = new StringReader(signingKey)) {
				publicKey = fromEntry(
						AuthorizedKeyEntry.readAuthorizedKeys(r, true));
			} catch (IOException e) {
				// Ignore and try to read as a file
				keyFile = new File(signingKey);
			}
		}
		if (keyFile != null && keyFile.isFile()) {
			try {
				publicKey = fromEntry(AuthorizedKeyEntry
						.readAuthorizedKeys(keyFile.toPath()));
				if (publicKey == null) {
					throw new IOException(MessageFormat.format(
							SshdText.get().signTooManyPublicKeys, keyFile));
				}
				// Try to find the private key so we don't go looking for
				// the agent (or PKCS#11) in vain.
				keyFile = getPrivateKeyFile(keyFile.getParentFile(),
						keyFile.getName());
				if (keyFile != null) {
					try {
						KeyPair pair = loadPrivateKey(keyFile.toPath(),
								credentials);
						if (pair != null) {
							PublicKey pk = pair.getPublic();
							if (pk == null) {
								privateKey = pair.getPrivate();
							} else {
								PublicKey original = publicKey;
								if (publicKey instanceof OpenSshCertificate cert) {
									original = cert.getCertPubKey();
								}
								if (KeyUtils.compareKeys(original, pk)) {
									privateKey = pair.getPrivate();
								}
							}
						}
					} catch (IOException e) {
						// Apparently it wasn't a private key file. Ignore.
					}
				}
			} catch (StreamCorruptedException e) {
				// File is readable, but apparently not a public key. Try to
				// load it as a private key.
				KeyPair pair = loadPrivateKey(keyFile.toPath(), credentials);
				if (pair != null) {
					publicKey = pair.getPublic();
					privateKey = pair.getPrivate();
				}
			}
		}
		if (publicKey == null) {
			throw new IOException(MessageFormat
					.format(SshdText.get().signNoPublicKey, signingKey));
		}
		if (publicKey instanceof OpenSshCertificate cert) {
			String message = SshCertificateUtils.verify(cert,
					committer.getWhenAsInstant());
			if (message != null) {
				throw new IOException(message);
			}
		}
		if (privateKey == null) {
			// Could be in the agent, or a PKCS#11 key. The normal procedure
			// with PKCS#11 keys is to put them in the agent and let the agent
			// deal with it.
			//
			// This may or may not work well. For instance, the agent might ask
			// for a passphrase for PKCS#11 keys... also, the OpenSSH ssh-agent
			// had a bug with signing using PKCS#11 certificates in the agent;
			// see https://bugzilla.mindrot.org/show_bug.cgi?id=3613 . If there
			// are troubles, we might do the PKCS#11 dance ourselves, but we'd
			// need additional configuration for the PKCS#11 library. (Plus
			// some refactoring in the Pkcs11Provider.)
			return new AgentIdentity(publicKey);

		}
		return new KeyPairIdentity(new KeyPair(publicKey, privateKey));
	}

	private static File getPrivateKeyFile(File directory,
			String publicKeyName) {
		if (publicKeyName.endsWith(".pub")) { //$NON-NLS-1$
			String privateKeyName = publicKeyName.substring(0,
					publicKeyName.length() - 4);
			if (!privateKeyName.isEmpty()) {
				File keyFile = new File(directory, privateKeyName);
				if (keyFile.isFile()) {
					return keyFile;
				}
				if (privateKeyName.endsWith("-cert")) { //$NON-NLS-1$
					privateKeyName = privateKeyName.substring(0,
							privateKeyName.length() - 5);
					if (!privateKeyName.isEmpty()) {
						keyFile = new File(directory, privateKeyName);
						if (keyFile.isFile()) {
							return keyFile;
						}
					}
				}
			}
		}
		return null;
	}

	private static KeyPair loadPrivateKey(Path path,
			CredentialsProvider credentials)
			throws CanceledException, GeneralSecurityException, IOException {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		KeyPairResourceParser parser = SecurityUtils.getKeyPairResourceParser();
		if (parser != null) {
			PasswordProviderWrapper provider = null;
			if (credentials != null) {
				provider = new PasswordProviderWrapper(
						() -> KeyPasswordProviderFactory.getInstance()
								.apply(credentials));
			}
			try {
				Collection<KeyPair> keyPairs = parser.loadKeyPairs(null, path,
						provider);
				if (keyPairs.size() != 1) {
					throw new GeneralSecurityException(MessageFormat.format(
							SshdText.get().signTooManyPrivateKeys, path));
				}
				return keyPairs.iterator().next();
			} catch (AuthenticationCanceledException e) {
				throw new CanceledException(e.getMessage());
			}
		}
		return null;
	}

	private static GpgSignature armor(byte[] data) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			b.write(SshSignatureConstants.ARMOR_HEAD);
			b.write('\n');
			String encoded = Base64.encodeBytes(data);
			int length = encoded.length();
			int column = 0;
			for (int i = 0; i < length; i++) {
				b.write(encoded.charAt(i));
				column++;
				if (column == LINE_LENGTH) {
					b.write('\n');
					column = 0;
				}
			}
			if (column > 0) {
				b.write('\n');
			}
			b.write(SshSignatureConstants.ARMOR_END);
			b.write('\n');
			return new GpgSignature(b.toByteArray());
		}
	}

	private static PublicKey fromEntry(List<AuthorizedKeyEntry> entries)
			throws GeneralSecurityException, IOException {
		if (entries == null || entries.size() != 1) {
			return null;
		}
		return entries.get(0).resolvePublicKey(null,
				PublicKeyEntryResolver.FAILING);
	}

	@Override
	public boolean canLocateSigningKey(Repository repository, GpgConfig config,
			PersonIdent committer, String signingKey,
			CredentialsProvider credentialsProvider) throws CanceledException {
		String key = signingKey;
		if (key == null) {
			key = config.getSigningKey();
		}
		return !(StringUtils.isEmptyOrNull(key)
				&& StringUtils.isEmptyOrNull(config.getSshDefaultKeyCommand()));
	}

	private static class KeyPairIdentity implements PublicKeyIdentity {

		private final @NonNull KeyPair pair;

		KeyPairIdentity(@NonNull KeyPair pair) {
			this.pair = pair;
		}

		@Override
		public KeyPair getKeyIdentity() {
			return pair;
		}

		@Override
		public Entry<String, byte[]> sign(SessionContext session, String algo,
				byte[] data) throws Exception {
			BuiltinSignatures factory = BuiltinSignatures.fromFactoryName(algo);
			if (factory == null || !factory.isSupported()) {
				throw new GeneralSecurityException(MessageFormat.format(
						SshdText.get().signUnknownSignatureAlgorithm, algo));
			}
			Signature signer = factory.create();
			signer.initSigner(null, pair.getPrivate());
			signer.update(null, data);
			return new SimpleImmutableEntry<>(factory.getName(),
					signer.sign(null));
		}
	}

	private static class AgentIdentity extends KeyPairIdentity {

		AgentIdentity(PublicKey publicKey) {
			super(new KeyPair(publicKey, null));
		}

		@Override
		public Entry<String, byte[]> sign(SessionContext session, String algo,
				byte[] data) throws Exception {
			ConnectorFactory factory = ConnectorFactory.getDefault();
			Connector connector = factory == null ? null
					: factory.create("", null); //$NON-NLS-1$
			if (connector == null) {
				throw new IOException(SshdText.get().signNoAgent);
			}
			try (SshAgentClient agent = new SshAgentClient(connector)) {
				return agent.sign(null, getKeyIdentity().getPublic(), algo,
						data);
			}
		}
	}
}
