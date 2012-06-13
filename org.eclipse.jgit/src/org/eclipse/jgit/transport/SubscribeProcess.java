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
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.PubSubConfig.Publisher;
import org.eclipse.jgit.transport.SubscribeCommand.Command;

/**
 * Performs the subscribe loop with fast-restart.
 *
 * @see BasePackSubscribeConnection
 */
class SubscribeProcess {
	/** Exponential backoff with auto-reset. */
	private static class Backoff {
		int n;

		final int max;

		final int timeout;

		long lastCall;

		Backoff(int max, int timeout) {
			this.max = max;
			this.timeout = timeout;
		}

		/**
		 * If this is called at an interval > timeout, the retry count is
		 * automatically reset to 0.
		 *
		 * @throws InterruptedException
		 */
		void backoff() throws InterruptedException {
			if (milliTime() - lastCall > timeout)
				reset();
			n += 1;
			double r = Math.random() + 0.5;
			double exp = Math.pow(2, n);
			long delay = (long) (r * timeout * exp);
			Thread.sleep(Math.min(max, delay));
			lastCall = milliTime();
		}

		void reset() {
			n = 0;
		}

		long milliTime() {
			return System.nanoTime() / 1000000;
		}
	}

	/** The default timeout for a subscribe connection. */
	public final static int SUBSCRIBE_TIMEOUT = 3 * 60 * 60; // Seconds

	private final Transport transport;

	private SubscribeConnection connection;

	private final Subscriber subscriber;

	private int timeout;

	/**
	 * @param uri
	 * @param s
	 * @throws TransportException
	 * @throws NotSupportedException
	 */
	public SubscribeProcess(URIish uri, Subscriber s)
			throws NotSupportedException, TransportException {
		subscriber = s;
		timeout = SUBSCRIBE_TIMEOUT;
		transport = Transport.open(uri);
	}

	/**
	 * Sync the existing subscribe specs against the subscribe specs found in
	 * the publisher config.
	 *
	 * @param publisher
	 * @return Map of repository name to list of SubscribeCommands required to
	 *         sync the existing state to the state of the publisher config.
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Map<String, List<SubscribeCommand>> sync(
			PubSubConfig.Publisher publisher)
			throws IOException, URISyntaxException {
		Map<String, List<SubscribeCommand>> subscribeCommands = new HashMap<
				String, List<SubscribeCommand>>();

		for (PubSubConfig.Subscriber s : publisher.getSubscribers()) {
			SubscribedRepository sr = subscriber.getRepository(s.getName());
			List<SubscribeCommand> repoCommands = new ArrayList<
					SubscribeCommand>();

			if (sr == null) {
				sr = new SubscribedRepository(s);
				for (RefSpec spec : s.getSubscribeSpecs())
					repoCommands.add(new SubscribeCommand(
							Command.SUBSCRIBE, spec.getSource()));
			} else {
				// Calculate a diff in subscriptions using the SubscribedRepos
				// and the new configuration. Use the exact string values.
				List<String> newSpecs = new ArrayList<String>();
				List<String> oldSpecs = new ArrayList<String>();
				for (RefSpec rs : s.getSubscribeSpecs())
					newSpecs.add(rs.getSource());
				for (RefSpec rs : sr.getSubscribeSpecs())
					oldSpecs.add(rs.getSource());

				Set<String> toAdd = new LinkedHashSet<String>(newSpecs);
				toAdd.removeAll(oldSpecs);
				for (String subscribe : toAdd)
					repoCommands.add(
							new SubscribeCommand(Command.SUBSCRIBE, subscribe));

				Set<String> toRemove = new LinkedHashSet<String>(oldSpecs);
				toRemove.removeAll(newSpecs);
				for (String unsubscribe : toRemove)
					repoCommands.add(new SubscribeCommand(
							Command.UNSUBSCRIBE, unsubscribe));

				// Update state with new specs
				sr.setSubscribeSpecs(s.getSubscribeSpecs());
			}

			subscribeCommands.put(sr.getName(), repoCommands);
			subscriber.putRepository(sr.getName(), sr);
			sr.setUpRefs();
		}
		return subscribeCommands;
	}

	/**
	 * Advertise all refs to subscribe to, create SubscribedRepo instances for
	 * each repository. This method blocks until the connection is closed with
	 * {@link #closeConnection()}, or throws an exception.
	 *
	 * @param commands
	 *            a map from repository name to a list of SubscribeCommands to
	 *            be executed for that repository.
	 * @param publisher
	 * @param output
	 * @throws NotSupportedException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public void execute(Map<String, List<SubscribeCommand>> commands,
			Publisher publisher, PrintWriter output)
			throws NotSupportedException, InterruptedException, IOException,
			URISyntaxException {
		// Max 240s backoff, 4s timeout
		Backoff backoff = new Backoff(240 * 1000, 4 * 1000);
		while (true) {
			if (Thread.interrupted())
				throw new InterruptedException();
			try {
				doSubscribe(commands, output);
			} catch (NotSupportedException e) {
				throw e;
			} catch (TransportException e) {
				throw e;
			} catch (IOException e) {
				// Connection error, reconnect with fast-restart if possible,
				// else resend commands. If we connected successfully, clear
				// the command list.
				if (subscriber.getRestartToken() != null)
					commands = Collections.emptyMap();
				backoff.backoff();
				continue;
			}
			// Clear all state when disconnected cleanly before reconnecting.
			backoff.reset();
			subscriber.reset();
			commands = sync(publisher);
		}
	}

	void doSubscribe(
			Map<String, List<SubscribeCommand>> commands, PrintWriter output)
			throws NotSupportedException, TransportException,
			InterruptedException, IOException {
		connection = transport.openSubscribe(subscriber);
		transport.setTimeout(timeout);
		try {
			connection.subscribe(subscriber, commands, output);
		} finally {
			closeConnection();
		}
	}

	Subscriber getSubscriber() {
		return subscriber;
	}

	/**
	 * Set the timeout for the subscribe connection.
	 *
	 * @param seconds
	 */
	public void setTimeout(int seconds) {
		this.timeout = seconds;
	}

	/** Close the SubscribeConnection. */
	public void closeConnection() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	/** Release all resources. */
	public void close() {
		transport.close();
		subscriber.close();
	}
}
