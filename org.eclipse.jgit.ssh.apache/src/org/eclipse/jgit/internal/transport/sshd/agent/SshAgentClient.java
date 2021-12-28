/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.agent;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.agent.SshAgentKeyConstraint;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.BufferException;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.io.der.DERParser;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for an SSH2 agent. This client supports querying identities,
 * signature requests, and adding keys to an agent (with or without
 * constraints). Removing keys is not supported, and the older SSH1 protocol is
 * not supported.
 *
 * @see <a href="https://tools.ietf.org/html/draft-miller-ssh-agent-04">SSH
 *      Agent Protocol, RFC draft</a>
 */
public class SshAgentClient implements SshAgent {

	private static final Logger LOG = LoggerFactory
			.getLogger(SshAgentClient.class);

	// OpenSSH limit
	private static final int MAX_NUMBER_OF_KEYS = 2048;

	private final AtomicBoolean closed = new AtomicBoolean();

	private final Connector connector;

	/**
	 * Creates a new {@link SshAgentClient} implementing the SSH2 ssh agent
	 * protocol, using the given {@link Connector} to connect to the SSH agent
	 * and to exchange messages.
	 *
	 * @param connector
	 *            {@link Connector} to use
	 */
	public SshAgentClient(Connector connector) {
		this.connector = connector;
	}

	private boolean open(boolean debugging) throws IOException {
		if (closed.get()) {
			if (debugging) {
				LOG.debug("SSH agent connection already closed"); //$NON-NLS-1$
			}
			return false;
		}
		boolean connected;
		try {
			connected = connector != null && connector.connect();
			if (!connected && debugging) {
				LOG.debug("No SSH agent"); //$NON-NLS-1$
			}
		} catch (IOException e) {
			// Agent not running?
			if (debugging) {
				LOG.debug("No SSH agent", e); //$NON-NLS-1$
			}
			throw e;
		}
		return connected;
	}

	@Override
	public void close() throws IOException {
		if (!closed.getAndSet(true) && connector != null) {
			connector.close();
		}
	}

	@Override
	public Iterable<? extends Map.Entry<PublicKey, String>> getIdentities()
			throws IOException {
		boolean debugging = LOG.isDebugEnabled();
		if (!open(debugging)) {
			return Collections.emptyList();
		}
		if (debugging) {
			LOG.debug("Requesting identities from SSH agent"); //$NON-NLS-1$
		}
		try {
			Buffer reply = rpc(
					SshAgentConstants.SSH2_AGENTC_REQUEST_IDENTITIES);
			byte cmd = reply.getByte();
			if (cmd != SshAgentConstants.SSH2_AGENT_IDENTITIES_ANSWER) {
				throw new SshException(MessageFormat.format(
						SshdText.get().sshAgentReplyUnexpected,
						SshAgentConstants.getCommandMessageName(cmd)));
			}
			int numberOfKeys = reply.getInt();
			if (numberOfKeys < 0 || numberOfKeys > MAX_NUMBER_OF_KEYS) {
				throw new SshException(MessageFormat.format(
						SshdText.get().sshAgentWrongNumberOfKeys,
						Integer.toString(numberOfKeys)));
			}
			if (numberOfKeys == 0) {
				if (debugging) {
					LOG.debug("SSH agent has no keys"); //$NON-NLS-1$
				}
				return Collections.emptyList();
			}
			if (debugging) {
				LOG.debug("Got {} key(s) from the SSH agent", //$NON-NLS-1$
						Integer.toString(numberOfKeys));
			}
			boolean tracing = LOG.isTraceEnabled();
			List<Map.Entry<PublicKey, String>> keys = new ArrayList<>(
					numberOfKeys);
			for (int i = 0; i < numberOfKeys; i++) {
				PublicKey key = reply.getPublicKey();
				String comment = reply.getString();
				if (tracing) {
					LOG.trace("Got SSH agent {} key: {} {}", //$NON-NLS-1$
							KeyUtils.getKeyType(key),
							KeyUtils.getFingerPrint(key), comment);
				}
				keys.add(new AbstractMap.SimpleImmutableEntry<>(key, comment));
			}
			return keys;
		} catch (BufferException e) {
			throw new SshException(SshdText.get().sshAgentShortReadBuffer, e);
		}
	}

	@Override
	public Map.Entry<String, byte[]> sign(SessionContext session, PublicKey key,
			String algorithm, byte[] data) throws IOException {
		boolean debugging = LOG.isDebugEnabled();
		String keyType = KeyUtils.getKeyType(key);
		String signatureAlgorithm;
		if (algorithm != null) {
			if (!KeyUtils.getCanonicalKeyType(algorithm).equals(keyType)) {
				throw new IllegalArgumentException(MessageFormat.format(
						SshdText.get().invalidSignatureAlgorithm, algorithm,
						keyType));
			}
			signatureAlgorithm = algorithm;
		} else {
			signatureAlgorithm = keyType;
		}
		if (!open(debugging)) {
			return null;
		}
		int flags = 0;
		switch (signatureAlgorithm) {
		case KeyUtils.RSA_SHA512_KEY_TYPE_ALIAS:
		case KeyUtils.RSA_SHA512_CERT_TYPE_ALIAS:
			flags = 4;
			break;
		case KeyUtils.RSA_SHA256_KEY_TYPE_ALIAS:
		case KeyUtils.RSA_SHA256_CERT_TYPE_ALIAS:
			flags = 2;
			break;
		default:
			break;
		}
		ByteArrayBuffer msg = new ByteArrayBuffer();
		msg.putInt(0);
		msg.putByte(SshAgentConstants.SSH2_AGENTC_SIGN_REQUEST);
		msg.putPublicKey(key);
		msg.putBytes(data);
		msg.putInt(flags);
		if (debugging) {
			LOG.debug(
					"sign({}): signing request to SSH agent for {} key, {} signature; flags={}", //$NON-NLS-1$
					session, keyType, signatureAlgorithm,
					Integer.toString(flags));
		}
		Buffer reply = rpc(SshAgentConstants.SSH2_AGENTC_SIGN_REQUEST,
				msg.getCompactData());
		byte cmd = reply.getByte();
		if (cmd != SshAgentConstants.SSH2_AGENT_SIGN_RESPONSE) {
			throw new SshException(
					MessageFormat.format(SshdText.get().sshAgentReplyUnexpected,
							SshAgentConstants.getCommandMessageName(cmd)));
		}
		try {
			Buffer signatureReply = new ByteArrayBuffer(reply.getBytes());
			String actualAlgorithm = signatureReply.getString();
			byte[] signature = signatureReply.getBytes();
			if (LOG.isTraceEnabled()) {
				LOG.trace(
						"sign({}): signature reply from SSH agent for {} key: {} signature={}", //$NON-NLS-1$
						session, keyType, actualAlgorithm,
						BufferUtils.toHex(':', signature));

			} else if (LOG.isDebugEnabled()) {
				LOG.debug(
						"sign({}): signature reply from SSH agent for {} key, {} signature", //$NON-NLS-1$
						session, keyType, actualAlgorithm);
			}
			return new AbstractMap.SimpleImmutableEntry<>(actualAlgorithm,
					signature);
		} catch (BufferException e) {
			throw new SshException(SshdText.get().sshAgentShortReadBuffer, e);
		}
	}

	@Override
	public void addIdentity(KeyPair key, String comment,
			SshAgentKeyConstraint... constraints) throws IOException {
		boolean debugging = LOG.isDebugEnabled();
		if (!open(debugging)) {
			return;
		}

		// Neither Pageant 0.76 nor Win32-OpenSSH 8.6 support command
		// SSH2_AGENTC_ADD_ID_CONSTRAINED. Adding a key with constraints will
		// fail. The only work-around for users is not to use "confirm" or "time
		// spec" with AddKeysToAgent, and not to use sk-* keys.
		//
		// With a true OpenSSH SSH agent, key constraints work.
		byte cmd = (constraints != null && constraints.length > 0)
				? SshAgentConstants.SSH2_AGENTC_ADD_ID_CONSTRAINED
				: SshAgentConstants.SSH2_AGENTC_ADD_IDENTITY;
		byte[] message = null;
		ByteArrayBuffer msg = new ByteArrayBuffer();
		try {
			msg.putInt(0);
			msg.putByte(cmd);
			String keyType = KeyUtils.getKeyType(key);
			if (KeyPairProvider.SSH_ED25519.equals(keyType)) {
				// Apache MINA sshd 2.8.0 lacks support for writing ed25519
				// private keys to a buffer.
				putEd25519Key(msg, key);
			} else {
				msg.putKeyPair(key);
			}
			msg.putString(comment == null ? "" : comment); //$NON-NLS-1$
			if (constraints != null) {
				for (SshAgentKeyConstraint constraint : constraints) {
					constraint.put(msg);
				}
			}
			if (debugging) {
				LOG.debug(
						"addIdentity: adding {} key {} to SSH agent; comment {}", //$NON-NLS-1$
						keyType, KeyUtils.getFingerPrint(key.getPublic()),
						comment);
			}
			message = msg.getCompactData();
		} finally {
			// The message contains the private key data, so clear intermediary
			// data ASAP.
			msg.clear();
		}
		Buffer reply;
		try {
			reply = rpc(cmd, message);
		} finally {
			Arrays.fill(message, (byte) 0);
		}
		int replyLength = reply.available();
		if (replyLength != 1) {
			throw new SshException(MessageFormat.format(
					SshdText.get().sshAgentReplyUnexpected,
					MessageFormat.format(
							SshdText.get().sshAgentPayloadLengthError,
							Integer.valueOf(1), Integer.valueOf(replyLength))));

		}
		cmd = reply.getByte();
		if (cmd != SshAgentConstants.SSH_AGENT_SUCCESS) {
			throw new SshException(
					MessageFormat.format(SshdText.get().sshAgentReplyUnexpected,
							SshAgentConstants.getCommandMessageName(cmd)));
		}
	}

	/**
	 * Writes an ed25519 {@link KeyPair} to a {@link Buffer}. OpenSSH specifies
	 * that it expects the 32 public key bytes, followed by 64 bytes formed by
	 * concatenating the 32 private key bytes with the 32 public key bytes.
	 *
	 * @param msg
	 *            {@link Buffer} to write to
	 * @param key
	 *            {@link KeyPair} to write
	 * @throws IOException
	 *             if the private key cannot be written
	 */
	private static void putEd25519Key(Buffer msg, KeyPair key)
			throws IOException {
		Buffer tmp = new ByteArrayBuffer(36);
		tmp.putRawPublicKeyBytes(key.getPublic());
		byte[] publicBytes = tmp.getBytes();
		msg.putString(KeyPairProvider.SSH_ED25519);
		msg.putBytes(publicBytes);
		// Next is the concatenation of the 32 byte private key value with the
		// 32 bytes of the public key.
		PrivateKey pk = key.getPrivate();
		String format = pk.getFormat();
		if (!"PKCS#8".equalsIgnoreCase(format)) { //$NON-NLS-1$
			throw new IOException(MessageFormat
					.format(SshdText.get().sshAgentEdDSAFormatError, format));
		}
		byte[] privateBytes = null;
		byte[] encoded = pk.getEncoded();
		try {
			privateBytes = asn1Parse(encoded, 32);
			byte[] combined = Arrays.copyOf(privateBytes, 64);
			Arrays.fill(privateBytes, (byte) 0);
			privateBytes = combined;
			System.arraycopy(publicBytes, 0, privateBytes, 32, 32);
			msg.putBytes(privateBytes);
		} finally {
			if (privateBytes != null) {
				Arrays.fill(privateBytes, (byte) 0);
			}
			Arrays.fill(encoded, (byte) 0);
		}
	}

	/**
	 * Extracts get the 32 private key bytes from an encoded ed25519 private key
	 * by parsing the bytes as ASN.1 according to RFC 5958 (PKCS #8 encoding):
	 *
	 * <pre>
	 * OneAsymmetricKey ::= SEQUENCE {
	 *   version Version,
	 *   privateKeyAlgorithm PrivateKeyAlgorithmIdentifier,
	 *   privateKey PrivateKey,
	 *   ...
	 * }
	 *
	 * Version ::= INTEGER
	 * PrivateKeyAlgorithmIdentifier ::= AlgorithmIdentifier
	 * PrivateKey ::= OCTET STRING
	 *
	 * AlgorithmIdentifier  ::=  SEQUENCE  {
	 *   algorithm   OBJECT IDENTIFIER,
	 *   parameters  ANY DEFINED BY algorithm OPTIONAL
	 * }
	 * </pre>
	 * <p>
	 * and RFC 8410: "... when encoding a OneAsymmetricKey object, the private
	 * key is wrapped in a CurvePrivateKey object and wrapped by the OCTET
	 * STRING of the 'privateKey' field."
	 * </p>
	 *
	 * <pre>
	 * CurvePrivateKey ::= OCTET STRING
	 * </pre>
	 *
	 * @param encoded
	 *            encoded private key to extract the private key bytes from
	 * @param n
	 *            number of bytes expected
	 * @return the extracted private key bytes; of length {@code n}
	 * @throws IOException
	 *             if the private key cannot be extracted
	 * @see <a href="https://tools.ietf.org/html/rfc5958">RFC 5958</a>
	 * @see <a href="https://tools.ietf.org/html/rfc8410">RFC 8410</a>
	 */
	private static byte[] asn1Parse(byte[] encoded, int n) throws IOException {
		byte[] privateKey = null;
		try (DERParser byteParser = new DERParser(encoded);
				DERParser oneAsymmetricKey = byteParser.readObject()
						.createParser()) {
			oneAsymmetricKey.readObject(); // skip version
			oneAsymmetricKey.readObject(); // skip algorithm identifier
			privateKey = oneAsymmetricKey.readObject().getValue();
			// The last n bytes of this must be the private key bytes
			return Arrays.copyOfRange(privateKey,
					privateKey.length - n, privateKey.length);
		} finally {
			if (privateKey != null) {
				Arrays.fill(privateKey, (byte) 0);
			}
		}
	}

	private Buffer rpc(byte command, byte[] message) throws IOException {
		return new ByteArrayBuffer(connector.rpc(command, message));
	}

	private Buffer rpc(byte command) throws IOException {
		return new ByteArrayBuffer(connector.rpc(command));
	}

	@Override
	public boolean isOpen() {
		return !closed.get();
	}

	@Override
	public void removeIdentity(PublicKey key) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeAllIdentities() throws IOException {
		throw new UnsupportedOperationException();
	}
}
