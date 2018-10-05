/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.internal.transport.sshd;

import static java.text.MessageFormat.format;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier.HostEntryPair;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.io.IoSession;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConstants;

/**
 * A {@link org.apache.sshd.client.session.ClientSession ClientSession} that can
 * be associated with the {@link HostConfigEntry} the session was created for.
 * The {@link JGitSshClient} creates such sessions and sets this association.
 * <p>
 * Also provides for associating a JGit {@link CredentialsProvider} with a
 * session.
 * </p>
 */
public class JGitClientSession extends ClientSessionImpl {

	private HostConfigEntry hostConfig;

	private CredentialsProvider credentialsProvider;

	/**
	 * @param manager
	 * @param session
	 * @throws Exception
	 */
	public JGitClientSession(ClientFactoryManager manager, IoSession session)
			throws Exception {
		super(manager, session);
	}

	/**
	 * Retrieves the {@link HostConfigEntry} this session was created for.
	 *
	 * @return the {@link HostConfigEntry}, or {@code null} if none set
	 */
	public HostConfigEntry getHostConfigEntry() {
		return hostConfig;
	}

	/**
	 * Sets the {@link HostConfigEntry} this session was created for.
	 *
	 * @param hostConfig
	 *            the {@link HostConfigEntry}
	 */
	public void setHostConfigEntry(HostConfigEntry hostConfig) {
		this.hostConfig = hostConfig;
	}

	/**
	 * Sets the {@link CredentialsProvider} for this session.
	 *
	 * @param provider
	 *            to set
	 */
	public void setCredentialsProvider(CredentialsProvider provider) {
		credentialsProvider = provider;
	}

	/**
	 * Retrieves the {@link CredentialsProvider} set for this session.
	 *
	 * @return the provider, or {@code null} if none is set.
	 */
	public CredentialsProvider getCredentialsProvider() {
		return credentialsProvider;
	}

	@Override
	protected String resolveAvailableSignaturesProposal(
			FactoryManager manager) {
		Set<String> defaultSignatures = new LinkedHashSet<>();
		defaultSignatures.addAll(getSignatureFactoriesNames());
		HostConfigEntry config = resolveAttribute(
				JGitSshClient.HOST_CONFIG_ENTRY);
		String hostKeyAlgorithms = config
				.getProperty(SshConstants.HOST_KEY_ALGORITHMS);
		if (hostKeyAlgorithms != null && !hostKeyAlgorithms.isEmpty()) {
			char first = hostKeyAlgorithms.charAt(0);
			if (first == '+') {
				// Additions make not much sense -- it's either in
				// defaultSignatures already, or we have no implementation for
				// it. No point in proposing it.
				return String.join(",", defaultSignatures); //$NON-NLS-1$
			} else if (first == '-') {
				// This takes wildcard patterns!
				removeFromList(defaultSignatures,
						SshConstants.HOST_KEY_ALGORITHMS,
						hostKeyAlgorithms.substring(1));
				if (defaultSignatures.isEmpty()) {
					// Too bad: user config error. Warn here, and then fail
					// later.
					log.warn(format(
							SshdText.get().configNoRemainingHostKeyAlgorithms,
							hostKeyAlgorithms));
				}
				return String.join(",", defaultSignatures); //$NON-NLS-1$
			} else {
				// Default is overridden -- only accept the ones for which we do
				// have an implementation.
				List<String> newNames = filteredList(defaultSignatures,
						hostKeyAlgorithms);
				if (newNames.isEmpty()) {
					log.warn(format(
							SshdText.get().configNoKnownHostKeyAlgorithms,
							hostKeyAlgorithms));
					// Use the default instead.
				} else {
					return String.join(",", newNames); //$NON-NLS-1$
				}
			}
		}
		// No HostKeyAlgorithms; using default -- change order to put existing
		// keys first.
		ServerKeyVerifier verifier = getServerKeyVerifier();
		if (verifier instanceof ServerKeyLookup) {
			List<HostEntryPair> allKnownKeys = ((ServerKeyLookup) verifier)
					.lookup(this, this.getIoSession().getRemoteAddress());
			Set<String> reordered = new LinkedHashSet<>();
			for (HostEntryPair h : allKnownKeys) {
				PublicKey key = h.getServerKey();
				if (key != null) {
					String keyType = KeyUtils.getKeyType(key);
					if (keyType != null) {
						reordered.add(keyType);
					}
				}
			}
			reordered.addAll(defaultSignatures);
			return String.join(",", reordered); //$NON-NLS-1$
		}
		return String.join(",", defaultSignatures); //$NON-NLS-1$
	}

	private void removeFromList(Set<String> current, String key,
			String patterns) {
		for (String toRemove : patterns.split("\\s*,\\s*")) { //$NON-NLS-1$
			if (toRemove.indexOf('*') < 0 && toRemove.indexOf('?') < 0) {
				current.remove(toRemove);
				continue;
			}
			try {
				FileNameMatcher matcher = new FileNameMatcher(toRemove, null);
				for (Iterator<String> i = current.iterator(); i.hasNext();) {
					matcher.reset();
					matcher.append(i.next());
					if (matcher.isMatch()) {
						i.remove();
					}
				}
			} catch (InvalidPatternException e) {
				log.warn(format(SshdText.get().configInvalidPattern, key,
						toRemove));
			}
		}
	}

	private List<String> filteredList(Set<String> known, String values) {
		List<String> newNames = new ArrayList<>();
		for (String newValue : values.split("\\s*,\\s*")) { //$NON-NLS-1$
			if (known.contains(newValue)) {
				newNames.add(newValue);
			}
		}
		return newNames;
	}

}
