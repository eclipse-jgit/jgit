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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.eclipse.jgit.util.RefTranslator;

/**
 * SubscribeConnection implementation over a send-receive connection. The client
 * writes out their current ref state and subscription requests, then receives a
 * continuous stream of packs. The pubsub protocol is designed to operate over
 * HTTP, so the client does not send any data back to the server after it
 * receives data.
 */
public class BasePackSubscribeConnection extends BasePackConnection implements
		SubscribeConnection {
	private static final int LATENCY_TIMEOUT = 15; // seconds

	private static class ReceivePublishedPack extends BaseReceivePack {
		final String remote;

		private ReceivePublishedPack(Repository into, String remoteName) {
			super(into);
			remote = remoteName;
		}

		private void receive(InputStream input) throws IOException {
			init(input, null, null);
			try {
				execute();
			} finally {
				unlockPack();
			}
		}

		private void execute() throws IOException {
			recvCommands();
			if (hasCommands()) {
				// Translate refs/* to refs/pubsub/remote/*
				for (ReceiveCommand c : getAllCommands())
					c.setRefName(RefTranslator.getPubSubRefFromRemote(
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
	 * Prepare a new connection.
	 *
	 * @param myIn
	 * @param myOut
	 */
	protected void start(InputStream myIn, OutputStream myOut) {
		closed = false;
		init(myIn, myOut);
	}

	public void subscribe(SubscribeState subscriber,
			Map<String, List<SubscribeCommand>> subscribeCommands,
			PrintWriter output)
			throws InterruptedException, TransportException, IOException {
		try {
			writeSubscribeHeader(subscriber);

			// Send subscription specs
			for (Map.Entry<String, List<SubscribeCommand>> e :
					subscribeCommands.entrySet()) {
				String repoName = e.getKey();
				writeSubscribeCommands(repoName, e.getValue());

				List<String> subscribeSpecs = new ArrayList<String>();
				for (SubscribeCommand cmd : e.getValue()) {
					if (cmd.getCommand() == Command.SUBSCRIBE)
						subscribeSpecs.add(cmd.getSpec());
				}
				writeRepositoryState(
						subscriber.getRepository(repoName), subscribeSpecs);
				pckOut.end();
			}

			write("done");

			if (!readResponseHeader())
				return;

			// Receive publishes forever
			while (!closed) {
				if (Thread.interrupted())
					throw new InterruptedException();
				readUpdate(subscriber, output);
			}
		} finally {
			close();
		}
	}

	/**
	 * @param subscriber
	 * @param output
	 * @throws TransportException
	 * @throws IOException
	 */
	private void readUpdate(SubscribeState subscriber, PrintWriter output)
			throws TransportException, IOException {
		String line = pckIn.readString();
		if (line.equals("heartbeat"))
			return;
		if (line.startsWith("restart-token ")) {
			subscriber.setRestartToken(line.substring(
					"restart-token ".length()));
			return;
		}
		if (line.startsWith("heartbeat-interval ")) {
			transport.setTimeout(Integer.parseInt(line.substring(
					"heartbeat-interval ".length())) + LATENCY_TIMEOUT);
			return;
		}
		if (!line.startsWith("update "))
			throw new TransportException(MessageFormat.format(
					JGitText.get().expectedGot, "update", line));
		String repo = line.substring("update ".length());

		SubscribedRepository db = subscriber.getRepository(repo);
		if (db == null)
			throw new TransportException(MessageFormat.format(
					JGitText.get().repositoryNotFound, repo));
		receivePublish(db, output);
		line = pckIn.readString();
		if (!line.startsWith("pack-id "))
			throw new TransportException(MessageFormat.format(
					JGitText.get().expectedGot, "pack-id", line));
		subscriber.setLastPackId(line.substring(
				"pack-id ".length()));
	}

	/**
	 * Send current ref sha1 for all newly matching pubsub refs, but send the
	 * remote ref names the server recognizes.
	 *
	 * @param repository
	 * @param subscribeSpecs
	 * @throws IOException
	 */
	private void writeRepositoryState(
			SubscribedRepository repository, List<String> subscribeSpecs)
			throws IOException {
		for (Map.Entry<String, Ref> ref :
				repository.getPubSubRefs().entrySet()) {
			String refName = ref.getKey();
			for (String spec : subscribeSpecs) {
				if (RefSpec.isWildcard(spec) && !refName.startsWith(
						spec.substring(0, spec.length() - 1)))
					continue;
				else if (!refName.equals(spec))
					continue;
				String objId = ref.getValue().getLeaf().getObjectId().getName();
				write("have " + objId + " " + refName);
			}
		}
	}

	/**
	 * @param repoName
	 * @param commands
	 * @throws IOException
	 */
	private void writeSubscribeCommands(
			String repoName, List<SubscribeCommand> commands)
			throws IOException {
		write("repository " + repoName);
		for (SubscribeCommand cmd : commands) {
			switch (cmd.getCommand()) {
			case SUBSCRIBE:
				write("want " + cmd.getSpec());
				break;
			case UNSUBSCRIBE:
				write("stop " + cmd.getSpec());
				break;
			default:
				throw new IllegalArgumentException(cmd.toString());
			}
		}
	}

	private void writeSubscribeHeader(SubscribeState subscriber)
			throws IOException {
		write("subscribe");
		// Send restart
		String restart = subscriber.getRestartToken();
		if (restart != null) {
			write("restart " + restart);
			String id = subscriber.getLastPackId();
			if (id != null)
				write("last-pack-id " + id);
		}
		pckOut.end();
	}

	public void sendSubscribeAdvertisement(SubscribeState subscriber)
			throws IOException, TransportException {
		try {
			pckOut.writeString("advertisement");
			for (String repository : subscriber.getAllRepositories()) {
				write("repositoryaccess " + repository);
			}
			pckOut.end();

			readResponseHeader();
		} finally {
			close();
		}
	}

	/**
	 * @return true to begin receiving publishes, false for reconnect, exception
	 *         for remote error.
	 * @throws IOException
	 * @throws TransportException
	 */
	private boolean readResponseHeader()
			throws IOException, TransportException {
		String line = pckIn.readString();
		if ("reconnect".equals(line))
			return false;
		if (line.startsWith("ERR "))
			throw new TransportException(line.substring("ERR ".length()));
		if (line.startsWith("error: "))
			throw new TransportException(line.substring("error: ".length()));
		if (!line.equals("ACK"))
			throw new TransportException(MessageFormat.format(
					JGitText.get().expectedGot, "ACK", line));
		return true;
	}

	private void write(String line) throws IOException {
		pckOut.writeString(line + "\n");
	}

	private void receivePublish(SubscribedRepository sr, PrintWriter output)
			throws IOException {
		Repository repository = sr.getRepository();
		ReceivePublishedPack rp = new ReceivePublishedPack(
				repository, sr.getRemote());
		rp.setExpectDataAfterPackFooter(true);
		// Set the advertised refs to be the current refs/pubsub/* refs
		rp.setAdvertisedRefs(sr.getPubSubRefs(), null);
		try {
			rp.receive(in);
			// Check for any errors during receive
			for (ReceiveCommand rc : rp.getAllCommands()) {
				if (rc.getResult() != ReceiveCommand.Result.OK)
					throw new TransportException(rc.getMessage() + " " + rc);
			}
			String workDir = JGitText.get().notFound;
			try {
				workDir = repository.getWorkTree().getCanonicalPath();
			} catch (NoWorkTreeException e) {
				// Nothing
			}
			output.println(MessageFormat.format(
					JGitText.get().subscribeNewUpdate, sr.getName(),
					workDir));
			for (ReceiveCommand rc : rp.getAllCommands())
				output.format("%-10s %-7s -> %-7s %s\n", rc.getType(),
						rc.getOldId().abbreviate(7).name(),
						rc.getNewId().abbreviate(7).name(), rc.getRefName());
			output.flush();
		} finally {
			rp.release();
		}
	}

	@Override
	public void close() {
		closed = true;
		super.close();
	}
}
