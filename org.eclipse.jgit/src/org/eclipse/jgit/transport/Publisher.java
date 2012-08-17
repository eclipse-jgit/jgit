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
import java.util.concurrent.ThreadFactory;
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
public class Publisher implements PostReceiveHook {
	/** Session lifetime after a client disconnects, in seconds. */
	private static final int DEFAULT_SESSION_GRACE_PERIOD = 60 * 60;

	private static final int DEFAULT_SESSION_ROLLBACK_COUNT = 2;

	private final PublisherPackFactory packFactory;

	private final Map<String, PublisherSession> sessions;

	private final PublisherReverseResolver repositoryNameLookup;

	private final PublisherStream pushStream;

	private final ScheduledThreadPoolExecutor sessionDeleter;

	private final SessionGenerator generator;

	private int sessionLifetime;

	private int sessionRollbackCount;

	private long pushCount;

	private volatile boolean closed;

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
		pushStream = new PublisherStream();
		sessionDeleter = new ScheduledThreadPoolExecutor(
				1, new ThreadFactory() {
					public Thread newThread(Runnable r) {
						Thread t = new Thread(r);
						t.setName("SessionDeleter");
						t.setDaemon(true);
						return t;
					}
				});
		sessionLifetime = DEFAULT_SESSION_GRACE_PERIOD;
		sessionRollbackCount = DEFAULT_SESSION_ROLLBACK_COUNT;
		this.repositoryNameLookup = repositoryNameLookup;
		this.packFactory = packFactory;
		this.generator = generator;
	}

	/**
	 * @param lifetime
	 *            the number of seconds before deleting this session after its
	 *            client disconnects
	 */
	public synchronized void setSessionLifetime(int lifetime) {
		sessionLifetime = lifetime;
	}

	/**
	 * @param count
	 *            the number of updates each session should keep after sending,
	 *            to allow the client to rollback the stream
	 */
	public synchronized void setSessionRollbackCount(int count) {
		sessionRollbackCount = count;
	}

	/**
	 * @return a new session, initialized with commands
	 */
	public synchronized PublisherSession newSession() {
		Window window = pushStream.newWindow(sessionRollbackCount);
		PublisherSession session = new PublisherSession(
				window, generator
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
		if (state == null || !state.cancelDeleteTimer())
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
		try {
			session.close();
		} catch (PublisherException e) {
			close();
			e.printStackTrace();
		}
	}

	/** Post-receive hook implementation to install in ReceivePack */
	public void onPostReceive(
			ReceivePack rp, Collection<ReceiveCommand> commands) {
		if (isClosed())
			return;
		List<ReceiveCommand> updates = new ArrayList<ReceiveCommand>(
				commands.size());
		for (ReceiveCommand c : commands) {
			if (c.getResult() != ReceiveCommand.Result.OK)
				continue;
			updates.add(c);
		}
		try {
			onPush(rp.db, updates);
		} catch (NotSupportedException e) {
			e.printStackTrace();
		} catch (PublisherException e) {
			// Fatal error
			close();
			e.printStackTrace();
		}
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
	public void hookRepository(Repository r, String name)
			throws NotSupportedException {
		repositoryNameLookup.register(r, name);
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
	 * @throws PublisherException
	 */
	public synchronized void onPush(
			Repository db, final Collection<ReceiveCommand> updates)
			throws NotSupportedException, PublisherException {
		String dbName = repositoryNameLookup.find(db);
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

	/**
	 * Close this publisher. This will cause all clients to disconnect and
	 * the publisher to stop receiving updates.
	 */
	public void close() {
		closed = true;
		sessionDeleter.shutdown();
	}

	/**
	 * @return true if the publisher is closed
	 */
	public boolean isClosed() {
		return closed;
	}
}
