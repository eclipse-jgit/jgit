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
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.SubscribeCommand.Command;

/**
 * Subscribe implementation over a bi-directional connection. Client writes out
 * their current ref state and subscription requests, then receives a continuous
 * stream of packs.
 */
public class BasePackSubscribeConnection extends BasePackConnection implements
		SubscribeConnection {
	private boolean closed;

	/**
	 * @param packTransport
	 */
	BasePackSubscribeConnection(PackTransport packTransport) {
		super(packTransport);
		closed = false;
	}

	/**
	 * Send the subscription requests and wait for new updates.
	 *
	 * @param subscriber
	 * @param subscribeCommands
	 * @param monitor
	 * @throws InterruptedException
	 */
	public void doSubscribe(Subscriber subscriber,
			Map<String, List<SubscribeCommand>> subscribeCommands,
			ProgressMonitor monitor)
			throws InterruptedException, TransportException, IOException {
		try {
			monitor.beginTask(MessageFormat.format(
					JGitText.get().subscribeStart, subscriber.getKey()), 2);

			// Send fast restart
			String restart = subscriber.getRestartToken();
			if (restart == null)
				pckOut.writeString("hello");
			else {
				String sequence = subscriber.getRestartSequence();
				if (sequence == null)
					pckOut.writeString("fast-restart " + restart);
				else
					pckOut.writeString(
							"fast-restart " + restart + " " + sequence);
			}

			// Send subscription specs
			for (Map.Entry<String, List<SubscribeCommand>> e :
					subscribeCommands.entrySet()) {
				pckOut.writeString("repo " + e.getKey());
				for (SubscribeCommand cmd : e.getValue()) {
					if (cmd.getCommand() == Command.SUBSCRIBE)
						pckOut.writeString("subscribe " + cmd.getSpec());
					else
						pckOut.writeString("unsubscribe " + cmd.getSpec());
				}
			}
			pckOut.end();

			// Send current ref sha1 for all matching pubsub refs, but send
			// the local ref names
			for (String repoName : subscribeCommands.keySet()) {
				SubscribedRepository repo = subscriber.getRepository(repoName);
				pckOut.writeString("repo " + repoName);
				for (Map.Entry<String, Ref> ref :
						repo.getPubSubRefs().entrySet()) {
					pckOut.writeString(ref.getValue().getLeaf().getObjectId()
							.getName() + " " + ref.getKey());
				}
			}
			pckOut.end();
			monitor.update(1);

			// Read fast restart token
			String line = pckIn.readString();
			String parts[] = line.split(" ", 2);
			if (parts[0].equals("reconnect")) {
				subscriber.setRestartToken(null);
				subscriber.setRestartSequence(null);
				return;
			}
			if (!parts[0].equals("fast-restart"))
				throw new TransportException(MessageFormat.format(
						JGitText.get().expectedGot, "fast-restart", line));
			subscriber.setRestartToken(parts[1]);
			if ((line = pckIn.readString()) != PacketLineIn.END)
				throw new TransportException(MessageFormat.format(
						JGitText.get().expectedGot, "END", line));
			monitor.update(1);
			monitor.endTask();

			// Receive publishes forever
			while (!closed) {
				line = pckIn.readString();
				if (line.equals("heartbeat")) {
					if ((line = pckIn.readString()) != PacketLineIn.END)
						throw new TransportException(MessageFormat.format(
								JGitText.get().expectedGot, "END", line));
					continue;
				} else if (!line.startsWith("update "))
					throw new TransportException(MessageFormat.format(
							JGitText.get().expectedGot, "update", line));
				String repo = line.split(" ", 2)[1];

				SubscribedRepository db = subscriber.getRepository(repo);
				if (db == null)
					throw new TransportException(MessageFormat.format(
							JGitText.get().repositoryNotFound, repo));
				monitor.beginTask(MessageFormat.format(
						JGitText.get().subscribeNewUpdate, repo), 1);
				receivePublish(db);
				monitor.update(1);
				monitor.endTask();

				line = pckIn.readString();
				if (!line.startsWith("sequence "))
					throw new TransportException(MessageFormat.format(
							JGitText.get().expectedGot, "sequence", line));
				subscriber.setRestartSequence(line.split(" ", 2)[1]);
				if ((line = pckIn.readString()) != PacketLineIn.END)
					throw new TransportException(MessageFormat.format(
							JGitText.get().expectedGot, "END", line));

				if (Thread.interrupted() || monitor.isCancelled())
					throw new InterruptedException();
			}
		} finally {
			close();
		}
	}

	private void receivePublish(SubscribedRepository repo)
			throws IOException {
		final String remote = repo.getRemote();
		ReceivePack rp = new ReceivePack(repo.getRepository()) {
			/** Translate refs/* to refs/pubsub/remote/* before validating. */
			@Override
			protected void validateCommands() {
				for (ReceiveCommand c : getAllCommands())
					c.setName(SubscribedRepository.getPubSubRefFromLocal(
							remote, c.getRefName()));
				super.validateCommands();
			}
		};
		rp.setExpectDataAfterPackFooter(true);
		rp.setBiDirectionalPipe(false);
		// Set the advertised refs to be the current refs/pubsub/* refs
		rp.setAdvertisedRefs(repo.getPubSubRefs(), null);
		try {
			rp.receive(in, out, null);
		} finally {
			rp.unlockPack();
		}
	}

	public void close() {
		closed = true;
		super.close();
	}
}
