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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jgit.transport.Publisher.PublisherException;
import org.eclipse.jgit.transport.PublisherStream.Window;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * The state of one client connection to one or more repositories. This is to
 * allow fast reconnect if the connection is dropped.
 */
public class PublisherSession {
	/**
	 * The state of a single client repository and list of current
	 * subscriptions.
	 */
	public static class SubscribedRepository {
		private final Set<SubscribeSpec> subscribeSpecs;

		private final String name;

		private SubscribedRepository(String name) {
			this.name = name;
			subscribeSpecs = new HashSet<SubscribeSpec>();
		}

		/**
		 * @param s
		 *            add subscribe spec
		 * @return false if this spec did not exist and was added
		 */
		private boolean add(SubscribeSpec s) {
			return !subscribeSpecs.add(s);
		}

		/** @return the current subscribe specs for this client */
		public Set<SubscribeSpec> getSubscribeSpecs() {
			return Collections.unmodifiableSet(subscribeSpecs);
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

	private final Window pushStreamIterator;

	private ScheduledFuture<?> deleteTimer;

	PublisherSession(Window iterator, String sessionId) {
		restartToken = sessionId;
		state = new HashMap<String, SubscribedRepository>();
		pushStreamIterator = iterator;
	}

	/** @return a unique key for this client's state */
	public String getKey() {
		return restartToken;
	}

	/**
	 * Record the client's subscribe commands in the session's state.
	 *
	 * @param commands
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	public synchronized void sync(Map<String, List<SubscribeCommand>> commands)
			throws IOException, ServiceNotAuthorizedException,
			ServiceNotEnabledException {
		for (Entry<String, List<SubscribeCommand>> e :
				commands.entrySet()) {
			String name = e.getKey();
			SubscribedRepository sr = state.get(name);
			if (sr == null) {
				sr = new SubscribedRepository(name);
				state.put(name, sr);
			}
			for (SubscribeCommand command : e.getValue()) {
				if (command.getCommand() == Command.SUBSCRIBE)
					sr.add(new SubscribeSpec(command.getSpec()));
			}
		}
	}

	/**
	 * @return the names of all SubscribedRepository states for this session
	 */
	public synchronized Set<String> getSubscribedRepositoryNames() {
		return Collections.unmodifiableSet(state.keySet());
	}

	/**
	 * @param repoName
	 * @return subscription specs for the named repository, never null
	 */
	public synchronized Set<SubscribeSpec> getSubscriptions(String repoName) {
		SubscribedRepository r = state.get(repoName);
		if (r == null)
			return Collections.emptySet();
		return r.getSubscribeSpecs();
	}

	/**
	 * Close this session, release the stream window.
	 *
	 * @throws PublisherException
	 */
	public void close() throws PublisherException {
		pushStreamIterator.release();
	}

	/**
	 * @return the window into the PublisherStream
	 */
	public PublisherStream.Window getStream() {
		return pushStreamIterator;
	}

	void setDeleteTimer(ScheduledFuture<?> timer) {
		deleteTimer = timer;
	}

	/** @return true if the timer to delete this session was canceled */
	public boolean cancelDeleteTimer() {
		if (deleteTimer == null)
			return true;
		boolean result = deleteTimer.cancel(false);
		deleteTimer = null;
		return result;
	}

	@Override
	public String toString() {
		return "PublisherSession[id=" + restartToken + "]";
	}
}
