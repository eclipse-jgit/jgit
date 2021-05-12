/*
 * Copyright (C) 2018, 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.signature.SignatureFactoriesHolder;
import org.apache.sshd.common.util.buffer.Buffer;

/**
 * Custom {@link UserAuthPublicKey} implementation fixing SSHD-1105: if there
 * are several signature algorithms applicable for a public key type, we must
 * try them all, in the correct order.
 *
 * @see <a href="https://issues.apache.org/jira/browse/SSHD-1105">SSHD-1105</a>
 * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=572056">Bug
 *      572056</a>
 */
public class JGitPublicKeyAuthentication extends UserAuthPublicKey {

	private final Deque<String> currentAlgorithms = new LinkedList<>();

	private String chosenAlgorithm;

	JGitPublicKeyAuthentication(List<NamedFactory<Signature>> factories) {
		super(factories);
	}

	@Override
	protected boolean sendAuthDataRequest(ClientSession session, String service)
			throws Exception {
		if (current == null) {
			currentAlgorithms.clear();
			chosenAlgorithm = null;
		}
		String currentAlgorithm = null;
		if (current != null && !currentAlgorithms.isEmpty()) {
			currentAlgorithm = currentAlgorithms.poll();
			if (chosenAlgorithm != null) {
				Set<String> knownServerAlgorithms = session.getAttribute(
						JGitKexExtensionHandler.SERVER_ALGORITHMS);
				if (knownServerAlgorithms != null
						&& knownServerAlgorithms.contains(chosenAlgorithm)) {
					// We've tried key 'current' with 'chosenAlgorithm', but it
					// failed. However, the server had told us it supported
					// 'chosenAlgorithm'. Thus it makes no sense to continue
					// with this key and other signature algorithms. Skip to the
					// next key, if any.
					currentAlgorithm = null;
				}
			}
		}
		if (currentAlgorithm == null) {
			try {
				if (keys == null || !keys.hasNext()) {
					if (log.isDebugEnabled()) {
						log.debug(
								"sendAuthDataRequest({})[{}] no more keys to send", //$NON-NLS-1$
								session, service);
					}
					current = null;
					return false;
				}
				current = keys.next();
				currentAlgorithms.clear();
				chosenAlgorithm = null;
			} catch (Error e) { // Copied from superclass
				throw new RuntimeSshException(e);
			}
		}
		PublicKey key;
		try {
			key = current.getPublicKey();
		} catch (Error e) { // Copied from superclass
			throw new RuntimeSshException(e);
		}
		if (currentAlgorithm == null) {
			String keyType = KeyUtils.getKeyType(key);
			Set<String> aliases = new HashSet<>(
					KeyUtils.getAllEquivalentKeyTypes(keyType));
			aliases.add(keyType);
			List<NamedFactory<Signature>> existingFactories;
			if (current instanceof SignatureFactoriesHolder) {
				existingFactories = ((SignatureFactoriesHolder) current)
						.getSignatureFactories();
			} else {
				existingFactories = getSignatureFactories();
			}
			if (existingFactories != null) {
				if (log.isDebugEnabled()) {
					log.debug(
							"sendAuthDataRequest({})[{}] selecting from PubKeyAcceptedAlgorithms {}", //$NON-NLS-1$
							session, service,
							NamedResource.getNames(existingFactories));
				}
				// Select the factories by name and in order
				existingFactories.forEach(f -> {
					if (aliases.contains(f.getName())) {
						currentAlgorithms.add(f.getName());
					}
				});
			}
			currentAlgorithm = currentAlgorithms.isEmpty() ? keyType
					: currentAlgorithms.poll();
		}
		String name = getName();
		if (log.isDebugEnabled()) {
			log.debug(
					"sendAuthDataRequest({})[{}] send SSH_MSG_USERAUTH_REQUEST request {} type={} - fingerprint={}", //$NON-NLS-1$
					session, service, name, currentAlgorithm,
					KeyUtils.getFingerPrint(key));
		}

		chosenAlgorithm = currentAlgorithm;
		Buffer buffer = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		buffer.putString(session.getUsername());
		buffer.putString(service);
		buffer.putString(name);
		buffer.putBoolean(false);
		buffer.putString(currentAlgorithm);
		buffer.putPublicKey(key);
		session.writePacket(buffer);
		return true;
	}

	@Override
	protected boolean processAuthDataRequest(ClientSession session,
			String service, Buffer buffer) throws Exception {
		String name = getName();
		int cmd = buffer.getUByte();
		if (cmd != SshConstants.SSH_MSG_USERAUTH_PK_OK) {
			throw new IllegalStateException(MessageFormat.format(
					SshdText.get().pubkeyAuthWrongCommand,
					SshConstants.getCommandMessageName(cmd),
					session.getConnectAddress(), session.getServerVersion()));
		}
		PublicKey key;
		try {
			key = current.getPublicKey();
		} catch (Error e) { // Copied from superclass
			throw new RuntimeSshException(e);
		}
		String rspKeyAlgorithm = buffer.getString();
		PublicKey rspKey = buffer.getPublicKey();
		if (log.isDebugEnabled()) {
			log.debug(
					"processAuthDataRequest({})[{}][{}] SSH_MSG_USERAUTH_PK_OK type={}, fingerprint={}", //$NON-NLS-1$
					session, service, name, rspKeyAlgorithm,
					KeyUtils.getFingerPrint(rspKey));
		}
		if (!KeyUtils.compareKeys(rspKey, key)) {
			throw new InvalidKeySpecException(MessageFormat.format(
					SshdText.get().pubkeyAuthWrongKey,
					KeyUtils.getFingerPrint(key),
					KeyUtils.getFingerPrint(rspKey),
					session.getConnectAddress(), session.getServerVersion()));
		}
		if (!chosenAlgorithm.equalsIgnoreCase(rspKeyAlgorithm)) {
			// 'algo' SHOULD be the same as 'chosenAlgorithm', which is the one
			// we sent above. See https://tools.ietf.org/html/rfc4252#page-9 .
			//
			// However, at least Github (SSH-2.0-babeld-383743ad) servers seem
			// to return the key type, not the algorithm name.
			//
			// So we don't check but just log the inconsistency. We sign using
			// 'chosenAlgorithm' in any case, so we don't really care what the
			// server says here.
			log.warn(MessageFormat.format(
					SshdText.get().pubkeyAuthWrongSignatureAlgorithm,
					chosenAlgorithm, rspKeyAlgorithm, session.getConnectAddress(),
					session.getServerVersion()));
		}
		String username = session.getUsername();
		Buffer out = session
				.createBuffer(SshConstants.SSH_MSG_USERAUTH_REQUEST);
		out.putString(username);
		out.putString(service);
		out.putString(name);
		out.putBoolean(true);
		out.putString(chosenAlgorithm);
		out.putPublicKey(key);
		if (log.isDebugEnabled()) {
			log.debug(
					"processAuthDataRequest({})[{}][{}]: signing with algorithm {}", //$NON-NLS-1$
					session, service, name, chosenAlgorithm);
		}
		appendSignature(session, service, name, username, chosenAlgorithm, key,
				out);
		session.writePacket(out);
		return true;
	}

	@Override
	protected void releaseKeys() throws IOException {
		currentAlgorithms.clear();
		current = null;
		chosenAlgorithm = null;
		super.releaseKeys();
	}
}
