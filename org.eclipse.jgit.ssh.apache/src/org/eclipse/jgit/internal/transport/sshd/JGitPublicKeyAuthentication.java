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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
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

	private final List<String> algorithms = new LinkedList<>();

	JGitPublicKeyAuthentication(List<NamedFactory<Signature>> factories) {
		super(factories);
	}

	@Override
	protected boolean sendAuthDataRequest(ClientSession session, String service)
			throws Exception {
		if (current == null) {
			algorithms.clear();
		}
		String currentAlgorithm = null;
		if (current != null && !algorithms.isEmpty()) {
			currentAlgorithm = algorithms.remove(0);
		}
		if (currentAlgorithm == null) {
			try {
				if (keys == null || !keys.hasNext()) {
					if (log.isDebugEnabled()) {
						log.debug(
								"sendAuthDataRequest({})[{}] no more keys to send", //$NON-NLS-1$
								session, service);
					}
					return false;
				}
				current = keys.next();
				algorithms.clear();
			} catch (Error e) { // Copied from superclass
				warn("sendAuthDataRequest({})[{}] failed ({}) to get next key: {}", //$NON-NLS-1$
						session, service, e.getClass().getSimpleName(),
						e.getMessage(), e);
				throw new RuntimeSshException(e);
			}
		}
		PublicKey key;
		try {
			key = current.getPublicKey();
		} catch (Error e) { // Copied from superclass
			warn("sendAuthDataRequest({})[{}] failed ({}) to retrieve public key: {}", //$NON-NLS-1$
					session, service, e.getClass().getSimpleName(),
					e.getMessage(), e);
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
				// Select the factories by name and in order
				existingFactories.forEach(f -> {
					if (aliases.contains(f.getName())) {
						algorithms.add(f.getName());
					}
				});
			}
			currentAlgorithm = algorithms.isEmpty() ? keyType
					: algorithms.remove(0);
		}
		String name = getName();
		if (log.isDebugEnabled()) {
			log.debug(
					"sendAuthDataRequest({})[{}] send SSH_MSG_USERAUTH_REQUEST request {} type={} - fingerprint={}", //$NON-NLS-1$
					session, service, name, currentAlgorithm,
					KeyUtils.getFingerPrint(key));
		}

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
	protected void releaseKeys() throws IOException {
		algorithms.clear();
		current = null;
		super.releaseKeys();
	}
}
