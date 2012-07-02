/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Holds a collection of {@link PublisherPack}s, and the functionality for
 * creating new packs.
 */
public class PublisherPush implements Closeable {
	private class Generator {
		private PublisherPack pack;

		private final Repository db;

		private final Collection<ReceiveCommand> updates;

		private final Set<SubscribeSpec> specs;

		private final String packId;

		private Generator(Repository db, Collection<ReceiveCommand> updates,
				Collection<SubscribeSpec> specs, String packId) {
			this.db = db;
			this.updates = updates;
			this.specs = new HashSet<SubscribeSpec>(specs);
			this.packId = packId;
			db.incrementOpen();
		}

		private synchronized PublisherPack get() throws IOException {
			if (pack != null)
				return pack;
			pack = factory.buildPack(db, updates, specs, packId);
			db.close();
			return pack;
		}

		/**
		 * @param subscribeSpecs
		 * @return true if all of this pack's refs match {@code subscribeSpecs}.
		 */
		private boolean match(Set<SubscribeSpec> subscribeSpecs) {
			return specs.equals(subscribeSpecs);
		}

		private void close() throws PublisherException {
			pack.close();
		}
	}

	private final PublisherPackFactory factory;

	private final String repositoryName;

	private final String pushId;

	private final List<Generator> packs;

	private final Collection<ReceiveCommand> commands;

	private volatile boolean closed;

	/**
	 * @param name
	 * @param commands
	 * @param id
	 * @param factory
	 */
	public PublisherPush(String name, Collection<ReceiveCommand> commands,
			String id, PublisherPackFactory factory) {
		repositoryName = name;
		pushId = id;
		packs = new ArrayList<Generator>();
		this.commands = commands;
		this.factory = factory;
	}

	/**
	 * @param client
	 * @return the pack to be streamed to this session, generated on the fly if
	 *         necessary.
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	public PublisherPack get(PublisherClient client) throws IOException,
			ServiceNotAuthorizedException, ServiceNotEnabledException {
		if (closed)
			throw new IOException("Push already closed");
		PublisherSession session = client.getSession();
		Set<SubscribeSpec> specs = session.getSubscriptions(repositoryName);
		if (specs.size() == 0)
			return null;
		// Each client must open the repository for every push to check
		// permissions.
		Repository r = client.openRepository(repositoryName);
		try {
			Generator packGenerator = null;
			synchronized (this) {
				for (Generator p : packs) {
					if (p.match(specs))
						packGenerator = p;
				}
				if (packGenerator == null) {
					// Filter the full list of updates by the given subscribe
					// specs
					Set<ReceiveCommand> matchedUpdates = new HashSet<
							ReceiveCommand>();
					for (ReceiveCommand rc : commands) {
						for (SubscribeSpec f : specs) {
							if (f.isMatch(rc.getRefName())) {
								matchedUpdates.add(rc);
								break;
							}
						}
					}
					if (matchedUpdates.size() == 0)
						return null;
					packGenerator = new Generator(r,
							matchedUpdates, specs, pushId + "-" + packs.size());
					packs.add(packGenerator);
				}
			}
			return packGenerator.get();
		} finally {
			r.close();
		}
	}

	/**
	 * @throws PublisherException
	 */
	public synchronized void close() throws PublisherException {
		if (closed)
			return;
		for (Generator p : packs)
			p.close();
		closed = true;
	}

	/**
	 * @return the name of this repository
	 */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * @return the id of this push
	 */
	public String getPushId() {
		return pushId;
	}

	@Override
	public String toString() {
		return "PublisherPush[id=" + pushId + ", " + commands + "]";
	}
}
