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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.ConcurrentLinkedList.ConcurrentIterator;

/**
 * The state of one client connection to one or more repositories. This is to
 * allow fast reconnect if the connection is dropped.
 */
public class PublisherSession {
	/**
	 * The state of a single client repository and list of current subscriptions
	 * to be persisted across disconnects and updated when packs are
	 * successfully pushed.
	 */
	public class SubscribedRepository {
		private final Map<String, SubscribeSpec> subscribeSpecs;

		private final String name;

		SubscribedRepository(String name) {
			this.name = name;
			subscribeSpecs = new HashMap<String, SubscribeSpec>();
		}

		/**
		 * @param s
		 *            remove subscribe spec
		 * @return true if this spec existed and was removed
		 */
		public synchronized boolean remove(SubscribeSpec s) {
			return (subscribeSpecs.remove(s.getRefName()) != null);
		}

		/**
		 * @param s
		 *            add subscribe spec
		 * @return false if this spec did not exist and was added
		 */
		public synchronized boolean add(SubscribeSpec s) {
			return (subscribeSpecs.put(s.getRefName(), s) != null);
		}

		/** @return the current subscribe specs for this client */
		public Collection<SubscribeSpec> getSubscribeSpecs() {
			return Collections.unmodifiableCollection(subscribeSpecs.values());
		}

		/** @return this repository's name e.g "repo.git" */
		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("SubscribedRepository[\n");
			for (SubscribeSpec s : getSubscribeSpecs())
				sb.append(s.toString()).append("\n");
			sb.append("]");
			return sb.toString();
		}
	}

	/** Generates a new session ID. */
	public interface SessionGenerator {
		/** @return a new session ID */
		public String generate();
	}

	/** "repo" -> ClientState */
	private final Map<String, SubscribedRepository> state;

	private final String restartToken;

	private volatile PublisherClient client;

	private final Publisher publisher;

	private final ConcurrentIterator<PublisherPack> packStreamIterator;

	private List<PublisherPack> initialUpdates;

	private ScheduledFuture<?> deleteTimer;

	PublisherSession(Publisher p, SessionGenerator generator) {
		restartToken = generator.generate();
		state = new HashMap<String, SubscribedRepository>();
		publisher = p;
		packStreamIterator = p.getPackStreamIterator();
	}

	/** @return a unique key for this client's state */
	public String getKey() {
		return restartToken;
	}

	/**
	 * Connect the client communication channel and synchronize any updated
	 * subscription requests and ref states. Decrease reference counts on packs
	 * that are no longer subscribed to, and increase counts on new packs. Build
	 * an initial update pack to bring the client up to the server's state.
	 *
	 * @param c
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	public synchronized void sync(PublisherClient c) throws IOException,
			ServiceNotAuthorizedException, ServiceNotEnabledException {
		disconnect();
		client = c;

		// Sync subscribe/unsubscribe requests, record the commands that changed
		Map<String, Map<SubscribeSpec, Command>> realCommands = syncCommands(
				c.getCommands());

		Map<String, Map<String, ObjectId>> baseUpdateRefs = syncReferenceCounts(
				realCommands);

		// Fill in ref values for subscribe commands not matching any pack in
		// the stream from the server repository
		for (Entry<String, Map<SubscribeSpec, Command>> e :
				realCommands.entrySet()) {
			String name = e.getKey();
			Repository serverRepository = publisher.getRepository(
					c, name);
			RefDatabase refDb = serverRepository.getRefDatabase();
			List<Ref> refList = new ArrayList<Ref>();
			for (Entry<SubscribeSpec, Command> e2 : e.getValue().entrySet()) {
				if (e2.getValue() == Command.UNSUBSCRIBE)
					continue;
				SubscribeSpec s = e2.getKey();
				Collection<Ref> serverRefs;
				if (s.isWildcard()) {
					String refName = s.getRefName();
					refName = refName.substring(0, refName.length() - 1);
					serverRefs = refDb.getRefs(refName).values();
					if (serverRefs.isEmpty())
						continue;
					refList.addAll(serverRefs);
				} else {
					Ref r = refDb.getRef(s.getRefName());
					if (r == null)
						continue;
					refList.add(r);
				}
			}
			Map<String, ObjectId> repoBaseRefs = baseUpdateRefs.get(name);
			if (repoBaseRefs == null) {
				repoBaseRefs = new HashMap<String, ObjectId>();
				baseUpdateRefs.put(name, repoBaseRefs);
			}
			for (Ref r : refList) {
				if (!repoBaseRefs.containsKey(r.getName()))
					repoBaseRefs.put(r.getName(), r.getObjectId());
			}
		}

		// Push updates to the client to get them up to date with the current
		// repository state
		initialUpdates = new ArrayList<PublisherPack>();
		Map<String, Map<String, ObjectId>> clientState = client
				.getRefState();
		for (Entry<String, Map<String, ObjectId>> e :
				baseUpdateRefs.entrySet()) {
			String repoName = e.getKey();
			Repository serverRepository = publisher.getRepository(
					c, repoName);
			publisher.hookRepository(serverRepository, repoName);

			List<ReceiveCommand> refUpdates = new ArrayList<ReceiveCommand>();

			Map<String, ObjectId> clientRepoState = clientState.get(repoName);
			for (Entry<String, ObjectId> e2 : e.getValue().entrySet()) {
				// Is this ref different from the client's value?
				String refName = e2.getKey();
				ObjectId refValue = e2.getValue();
				ObjectId ourRef = clientRepoState.get(refName);
				if (ourRef == null)
					// New ref on the server
					refUpdates.add(new ReceiveCommand(
							ObjectId.zeroId(), refValue, refName));
				else if (!refValue.equals(ourRef))
					refUpdates.add(new ReceiveCommand(
							ourRef, refValue, refName));
			}
			if (!refUpdates.isEmpty()) {
				initialUpdates.add(publisher.createInitialPack(
						refUpdates, serverRepository, repoName));
			}
		}
	}

	private Map<String, Map<SubscribeSpec, Command>> syncCommands(
			Map<String, List<SubscribeCommand>> commands) {
		Map<String, Map<SubscribeSpec, Command>> realCommands = new HashMap<
				String, Map<SubscribeSpec, Command>>();
		for (Entry<String, List<SubscribeCommand>> e : commands.entrySet()) {
			String repoName = e.getKey();
			SubscribedRepository repo = state.get(repoName);
			if (repo == null) {
				repo = new SubscribedRepository(repoName);
				state.put(repoName, repo);
			}
			Map<SubscribeSpec, Command> realRepoCommands = new HashMap<
					SubscribeSpec, Command>();
			realCommands.put(repoName, realRepoCommands);
			for (SubscribeCommand cmd : e.getValue()) {
				if (cmd.getCommand() == Command.SUBSCRIBE) {
					SubscribeSpec spec = new SubscribeSpec(cmd.getSpec());
					if (!repo.add(spec))
						realRepoCommands.put(spec, cmd.getCommand());
				} else if (cmd.getCommand() == Command.UNSUBSCRIBE) {
					SubscribeSpec spec = new SubscribeSpec(cmd.getSpec());
					if (repo.remove(spec))
						realRepoCommands.put(spec, cmd.getCommand());
				}
			}
		}
		return realCommands;
	}

	/**
	 * Dereference all packs that would have been used, but now will not because
	 * of unsubscribe commands. Get all server refs that match new subscribe
	 * commands, then bring the client up to date to the old id of the first
	 * pack that mentions each command. This lets the client continue to read
	 * from where it is in the stream as it adds and removes subscriptions.
	 *
	 * @param realCommands
	 * @return a map of all refs that are now matched by the new subscribe
	 *         commands. Each ref is the first instance of that ref in a
	 *         PublisherPack.
	 */
	private Map<String, Map<String, ObjectId>> syncReferenceCounts(
			Map<String, Map<SubscribeSpec, Command>> realCommands) {
		Map<String, Map<String, ObjectId>> newSubscribeRefs = new HashMap<
				String, Map<String, ObjectId>>();
		for (Iterator<PublisherPack> it = packStreamIterator.copy();
				it.hasNext();) {
			PublisherPack pk = it.next();
			Collection<ReceiveCommand> packCommands = pk.getCommands();

			if (!realCommands.containsKey(pk.getRepositoryName()))
				continue;
			// Unsubscribe if a single command matches an unsubscribe spec and
			// no other specs
			boolean matchUnsubscribe = false;
			boolean matchSubscribe = false;
			Map<String, ObjectId> matchingRefs = new HashMap<String, ObjectId>();
			Map<String, ObjectId> existingRefs = newSubscribeRefs.get(
					pk.getRepositoryName());
			Map<SubscribeSpec, Command> changedCommands = realCommands.get(
					pk.getRepositoryName());
			for (ReceiveCommand packCommand : packCommands) {
				for (Entry<SubscribeSpec, Command> e :
						changedCommands.entrySet()) {
					if (e.getKey().isMatch(packCommand.getRefName())) {
						if (e.getValue() == Command.SUBSCRIBE) {
							matchSubscribe = true;
							// Don't overwrite older refs
							if (existingRefs != null && !existingRefs
									.containsKey(packCommand.getRefName()))
								matchingRefs.put(packCommand.getRefName(),
										packCommand.getOldId());
						} else
							matchUnsubscribe = true;
					}
				}
			}
			if (matchUnsubscribe && matchSubscribe) {
				// Do nothing
			} else if (matchUnsubscribe) {
				// Decrement
				pk.release();
			} else if (matchSubscribe) {
				// Try to increment, if this fails then the pack has been closed
				// and we shouldn't add the matching refs
				if (!pk.incrementOpen())
					continue;
			}
			if (!matchingRefs.isEmpty()) {
				if (existingRefs == null)
					newSubscribeRefs.put(pk.getRepositoryName(), matchingRefs);
				else
					existingRefs.putAll(matchingRefs);
			}
		}
		return newSubscribeRefs;
	}

	/**
	 * @param repoName
	 * @return subscription specs for the named repository
	 */
	public Collection<SubscribeSpec> getSubscriptions(String repoName) {
		SubscribedRepository r = state.get(repoName);
		if (r == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableCollection(r.subscribeSpecs.values());
	}

	/** @return true if there is a client connected */
	public boolean isConnected() {
		PublisherClient pc = client;
		return (pc != null && !pc.isClosed());
	}

	/** @return the currently connected client */
	public PublisherClient getClient() {
		return client;
	}

	/** Disconnect the current PublisherClient. */
	public void disconnect() {
		PublisherClient pc = client;
		if (pc != null && !pc.isClosed()) {
			pc.close();
			client = null;
			startDeleteTimer();
		}
	}

	/**
	 * Close this session, decrement all references to packs we would have used.
	 */
	public void close() {
		disconnect();
		for (Iterator<PublisherPack> it = packStreamIterator.copy();
				it.hasNext();) {
			PublisherPack pk = it.next();
			SubscribedRepository sr = state.get(pk.getRepositoryName());
			if (sr == null)
				continue;
			if (pk.match(sr.getSubscribeSpecs()))
				pk.release();
		}
	}

	private boolean matchPack(PublisherPack pk) {
		SubscribedRepository sr = state.get(pk.getRepositoryName());
		return sr != null && pk.match(sr.getSubscribeSpecs());
	}

	/** Reset the stream to before the current update. */
	public void rollbackUpdateStream() {
		packStreamIterator.reset();
	}

	/**
	 * First updates using the initial backlog, then continues from the main
	 * update stream.
	 *
	 * @param heartbeatInterval
	 *            in milliseconds
	 * @return the next matching pack, or null
	 * @throws InterruptedException
	 */
	public PublisherPack getNextUpdate(int heartbeatInterval)
			throws InterruptedException {
		if (!initialUpdates.isEmpty())
			return initialUpdates.remove(0);
		int waitTime = heartbeatInterval;
		while (waitTime > 0) {
			long startTime = System.currentTimeMillis();
			PublisherPack next;
			try {
				next = packStreamIterator.next(
						waitTime, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				next = null;
			}
			waitTime -= (System.currentTimeMillis() - startTime);
			if (next != null && matchPack(next)) {
				packStreamIterator.markBefore();
				return next;
			}
		}

		return null;
	}

	/** @return the next update without advancing the stream */
	public PublisherPack peekNextUpdate() {
		return packStreamIterator.peek();
	}

	/** Start the timer to delete this session. */
	private void startDeleteTimer() {
		deleteTimer = publisher.startDeleteTimer(this);
	}

	/** @return true if the timer to delete this session was canceled */
	public boolean stopDeleteTimer() {
		if (deleteTimer == null)
			return true;
		return deleteTimer.cancel(false);
	}

	@Override
	public String toString() {
		return "PublisherSession[id=" + restartToken + ", connected="
				+ isConnected() + "]";
	}
}
