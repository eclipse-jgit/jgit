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
import java.text.MessageFormat;
import java.util.Collection;
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
	private Map<String, SubscribeProcess>
			subscribers = new HashMap<String, SubscribeProcess>();

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
	 * exception should have their state reset before reconnecting; the server
	 * did not recognize the fast-restart token so the token should be discarded
	 * and all subscribe specs and ref state should be sent again. If an
	 * IOException is thrown, the connection was cut and a reconnect should be
	 * attempted.
	 *
	 * @param publishers
	 */
	public void sync(Collection<PubSubConfig.Publisher> publishers) {
		Set<String> existingKeys = new HashSet<String>(subscribers.keySet());
		for (final PubSubConfig.Publisher p : publishers) {
			final String key = p.getKey();
			SubscribeProcess process = subscribers.get(key);
			Map<String, List<SubscribeCommand>> syncCommands;
			if (process != null) {
				try {
					syncCommands = process.sync(p);
				} catch (Exception e) {
					printError(key, e);
					process.close();
					continue;
				}
				if (syncCommands.size() < 1)
					continue;
				// Close the existing connection, restart in a new thread with
				// the new sync commands.
				process.close();
			} else {
				try {
					process = new SubscribeProcess(new Subscriber(p.getUri()));
					subscribers.put(p.getKey(), process);
					syncCommands = process.sync(p);
				} catch (Exception e) {
					subscribers.remove(p.getKey());
					if (process != null)
						process.close();
					printError(key, e);
					continue;
				}
			}

			final SubscribeProcess finalProcess = process;
			final Map<String, List<SubscribeCommand>> initCmds = syncCommands;

			Thread thread = new Thread() {
				public void run() {
					try {
						finalProcess.execute(initCmds, p, monitor);
					} catch (NotSupportedException e) {
						printError(key, e);
					} catch (InterruptedException e) {
						return;
					} catch (IOException e) {
						printError(key, e);
					} catch (URISyntaxException e) {
						printError(key, e);
					}
				}
			};
			process.setThread(thread);
			thread.start();
			existingKeys.remove(key);
		}

		// Remove any subscribers no longer in the config.
		for (String key : existingKeys) {
			SubscribeProcess s = subscribers.get(key);
			s.close();
			subscribers.remove(key);
		}
	}

	/** Close all open subscriber connections. */
	public void close() {
		for (String key : subscribers.keySet()) {
			SubscribeProcess s = subscribers.get(key);
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
