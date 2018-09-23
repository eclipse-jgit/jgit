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

import java.io.IOException;
import java.nio.channels.Channel;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.client.auth.pubkey.AbstractKeyPairIterator;
import org.apache.sshd.client.auth.pubkey.KeyAgentIdentity;
import org.apache.sshd.client.auth.pubkey.KeyPairIdentity;
import org.apache.sshd.client.auth.pubkey.PublicKeyIdentity;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.signature.SignatureFactoriesManager;

/**
 * A new iterator over key pairs that we use instead of the default
 * {@link org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyIterator}, which
 * in its constructor does some strange {@link java.util.stream.Stream} "magic"
 * that ends up loading keys prematurely. This class uses plain
 * {@link Iterator}s instead to avoid that problem. Used in
 * {@link JGitPublicKeyAuthentication}.
 *
 * @see <a href=
 *      "https://issues.apache.org/jira/projects/SSHD/issues/SSHD-860">Upstream
 *      issue SSHD-860</a>
 */
public class JGitPublicKeyIterator
		extends AbstractKeyPairIterator<PublicKeyIdentity> implements Channel {

	// Re: the cause for the problem mentioned above has not been determined.
	// It looks as if either the Apache code inadvertently calls
	// GenericUtils.isEmpty() on all streams (which would load the first key
	// of each stream), or the Java stream implementation does some prefetching.
	// It's not entirely clear. Using Iterators we have more control over
	// what happens when.

	private final AtomicBoolean open = new AtomicBoolean(true);

	private SshAgent agent;

	private final List<Iterator<PublicKeyIdentity>> keys = new ArrayList<>(3);

	private final Iterator<Iterator<PublicKeyIdentity>> keyIter;

	private Iterator<PublicKeyIdentity> current;

	private Boolean hasElement;

	/**
	 * Creates a new {@link JGitPublicKeyIterator}.
	 *
	 * @param session
	 *            we're trying to authenticate
	 * @param signatureFactories
	 *            to use
	 * @throws Exception
	 *             if an {@link SshAgentFactory} is configured and getting
	 *             identities from the agent fails
	 */
	public JGitPublicKeyIterator(ClientSession session,
			SignatureFactoriesManager signatureFactories) throws Exception {
		super(session);
		boolean useAgent = true;
		if (session instanceof JGitClientSession) {
			HostConfigEntry config = ((JGitClientSession) session)
					.getHostConfigEntry();
			useAgent = !config.isIdentitiesOnly();
		}
		if (useAgent) {
			FactoryManager manager = session.getFactoryManager();
			SshAgentFactory factory = manager == null ? null
					: manager.getAgentFactory();
			if (factory != null) {
				try {
					agent = factory.createClient(manager);
					keys.add(new AgentIdentityIterator(agent));
				} catch (IOException e) {
					try {
						closeAgent();
					} catch (IOException err) {
						e.addSuppressed(err);
					}
					throw e;
				}
			}
		}
		keys.add(
				new KeyPairIdentityIterator(session.getRegisteredIdentities(),
						session, signatureFactories));
		keys.add(new KeyPairIdentityIterator(session.getKeyPairProvider(),
				session, signatureFactories));
		keyIter = keys.iterator();
	}

	@Override
	public boolean isOpen() {
		return open.get();
	}

	@Override
	public void close() throws IOException {
		if (open.getAndSet(false)) {
			closeAgent();
		}
	}

	@Override
	public boolean hasNext() {
		if (!isOpen()) {
			return false;
		}
		if (hasElement != null) {
			return hasElement.booleanValue();
		}
		while (current == null || !current.hasNext()) {
			if (keyIter.hasNext()) {
				current = keyIter.next();
			} else {
				current = null;
				hasElement = Boolean.FALSE;
				return false;
			}
		}
		hasElement = Boolean.TRUE;
		return true;
	}

	@Override
	public PublicKeyIdentity next() {
		if (!isOpen() || hasElement == null && !hasNext()
				|| !hasElement.booleanValue()) {
			throw new NoSuchElementException();
		}
		hasElement = null;
		PublicKeyIdentity result;
		try {
			result = current.next();
		} catch (NoSuchElementException e) {
			result = null;
		}
		return result;
	}

	private void closeAgent() throws IOException {
		if (agent == null) {
			return;
		}
		try {
			agent.close();
		} finally {
			agent = null;
		}
	}

	/**
	 * An {@link Iterator} that maps the data obtained from an agent to
	 * {@link PublicKeyIdentity}.
	 */
	private static class AgentIdentityIterator
			implements Iterator<PublicKeyIdentity> {

		private final SshAgent agent;

		private final Iterator<? extends Map.Entry<PublicKey, String>> iter;

		public AgentIdentityIterator(SshAgent agent) throws IOException {
			this.agent = agent;
			iter = agent == null ? null : agent.getIdentities().iterator();
		}

		@Override
		public boolean hasNext() {
			return iter != null && iter.hasNext();
		}

		@Override
		public PublicKeyIdentity next() {
			if (iter == null) {
				throw new NoSuchElementException();
			}
			Map.Entry<PublicKey, String> entry = iter.next();
			return new KeyAgentIdentity(agent, entry.getKey(),
					entry.getValue());
		}
	}

	/**
	 * An {@link Iterator} that maps {@link KeyPair} to
	 * {@link PublicKeyIdentity}.
	 */
	private static class KeyPairIdentityIterator
			implements Iterator<PublicKeyIdentity> {

		private final Iterator<KeyPair> keyPairs;

		private final ClientSession session;

		private final SignatureFactoriesManager signatureFactories;

		public KeyPairIdentityIterator(KeyIdentityProvider provider,
				ClientSession session,
				SignatureFactoriesManager signatureFactories) {
			this.session = session;
			this.signatureFactories = signatureFactories;
			keyPairs = provider == null ? null : provider.loadKeys().iterator();
		}

		@Override
		public boolean hasNext() {
			return keyPairs != null && keyPairs.hasNext();
		}

		@Override
		public PublicKeyIdentity next() {
			if (keyPairs == null) {
				throw new NoSuchElementException();
			}
			KeyPair key = keyPairs.next();
			return new KeyPairIdentity(signatureFactories, session, key);
		}
	}
}
