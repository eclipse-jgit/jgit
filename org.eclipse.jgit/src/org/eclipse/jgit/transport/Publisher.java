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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PublisherSession.SessionGenerator;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.ConcurrentLinkedList;
import org.eclipse.jgit.util.ConcurrentLinkedList.ConcurrentIterator;

/**
 * Publishes pack data to multiple Subscribers with matching subscription
 * requests. This publisher handles multiple repositories.
 */
public class Publisher {
	/** Session lifetime after a client disconnects, in seconds. */
	private static final int DEFAULT_SESSION_LIFETIME = 60 * 60;

	/**
	 * Contains acceleration structures to speed up calculation of which clients
	 * the PublisherUpdate needs to be sent to.
	 */
	class PublisherEventProcessor {
		LinkedHashSet<PublisherSession> clients = new LinkedHashSet<PublisherSession>();

		public synchronized void addClient(PublisherSession c) {
			clients.add(c);
		}

		public synchronized void removeClient(PublisherSession c) {
			clients.remove(c);
		}

		/**
		 * Find all matches between client SubscribeSpecs and the update.
		 *
		 * @param name
		 * @param updates
		 * @return a list of all client states that have overlapping refs
		 */
		public synchronized Map<Set<ReceiveCommand>, List<PublisherSession>> getAffectedClients(
				String name, Collection<ReceiveCommand> updates) {
			Map<Set<ReceiveCommand>, List<PublisherSession>> groups = new LinkedHashMap<Set<ReceiveCommand>, List<PublisherSession>>();

			for (PublisherSession c : clients) {
				Collection<SubscribeSpec> clientSpecs = c.getSubscriptions(
						name);
				if (!clientSpecs.isEmpty()) {
					Set<ReceiveCommand> matchedUpdates = new LinkedHashSet<
							ReceiveCommand>();
					for (ReceiveCommand r : updates) {
						for (SubscribeSpec f : clientSpecs) {
							if (f.isMatch(r.getRefName())) {
								matchedUpdates.add(r);
								break;
							}
						}
					}
					// Search for match
					if (matchedUpdates.size() > 0) {
						if (!groups.containsKey(matchedUpdates))
							groups.put(matchedUpdates,
									new ArrayList<PublisherSession>());
						groups.get(matchedUpdates).add(c);
					}
				}
			}
			return groups;
		}
	}

	private final PostReceiveHook postReceiveHook = new PostReceiveHook() {
		public void onPostReceive(
				ReceivePack rp, Collection<ReceiveCommand> commands) {
			List<ReceiveCommand> updates = new ArrayList<ReceiveCommand>();
			for (ReceiveCommand c : commands) {
				if (c.getResult() != ReceiveCommand.Result.OK)
					continue;
				updates.add(c);
			}
			try {
				onPush(rp.db, updates);
			} catch (NotSupportedException e) {
				e.printStackTrace();
			}
		}
	};

	private final PublisherPackFactory packFactory;

	private final PublisherEventProcessor processor;

	private final Map<String, PublisherSession> clientStates;

	private final PublisherReverseResolver repositoryNameLookup;

	private final RepositoryResolver<PublisherClient> resolver;

	private final ConcurrentLinkedList<PublisherPack> packStream;

	private final ScheduledThreadPoolExecutor sessionDeleter;

	private final SessionGenerator generator;

	private int sessionLifetime;

	/**
	 * Create a new Publisher to serve multiple repositories.
	 *
	 * @param repositoryNameLookup
	 * @param resolver
	 *            a RepositoryResolver to open repositories
	 * @param packFactory
	 * @param generator
	 */
	public Publisher(PublisherReverseResolver repositoryNameLookup,
			RepositoryResolver<PublisherClient> resolver,
			PublisherPackFactory packFactory, SessionGenerator generator) {
		processor = new PublisherEventProcessor();
		clientStates = new HashMap<String, PublisherSession>();
		packStream = new ConcurrentLinkedList<PublisherPack>();
		sessionDeleter = new ScheduledThreadPoolExecutor(1);
		sessionLifetime = DEFAULT_SESSION_LIFETIME;
		this.repositoryNameLookup = repositoryNameLookup;
		this.resolver = resolver;
		this.packFactory = packFactory;
		this.generator = generator;
	}

	/**
	 * @param lifetime
	 *            the number of seconds before deleting this session after its
	 *            client disconnects
	 */
	public void setSessionLifetime(int lifetime) {
		sessionLifetime = lifetime;
	}

	/**
	 * @param c
	 * @return the persistent state for this connection, or null if a restart
	 *         token was given but could not be recovered
	 */
	public synchronized PublisherSession connectClient(PublisherClient c) {
		PublisherSession state;
		if (c.getRestartToken() != null) {
			if (!clientStates.containsKey(c.getRestartToken()))
				return null;
			state = clientStates.get(c.getRestartToken());
			if (!state.stopDeleteTimer())
				return null;
		} else {
			state = new PublisherSession(this, generator);
			clientStates.put(state.getKey(), state);
		}

		boolean failedSyncing = false;
		try {
			state.sync(c);
			processor.addClient(state);
			return state;
		} catch (IOException e) {
			failedSyncing = true;
			e.printStackTrace();
		} catch (ServiceNotAuthorizedException e) {
			failedSyncing = true;
			e.printStackTrace();
		} catch (ServiceNotEnabledException e) {
			failedSyncing = true;
			e.printStackTrace();
		} finally {
			if (failedSyncing) {
				state.disconnect();
				processor.removeClient(state);
			}
		}
		return null;
	}

	/**
	 * Remove this session from the state tables, and release all references to
	 * subscribed packs.
	 *
	 * @param session
	 */
	public synchronized void removeSession(PublisherSession session) {
		clientStates.remove(session.getKey());
		session.close();
	}

	/** @return post-receive hook implementation to install in ReceivePack */
	public PostReceiveHook getHook() {
		return postReceiveHook;
	}

	/**
	 * Register for updates on this repository. This may be called multiple
	 * times for the same repository, and it is guaranteed to be called for all
	 * repositories that have subscribers.
	 *
	 * @param r
	 * @param name
	 *            the name of the repository to hook, e.g "repo.git". This is
	 *            often the /path string of a request that is needed to access
	 *            this repository, and does not necessarily correspond to the
	 *            filesystem. It is the same name used by RepositoryResolver
	 *            to resolve a repository.
	 * @throws NotSupportedException
	 */
	public void hookRepository(Repository r, String name) throws NotSupportedException {
		repositoryNameLookup.register(r, name);
	}

	private String getName(Repository r) throws NotSupportedException {
		return repositoryNameLookup.find(r);
	}

	/**
	 * Needs to be called whenever a successful update to a repository occurs.
	 * Guaranteed to be called for repositories already opened with
	 * {@link #hookRepository(Repository, String)}, but it may be called with
	 * repositories not opened.
	 *
	 * @param db
	 * @param updates
	 * @throws NotSupportedException
	 */
	public synchronized void onPush(
			Repository db, Collection<ReceiveCommand> updates) throws NotSupportedException {
		// Split pack apart into the smallest number of packs needed to cover
		// the different sessions. Not all sessions will be subscribed to all of
		// the updated refs.
		Map<Set<ReceiveCommand>, List<PublisherSession>> updateGroups = processor
				.getAffectedClients(getName(db), updates);
		for (Map.Entry<Set<ReceiveCommand>, List<PublisherSession>> e :
				updateGroups.entrySet()) {
			try {
				List<PublisherSession> sessions = e.getValue();
				if (sessions.size() == 0)
					break;

				PublisherPack pk = packFactory.buildPack(
						sessions.size(), getName(db), updates, db);

				packStream.put(pk);
			} catch (IOException ex) {
				// Nothing, pack already released
			}
		}
	}

	/**
	 * @param c
	 * @param name
	 * @return the Repository with this name
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 * @throws ServiceMayNotContinueException
	 * @throws RepositoryNotFoundException
	 */
	public Repository getRepository(PublisherClient c, String name)
			throws RepositoryNotFoundException, ServiceMayNotContinueException,
			ServiceNotAuthorizedException, ServiceNotEnabledException {
		return resolver.open(c, name);
	}

	/** @return a new iterator starting at the tail of the stream */
	public ConcurrentIterator<PublisherPack> getPackStreamIterator() {
		return packStream.getTailIterator();
	}

	/**
	 * @param refUpdates
	 * @param serverRepository
	 * @param repoName
	 * @return a new publisher pack initialized for 1 consumer
	 * @throws IOException
	 */
	public PublisherPack createInitialPack(List<ReceiveCommand> refUpdates,
			Repository serverRepository, String repoName) throws IOException {
		return packFactory.buildPack(1, repoName, refUpdates, serverRepository);
	}

	/**
	 * @param session
	 * @return the task to delete this session
	 */
	public ScheduledFuture<?> startDeleteTimer(
			final PublisherSession session) {
		return sessionDeleter.schedule(new Callable<Void>() {
			public Void call() throws Exception {
				removeSession(session);
				return null;
			}
		}, sessionLifetime, TimeUnit.SECONDS);
	}
}
