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

import java.util.List;

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
		JGitClientSession session = ((JGitClientSession) rawSession);
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
	}
}
