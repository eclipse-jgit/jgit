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
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Manage starting, syncing and closing Subscribers based upon Publisher config
 * updates.
 */
public class SubscriptionManager {
	private static class SubscriptionInstance {
		Subscriber subscriber;

		Thread subscriberThread;

		private void close() {
			if (subscriber == null)
				return;
			subscriber.close();
			if (subscriberThread != null)
				subscriberThread.interrupt();
		}
	}

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
			if (System.currentTimeMillis() - lastCall > timeout)
				reset();
			n += 1;
			double r = Math.random() + 0.5;
			double exp = Math.pow(2, n);
			long delay = (long) (r * timeout * exp);
			Thread.sleep(Math.min(max, delay));
			lastCall = System.currentTimeMillis();
		}

		void reset() {
			n = 0;
		}
	}

	private Map<String, SubscriptionInstance>
			subscribers = new HashMap<String, SubscriptionInstance>();

	private final PrintWriter out;

	private final ProgressMonitor monitor;

	/**
	 * @param monitor
	 * @param out
	 */
	public SubscriptionManager(ProgressMonitor monitor, PrintWriter out) {
		this.monitor = monitor;
		this.out = out;
	}

	/**
	 * Sync the subscription manager with a new configuration. This will examine
	 * changes between old and new configs and send subscribe/unsubscribe
	 * commands to the individual Subscriber connections. New domains will spawn
	 * new Subscriber connections, and removed domains will be closed.
	 *
	 * Subscribers that return from a subscribe() call without throwing an
	 * exception should have their state reset before reconnecting. If an
	 * IOException is thrown, the connection was cut and a reconnect should be
	 * attempted.
	 *
	 * @param publishers
	 */
	public void sync(Collection<PubSubConfig.Publisher> publishers) {
		Set<String> existingKeys = new HashSet<String>(subscribers.keySet());
		for (final PubSubConfig.Publisher p : publishers) {
			final String key = p.getKey();
			SubscriptionInstance instance = subscribers.get(key);
			Map<String, List<SubscribeCommand>> syncCommands;
			if (instance != null) {
				try {
					syncCommands = instance.subscriber.sync(p);
				} catch (IOException e) {
					printError(key, e);
					instance.close();
					continue;
				}
				if (syncCommands.size() < 1)
					continue;
				instance.close();
			} else {
				try {
					instance = new SubscriptionInstance();
					instance.subscriber = new Subscriber(p.getUri());
					subscribers.put(p.getKey(), instance);
					syncCommands = instance.subscriber.sync(p);
					if (syncCommands.size() < 1)
						continue;
				} catch (IOException e) {
					printError(key, e);
					continue;
				}
			}

			final Subscriber subscriber = instance.subscriber;
			final Map<String, List<SubscribeCommand>> initCmds = syncCommands;

			Thread thread = new Thread() {
				public void run() {
					try {
						// Max 240s backoff, 4s timeout
						Backoff backoff = new Backoff(240 * 1000, 4 * 1000);
						Map<String, List<SubscribeCommand>> commands = initCmds;
						while (true) {
							try {
								subscriber.subscribe(commands, monitor);
							} catch (NotSupportedException e) {
								throw e;
							} catch (IOException e) {
								printError(key, e);
								// Socket error, reconnect with fast-restart if
								// possible, else resend commands
								if (subscriber.getRestartToken() != null)
									commands = Collections.emptyMap();
								backoff.backoff();
								continue;
							}
							// Clear all state when disconnected cleanly before
							// reconnecting
							backoff.reset();
							List<RefSpec> clearSpecs = Collections.emptyList();
							for (PubSubConfig.Subscriber config :
									p.getSubscribers()) {
								SubscribedRepository sr = subscriber
										.getRepository(config.getName());
								if (sr != null)
									sr.setSubscribeSpecs(clearSpecs);
							}
							commands = subscriber.sync(p);
						}
					} catch (NotSupportedException e) {
						printError(key, e);
					} catch (InterruptedException e) {
						// Nothing
					} catch (IOException e) {
						// Error setting up SubscribedRepository
						printError(key, e);
					}
				}
			};
			instance.subscriberThread = thread;
			thread.start();

			existingKeys.remove(key);
		}

		// Remove any subscribers no longer in the config
		for (String key : existingKeys) {
			SubscriptionInstance s = subscribers.get(key);
			s.close();
			subscribers.remove(key);
		}
	}

	/** Close all open subscriber connections. */
	public void close() {
		for (String key : subscribers.keySet()) {
			SubscriptionInstance s = subscribers.get(key);
			s.close();
			subscribers.remove(key);
		}
	}

	private void printError(String key, Exception e) {
		out.println(MessageFormat.format(
				JGitText.get().subscribeError, key, e.getMessage()));
		out.flush();
	}
}
