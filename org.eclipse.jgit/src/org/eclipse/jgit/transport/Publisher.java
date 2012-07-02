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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PublisherSession.SessionGenerator;
import org.eclipse.jgit.transport.PublisherStream.Window;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Publishes pack data to multiple Subscribers with matching subscription
 * requests. This publisher handles multiple repositories.
 */
public class Publisher {
	/** Session lifetime after a client disconnects, in seconds. */
	private static final int DEFAULT_SESSION_LIFETIME = 60 * 60;

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

	private final Map<String, PublisherSession> sessions;

	private final PublisherReverseResolver repositoryNameLookup;

	private final PublisherStream<PublisherPush> pushStream;

	private final ScheduledThreadPoolExecutor sessionDeleter;

	private final SessionGenerator generator;

	private int sessionLifetime;

	private long pushCount;

	/**
	 * Create a new Publisher to serve multiple repositories.
	 *
	 * @param repositoryNameLookup
	 * @param packFactory
	 * @param generator
	 */
	public Publisher(PublisherReverseResolver repositoryNameLookup,
			PublisherPackFactory packFactory, SessionGenerator generator) {
		sessions = new HashMap<String, PublisherSession>();
		pushStream = new PublisherStream<PublisherPush>();
		sessionDeleter = new ScheduledThreadPoolExecutor(1);
		sessionLifetime = DEFAULT_SESSION_LIFETIME;
		this.repositoryNameLookup = repositoryNameLookup;
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
	 * @return a new session, initialized with commands
	 */
	public synchronized PublisherSession newSession() {
		Window<PublisherPush> it = pushStream.newIterator(2);
		PublisherSession session = new PublisherSession(it, generator
				.generate());
		sessions.put(session.getKey(), session);
		return session;
	}

	/**
	 * @param sessionId
	 * @return the persistent state for this connection, or null if a restart
	 *         token was given but could not be recovered
	 * @throws ServiceNotAuthorizedException
	 * @throws ServiceNotEnabledException
	 */
	public synchronized PublisherSession reconnectSession(String sessionId)
			throws ServiceNotAuthorizedException, ServiceNotEnabledException {
		PublisherSession state = sessions.get(sessionId);
		if (state == null || !state.stopDeleteTimer())
			return null;
		return state;
	}

	/**
	 * Remove this session from the state tables, and release all references to
	 * subscribed packs.
	 *
	 * @param session
	 */
	public synchronized void removeSession(PublisherSession session) {
		sessions.remove(session.getKey());
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
			Repository db, final Collection<ReceiveCommand> updates) throws NotSupportedException {
		String dbName = getName(db);
		if (dbName == null)
			return;
		pushStream.add(new PublisherPush(
				dbName, updates, "push-" + pushCount++, packFactory));
	}

	/**
	 * @return the factory used for creating new pack files to stream to
	 *         clients.
	 */
	public PublisherPackFactory getPackFactory() {
		return packFactory;
	}

	/**
	 * @param session
	 *            the session to be deleted after {@link #sessionLifetime}
	 *            seconds.
	 */
	public void startDeleteTimer(
			final PublisherSession session) {
		session.setDeleteTimer(sessionDeleter.schedule(new Callable<Void>() {
			public Void call() throws Exception {
				removeSession(session);
				return null;
			}
		}, sessionLifetime, TimeUnit.SECONDS));
	}
}
