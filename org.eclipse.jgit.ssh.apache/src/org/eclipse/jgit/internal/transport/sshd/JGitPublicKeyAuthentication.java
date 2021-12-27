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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.client.auth.pubkey.KeyAgentIdentity;
import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKey;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyIterator;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.signature.SignatureFactoriesManager;
import org.eclipse.jgit.util.StringUtils;

/**
 * Custom {@link UserAuthPublicKey} implementation for handling SSH config
 * PubkeyAcceptedAlgorithms and interaction with the SSH agent.
 */
public class JGitPublicKeyAuthentication extends UserAuthPublicKey {

	private SshAgent agent;

	private HostConfigEntry hostConfig;

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
		hostConfig = session.getHostConfigEntry();
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

	@Override
	protected Iterator<PublicKeyIdentity> createPublicKeyIterator(
			ClientSession session, SignatureFactoriesManager manager)
			throws Exception {
		agent = getAgent(session);
		return new KeyIterator(session, manager);
	}

	private SshAgent getAgent(ClientSession session) throws Exception {
		FactoryManager manager = Objects.requireNonNull(
				session.getFactoryManager(), "No session factory manager"); //$NON-NLS-1$
		SshAgentFactory factory = manager.getAgentFactory();
		if (factory == null) {
			return null;
		}
		return factory.createClient(session, manager);
	}

	@Override
	protected void releaseKeys() throws IOException {
		try {
			if (agent != null) {
				try {
					agent.close();
				} finally {
					agent = null;
				}
			}
		} finally {
			super.releaseKeys();
		}
	}

	private class KeyIterator extends UserAuthPublicKeyIterator {

		private Iterable<? extends Map.Entry<PublicKey, String>> agentKeys;

		// If non-null, all the public keys from explicitly given key files. Any
		// agent key not matching one of these public keys will be ignored in
		// getIdentities().
		private Collection<PublicKey> identityFiles;

		public KeyIterator(ClientSession session,
				SignatureFactoriesManager manager)
				throws Exception {
			super(session, manager);
		}

		private List<PublicKey> getExplicitKeys(
				Collection<String> explicitFiles) {
			if (explicitFiles == null) {
				return null;
			}
			return explicitFiles.stream().map(s -> {
				try {
					Path p = Paths.get(s + ".pub"); //$NON-NLS-1$
					if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
						return AuthorizedKeyEntry.readAuthorizedKeys(p).get(0)
								.resolvePublicKey(null,
										PublicKeyEntryResolver.IGNORING);
					}
				} catch (InvalidPathException | IOException
						| GeneralSecurityException e) {
					log.warn(format(SshdText.get().cannotReadPublicKey, s), e);
				}
				return null;
			}).filter(Objects::nonNull).collect(Collectors.toList());
		}

		@Override
		protected Iterable<KeyAgentIdentity> initializeAgentIdentities(
				ClientSession session) throws IOException {
			if (agent == null) {
				return null;
			}
			agentKeys = agent.getIdentities();
			if (hostConfig != null && hostConfig.isIdentitiesOnly()) {
				identityFiles = getExplicitKeys(hostConfig.getIdentities());
			}
			return () -> new Iterator<>() {

				private final Iterator<? extends Map.Entry<PublicKey, String>> iter = agentKeys
						.iterator();

				private Map.Entry<PublicKey, String> next;

				@Override
				public boolean hasNext() {
					while (next == null && iter.hasNext()) {
						Map.Entry<PublicKey, String> val = iter.next();
						PublicKey pk = val.getKey();
						// This checks against all explicit keys for any agent
						// key, but since identityFiles.size() is typically 1,
						// it should be fine.
						if (identityFiles == null || identityFiles.stream()
								.anyMatch(k -> KeyUtils.compareKeys(k, pk))) {
							next = val;
							return true;
						}
						if (log.isTraceEnabled()) {
							log.trace(
									"Ignoring SSH agent {} key not in explicit IdentityFile in SSH config: {}", //$NON-NLS-1$
									KeyUtils.getKeyType(pk),
									KeyUtils.getFingerPrint(pk));
						}
					}
					return next != null;
				}

				@Override
				public KeyAgentIdentity next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					}
					KeyAgentIdentity result = new KeyAgentIdentity(agent,
							next.getKey(), next.getValue());
					next = null;
					return result;
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
