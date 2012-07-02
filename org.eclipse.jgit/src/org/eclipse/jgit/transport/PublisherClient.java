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

import static org.eclipse.jgit.transport.SubscribeCommand.Command.SUBSCRIBE;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PublisherStream.Window;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * A single client (connection) subscribed to one or more repositories.
 */
public abstract class PublisherClient {
	private static class Header {
		private String lastPackId;

		private String restartToken;
	}

	private static class Request {
		private Map<String, List<SubscribeCommand>> commands;

		private Map<String, Map<String, ObjectId>> clientRefState;
	}

	private static final int HEARTBEAT_INTERVAL = 10000; // ms

	private final Publisher publisher;

	private PublisherSession session;

	private PacketLineIn in;

	private OutputStream out;

	private int heartbeatInterval;

	/**
	 * @param p
	 *            the publisher process
	 */
	public PublisherClient(Publisher p) {
		publisher = p;
		heartbeatInterval = HEARTBEAT_INTERVAL;
	}

	/**
	 * @param ms interval between heartbeats, in ms
	 */
	public void setHeartbeatInterval(int ms) {
		heartbeatInterval = ms;
	}

	/**
	 * Main publishing blocking loop. Reads current client state and reconnects
	 * to an existing state using the fast-restart token if possible. It then
	 * blocks on a queue for PublisherUpdates to send.
	 *
	 * @param myIn
	 * @param myOut
	 * @param messages
	 * @throws IOException
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 */
	public void subscribeLoop(
			InputStream myIn, OutputStream myOut, OutputStream messages)
			throws IOException, ServiceNotAuthorizedException,
			ServiceNotEnabledException {
		in = new PacketLineIn(myIn);
		out = new BufferedOutputStream(myOut);

		try {
			String line = in.readString();
			if (line.equals("advertisement"))
				doAdvertisement();
			if (line.equals("subscribe"))
				doSubscribe();
			throw new TransportException(MessageFormat.format(
					JGitText.get().expectedGot, "advertisement|subscribe",
					line));
		} finally {
			in = null;
			out.close();
			out = null;
		}
	}

	/**
	 * Open all listed repositories and check access.
	 *
	 * @throws IOException
	 * @throws ServiceNotAuthorizedException
	 */
	private void doAdvertisement()
			throws IOException, ServiceNotAuthorizedException {
		PacketLineOut pktLineOut = new PacketLineOut(out);
		try {
			String line;
			while ((line = in.readString()) != PacketLineIn.END) {
				if (!line.startsWith("repositoryaccess "))
					throw new TransportException(MessageFormat.format(
							JGitText.get().expectedGot, "repositoryaccess",
							line));
				Repository r = openRepository(
						line.substring("repositoryaccess ".length()));
				r.close();
			}
			pktLineOut.writeString("ACK");
		} catch (TransportException e) {
			pktLineOut.writeString("ERR " + e.getMessage());
		} catch (ServiceNotEnabledException e) {
			pktLineOut.writeString("ERR " + e.getMessage());
		} finally {
			pktLineOut.flush();
		}
	}

	private void doSubscribe() throws TransportException, IOException,
			ServiceNotAuthorizedException, ServiceNotEnabledException {
		Header header = readHeaders();
		Request request = readSubscribeCommands();

		PacketLineOut pktLineOut = new PacketLineOut(out);
		// If a client tries to reconnect using a restart token as well as
		// changing their subscribe specs, force them to reconnect. This case
		// may be handled in the future (the protocol supports
		// "stop"/unsubscribe).
		if (request.commands.size() > 0 && header.restartToken != null) {
			pktLineOut.writeString("reconnect");
			pktLineOut.end();
			return;
		}

		Window stream;

		if (header.restartToken != null) {
			session = publisher.reconnectSession(header.restartToken);
			// If the client supplied a restart token that was not found, force
			// a reconnect.
			if (session == null) {
				pktLineOut.writeString("reconnect");
				pktLineOut.end();
				return;
			}
			stream = session.getStream();
			// If restarting from after a pack number, check that we have a
			// reference to that pack next in the stream.
			if (header.lastPackId != null) {
				if (!stream.rollback(header.lastPackId)) {
					pktLineOut.writeString("reconnect");
					pktLineOut.end();
					return;
				}
			}
		} else {
			session = publisher.newSession();
			stream = session.getStream();
			session.sync(request.commands);
			generateInitialPacks(request.clientRefState);
		}

		header = null;
		request = null;

		pktLineOut.writeString("ACK");
		pktLineOut.writeString("restart-token " + session.getKey());
		pktLineOut.writeString(
				"heartbeat-interval " + heartbeatInterval / 1000);
		pktLineOut.flush();

		// Wait here for new PublisherUpdates until the connection is dropped
		Thread consumeThread = Thread.currentThread();
		String oldThreadName = consumeThread.getName();
		consumeThread.setName("PubSub Consumer " + session.getKey());
		try {
			while (true) {
				if (Thread.interrupted())
					throw new InterruptedException();
				sendUpdate(pktLineOut, stream);
			}
		} catch (InterruptedException e) {
			// Nothing, interrupted by disconnect
		} finally {
			consumeThread.setName(oldThreadName);
			consumeThread = null;
			publisher.startDeleteTimer(session);
		}
	}

	private void sendUpdate(PacketLineOut pktLineOut, Window stream)
			throws InterruptedException, IOException,
			ServiceNotAuthorizedException, ServiceNotEnabledException {
		int waitTime = heartbeatInterval;
		PublisherPush push = null;
		PublisherPack pack = null;
		while (waitTime > 0) {
			long startTime = System.currentTimeMillis();
			push = stream.next(waitTime, TimeUnit.MILLISECONDS);
			waitTime -= (System.currentTimeMillis() - startTime);
			if (push == null)
				break;
			pack = push.get(this);
			if (pack == null)
				continue; // This client doesn't want this update
			stream.mark();
			break;
		}
		if (publisher.isClosed())
			throw new EOFException();
		if (push == null || pack == null) {
			pktLineOut.writeString("heartbeat");
			pktLineOut.flush();
		} else {
			// Write repository update
			pktLineOut.writeString("update "
					+ push.getRepositoryName());
			pack.writeToStream(out);
			pktLineOut.writeString("pack-id " + push.getPushId());
			pktLineOut.flush();
		}
	}

	private void generateInitialPacks(
			Map<String, Map<String, ObjectId>> clientRefState)
			throws RepositoryNotFoundException,
			ServiceMayNotContinueException,
			ServiceNotAuthorizedException,
			ServiceNotEnabledException,
			IOException {
		Window it = session.getStream();
		int idCounter = 0;
		for (String name : session.getSubscribedRepositoryNames()) {
			List<ReceiveCommand> updateCommands = new ArrayList<
					ReceiveCommand>();
			Map<String, ObjectId> clientState = clientRefState.get(name);
			if (clientState == null)
				clientState = Collections.emptyMap();
			Repository r = openRepository(name);
			try {
				publisher.hookRepository(r, name);
				RefDatabase refDb = r.getRefDatabase();
				// Generate a list of ReceiveCommands to get the client up to
				// date from {client ref state} -> {matching server side ref
				// state}
				for (SubscribeSpec spec : session.getSubscriptions(name)) {
					Collection<Ref> matchedServerRefs;
					if (spec.isWildcard()) {
						String ref = spec.getRefName();
						matchedServerRefs = refDb.getRefs(
								SubscribeSpec.stripWildcard(ref)).values();
					} else {
						Ref ref = refDb.getRef(spec.getRefName());
						if (ref == null)
							continue;
						matchedServerRefs = Collections.singleton(ref);
					}
					// Find starting points from client state
					for (Ref ref : matchedServerRefs) {
						ObjectId start = clientState.get(ref.getName());
						if (start == null)
							start = ObjectId.zeroId();
						updateCommands.add(new ReceiveCommand(start, ref
								.getObjectId(), ref.getName()));
					}
				}
				it.prepend(new PublisherPush(name, updateCommands, "pack-"
						+ session.getKey() + "-" + idCounter++, publisher
						.getPackFactory()));
			} finally {
				r.close();
			}
		}
	}

	/**
	 * <pre>
	 * restart [token]
	 * last-pack-id [number]
	 * [END]
	 * </pre>
	 *
	 * @return header values
	 * @throws IOException
	 */
	private Header readHeaders() throws IOException {
		Header h = new Header();
		String line;
		while ((line = in.readString()) != PacketLineIn.END) {
			if (line.startsWith("restart "))
				h.restartToken = line.substring("restart ".length());
			else if (line.startsWith("last-pack-number "))
				h.lastPackId = line.substring("last-pack-number ".length());
		}
		return h;
	}

	/**
	 * <pre>
	 * repository [name]
	 * want [refspec]
	 * stop [refspec]
	 * ...
	 * have [objectid] [refspec]
	 * ...
	 * [END]
	 * ...
	 * done
	 *
	 * @return request data
	 * @throws IOException
	 * @throws TransportException
	 */
	private Request readSubscribeCommands()
			throws IOException, TransportException {
		Request r = new Request();
		r.commands = new HashMap<String, List<SubscribeCommand>>();
		r.clientRefState = new HashMap<String, Map<String, ObjectId>>();
		String line;
		ArrayList<SubscribeCommand> cmdList = null;
		Map<String, ObjectId> stateList = null;
		String repo = null;
		while (!(line = in.readString()).startsWith("done")) {
			if (line == PacketLineIn.END && repo != null) {
				r.commands.put(repo, cmdList);
				r.clientRefState.put(repo, stateList);
				repo = null;
				cmdList = null;
				stateList = null;
			} else if (line.startsWith("repository ")) {
				repo = line.substring("repository ".length());
				cmdList = new ArrayList<SubscribeCommand>();
				stateList = new HashMap<String, ObjectId>();
			} else {
				if (repo == null || cmdList == null || stateList == null)
					throw new TransportException(MessageFormat.format(JGitText
							.get().invalidSubscribeRequest, line));
				if (line.startsWith("want "))
					cmdList.add(new SubscribeCommand(SUBSCRIBE, line.substring(
							"want ".length())));
				else if (line.startsWith("stop "))
					throw new TransportException("stop command not supported");
				else if (line.startsWith("have ")) {
					String[] parts = line.split(" ", 3);
					if (parts.length < 3)
						throw new TransportException(MessageFormat.format(
								JGitText.get().invalidSubscribeRequest, line));
					stateList.put(parts[2], ObjectId.fromString(parts[1]));
				}
			}
		}
		return r;
	}

	/**
	 * @return the state of this client
	 */
	public PublisherSession getSession() {
		return session;
	}

	/**
	 * @param name
	 * @return the repository with this name
	 * @throws ServiceNotEnabledException
	 * @throws ServiceNotAuthorizedException
	 * @throws ServiceMayNotContinueException
	 * @throws RepositoryNotFoundException
	 */
	public abstract Repository openRepository(String name)
			throws RepositoryNotFoundException, ServiceMayNotContinueException,
			ServiceNotAuthorizedException, ServiceNotEnabledException;
}
