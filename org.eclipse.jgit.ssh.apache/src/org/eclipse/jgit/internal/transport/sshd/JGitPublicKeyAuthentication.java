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

import static java.text.MessageFormat.format;
import static org.eclipse.jgit.transport.SshConstants.PUBKEY_ACCEPTED_ALGORITHMS;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.sshd.client.auth.pubkey.KeyAgentIdentity;
import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.signature.Signature;
import org.eclipse.jgit.util.StringUtils;

/**
 * Custom {@link UserAuthPublicKey} implementation for handling SSH config
 * PubkeyAcceptedAlgorithms.
 */
public class JGitPublicKeyAuthentication extends UserAuthPublicKey {

	JGitPublicKeyAuthentication(List<NamedFactory<Signature>> factories) {
		super(factories);
	}

	@Override
	public void init(ClientSession rawSession, String service)
			throws Exception {
		if (!(rawSession instanceof JGitClientSession)) {
			throw new IllegalStateException("Wrong session type: " //$NON-NLS-1$
					+ rawSession.getClass().getCanonicalName());
		}
		JGitClientSession session = (JGitClientSession) rawSession;
		HostConfigEntry hostConfig = session.getHostConfigEntry();
		// Set signature algorithms for public key authentication
		String pubkeyAlgos = hostConfig.getProperty(PUBKEY_ACCEPTED_ALGORITHMS);
		if (!StringUtils.isEmptyOrNull(pubkeyAlgos)) {
			List<String> signatures = session.getSignatureFactoriesNames();
			signatures = session.modifyAlgorithmList(signatures,
					session.getAllAvailableSignatureAlgorithms(), pubkeyAlgos,
					PUBKEY_ACCEPTED_ALGORITHMS);
			if (!signatures.isEmpty()) {
				if (log.isDebugEnabled()) {
					log.debug(PUBKEY_ACCEPTED_ALGORITHMS + ' ' + signatures);
				}
				setSignatureFactoriesNames(signatures);
			} else {
				log.warn(format(SshdText.get().configNoKnownAlgorithms,
						PUBKEY_ACCEPTED_ALGORITHMS, pubkeyAlgos));
			}
		}
		// If we don't set signature factories here, the default ones from the
		// session will be used.
		super.init(session, service);
		// In sshd 2.7.0, we end up now with a key iterator that uses keys
		// provided by an ssh-agent even if IdentitiesOnly is true. So if
		// needed, filter out any KeyAgentIdentity.
		if (hostConfig.isIdentitiesOnly()) {
			Iterator<PublicKeyIdentity> original = keys;
			// The original iterator will already have gotten the identities
			// from the agent. Unfortunately there's nothing we can do about
			// that; it'll have to be fixed upstream. (As will, ultimately,
			// respecting isIdentitiesOnly().) At least we can simply not
			// use the keys the agent provided.
			//
			// See https://issues.apache.org/jira/browse/SSHD-1218
			keys = new Iterator<>() {

				private PublicKeyIdentity value;

				@Override
				public boolean hasNext() {
					if (value != null) {
						return true;
					}
					PublicKeyIdentity next = null;
					while (original.hasNext()) {
						next = original.next();
						if (!(next instanceof KeyAgentIdentity)) {
							value = next;
							return true;
						}
					}
					return false;
				}

				@Override
				public PublicKeyIdentity next() {
					if (hasNext()) {
						PublicKeyIdentity result = value;
						value = null;
						return result;
					}
					throw new NoSuchElementException();
				}
			};
		}
	}

	@Override
	protected PublicKeyIdentity resolveAttemptedPublicKeyIdentity(
			ClientSession session, String service) throws Exception {
		PublicKeyIdentity result = super.resolveAttemptedPublicKeyIdentity(
				session, service);
		// This fixes SSHD-1231. Can be removed once we're using Apache MINA
		// sshd > 2.8.0.
		//
		// See https://issues.apache.org/jira/browse/SSHD-1231
		currentAlgorithms.clear();
		return result;
	}

}
