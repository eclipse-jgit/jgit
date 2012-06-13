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
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.PubSubConfig.Publisher;

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

	private final Subscriber subscriber;

	private Thread subscriberThread;

	/**
	 * @param subscriber
	 */
	public SubscribeProcess(Subscriber subscriber) {
		this.subscriber = subscriber;
	}

	/**
	 * @param commands
	 *            a map from repository name to a list of SubscribeCommands to
	 *            be executed for that repository.
	 * @param publisher
	 * @param monitor
	 * @throws NotSupportedException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public void execute(Map<String, List<SubscribeCommand>> commands,
			Publisher publisher, ProgressMonitor monitor)
			throws NotSupportedException, InterruptedException, IOException,
			URISyntaxException {
		// Max 240s backoff, 4s timeout
		Backoff backoff = new Backoff(240 * 1000, 4 * 1000);
		while (true) {
			if (Thread.interrupted())
				throw new InterruptedException();
			try {
				subscriber.subscribe(commands, monitor);
			} catch (NotSupportedException e) {
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
			reset(publisher);
			commands = subscriber.sync(publisher);
		}
	}

	/**
	 * @param publisher
	 */
	public void reset(Publisher publisher) {
		List<RefSpec> clearSpecs = Collections.emptyList();
		for (PubSubConfig.Subscriber config : publisher.getSubscribers()) {
			SubscribedRepository sr = subscriber.getRepository(
					config.getName());
			if (sr != null)
				sr.setSubscribeSpecs(clearSpecs);
		}
	}

	/**
	 * @param p
	 * @return Map of repository name to list of SubscribeCommands required to
	 *         sync the existing state to the state of the publisher config.
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Map<String, List<SubscribeCommand>> sync(Publisher p)
			throws IOException, URISyntaxException {
		return subscriber.sync(p);
	}

	/**
	 * @param thread
	 */
	public void setThread(Thread thread) {
		subscriberThread = thread;
	}

	/** Close the subscriber and interrupt the subscribe thread, if any. */
	public void close() {
		if (subscriber == null)
			return;
		subscriber.close();
		if (subscriberThread != null)
			subscriberThread.interrupt();
	}
}
