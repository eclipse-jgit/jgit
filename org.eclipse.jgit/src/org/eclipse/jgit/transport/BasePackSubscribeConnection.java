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
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SubscribeCommand.Command;

/**
 * Subscribe implementation over a send-receive connection. The client writes
 * out their current ref state and subscription requests, then receives a
 * continuous stream of packs. The pubsub protocol is designed to operate over
 * HTTP, so the client does not send any data back to the server after it
 * receives data.
 */
public class BasePackSubscribeConnection extends BasePackConnection implements
		SubscribeConnection {
	class ReceivePublishedPack extends BaseReceivePack {
		final String remote;

		ReceivePublishedPack(Repository into, String remoteName) {
			super(into);
			remote = remoteName;
		}

		void receive(InputStream input) throws IOException {
			init(input, null, null);
			try {
				execute();
			} finally {
				try {
					unlockPack();
				} finally {
					release();
				}
			}
		}

		void execute() throws IOException {
			recvCommands();
			if (hasCommands()) {
				// Translate refs/* to refs/pubsub/remote/*
				for (ReceiveCommand c : getAllCommands())
					c.setName(SubscribedRepository.getPubSubRefFromRemote(
							remote, c.getRefName()));
				Throwable unpackError = null;
				if (needPack()) {
					try {
						receivePackAndCheckConnectivity();
					} catch (IOException err) {
						unpackError = err;
					} catch (RuntimeException err) {
						unpackError = err;
					} catch (Error err) {
						unpackError = err;
					}
				}
				if (unpackError == null) {
					validateCommands();
					executeCommands();
				}
				unlockPack();

				if (unpackError != null)
					throw new UnpackException(unpackError);
			}
		}

		@Override
		protected String getLockMessageProcessName() {
			return "pubsub-receive-" + remote;
		}
	}

	private volatile boolean closed;

	/**
	 * @param packTransport
	 */
	BasePackSubscribeConnection(PackTransport packTransport) {
		super(packTransport);
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

			// Send restart
			String restart = subscriber.getRestartToken();
			if (restart != null) {
				write("restart " + restart);
				String number = subscriber.getLastPackNumber();
				if (number != null)
					write("last-pack " + number);
			}
			pckOut.end();

			// Send subscription specs
			for (Map.Entry<String, List<SubscribeCommand>> e :
					subscribeCommands.entrySet()) {
				List<SubscribeCommand> subscribeOnly = new ArrayList<
						SubscribeCommand>();
				String repoName = e.getKey();
				write("repository " + repoName);
				for (SubscribeCommand cmd : e.getValue()) {
					if (cmd.getCommand() == Command.SUBSCRIBE) {
						write("want " + cmd.getSpec());
						subscribeOnly.add(cmd);
					} else
						write("stop " + cmd.getSpec());
				}
				// Send current ref sha1 for all newly matching pubsub refs, but
				// send the remote ref names the server recognizes.
				SubscribedRepository repo = subscriber.getRepository(repoName);
				for (Map.Entry<String, Ref> ref :
						repo.getPubSubRefs().entrySet()) {
					String refName = ref.getKey();
					for (SubscribeCommand cmd : subscribeOnly) {
						String spec = cmd.getSpec();
						if ((spec.endsWith("/*") && refName.startsWith(
								spec.substring(0, spec.length() - 1)))
								|| refName.equals(spec))
							write("have " + ref.getValue()
									.getLeaf().getObjectId().getName() + " "
									+ refName);
					}
				}
				pckOut.end();
			}

			write("done");
			monitor.update(1);

			// Read restart token and heartbeat interval
			String line = pckIn.readString();
			if ("reconnect".equals(line))
				return;

			if (!line.startsWith("restart-token "))
				throw new TransportException(MessageFormat.format(
						JGitText.get().expectedGot, "restart-token", line));
			subscriber.setRestartToken(line.substring(
					"restart-token ".length()));
			line = pckIn.readString();

			if (!line.startsWith("heartbeat-interval "))
				throw new TransportException(MessageFormat.format(
						JGitText.get().expectedGot, "heartbeat-interval",
						line));
			transport.setTimeout(Integer.parseInt(
					line.substring("heartbeat-interval ".length())));

			if ((line = pckIn.readString()) != PacketLineIn.END)
				throw new TransportException(MessageFormat.format(
						JGitText.get().expectedGot, "END", line));
			monitor.update(1);
			monitor.endTask();

			// Receive publishes forever
			while (!closed) {
				if (Thread.interrupted() || monitor.isCancelled())
					throw new InterruptedException();

				line = pckIn.readString();
				if (line.equals("heartbeat"))
					continue;
				if (line.startsWith("change-restart-token ")) {
					subscriber.setRestartToken(line.substring(
							"change-restart-token ".length()));
					continue;
				}
				if (!line.startsWith("update "))
					throw new TransportException(MessageFormat.format(
							JGitText.get().expectedGot, "update", line));
				String repo = line.substring("update ".length());

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
				subscriber.setLastPackNumber(line.substring(
						"sequence ".length()));
			}
		} finally {
			close();
		}
	}

	private void write(String line) throws IOException {
		pckOut.writeString(line + "\n");
	}

	private void receivePublish(SubscribedRepository repo)
			throws IOException {
		ReceivePublishedPack rp = new ReceivePublishedPack(
				repo.getRepository(), repo.getRemote());
		rp.setExpectDataAfterPackFooter(true);
		// Set the advertised refs to be the current refs/pubsub/* refs
		rp.setAdvertisedRefs(repo.getPubSubRefs(), null);
		rp.receive(in);
	}

	public void close() {
		closed = true;
		super.close();
	}
}
