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
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentConstants;
import org.apache.sshd.agent.SshAgentKeyConstraint;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.BufferException;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.sshd.agent.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client for an SSH2 agent. This client supports only querying identities and
 * signature requests.
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
	public void addIdentity(KeyPair key, String comment,
			SshAgentKeyConstraint... constraints) throws IOException {
		throw new UnsupportedOperationException();
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
