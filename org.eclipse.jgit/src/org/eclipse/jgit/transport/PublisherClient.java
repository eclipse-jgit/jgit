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
import static org.eclipse.jgit.transport.SubscribeCommand.Command.UNSUBSCRIBE;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * A single client (connection) subscribed to one or more repositories.
 */
public abstract class PublisherClient {
	private static final int HEARTBEAT_INTERVAL = 10000;

	private final Publisher publisher;

	private Map<String, List<SubscribeCommand>> commands;

	private Map<String, Map<String, ObjectId>> refState;

	private PacketLineIn in;

	private OutputStream out;

	private String restartToken;

	private int lastPackNumber;

	private volatile boolean closed;

	private Thread consumeThread;

	/**
	 * @param p
	 *            the publisher process
	 */
	public PublisherClient(Publisher p) {
		lastPackNumber = -1;
		publisher = p;
		commands = new HashMap<String, List<SubscribeCommand>>();
		refState = new HashMap<String, Map<String, ObjectId>>();
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
	public void subscribe(
			InputStream myIn, OutputStream myOut, OutputStream messages)
			throws IOException, ServiceNotAuthorizedException,
			ServiceNotEnabledException {
		this.in = new PacketLineIn(myIn);
		this.out = new BufferedOutputStream(myOut);

		readRestart();
		boolean isAdvertisment = readSubscribeCommands();

		// Add client to each of the subscribed repositories.
		PublisherSession clientState = publisher.connectClient(this);
		PacketLineOut pktLineOut = new PacketLineOut(out);
		if (clientState == null) {
			pktLineOut.writeString("reconnect");
			pktLineOut.end();
			return;
		}
		// If restarting from a pack number, check that we have a reference
		// to that pack next in the stream. Clients will supply this number only
		// when they were in the process of receiving a pack.
		if (lastPackNumber != -1) {
			PublisherPack nextPack = clientState.peekNextUpdate();
			if (nextPack == null
					|| nextPack.getPackNumber() != lastPackNumber) {
				pktLineOut.writeString("reconnect");
				pktLineOut.end();
				return;
			}
		}
		pktLineOut.writeString("restart-token " + clientState.getKey());
		pktLineOut.writeString(
				"heartbeat-interval " + HEARTBEAT_INTERVAL / 1000);
		pktLineOut.end();

		if (isAdvertisment) {
			clientState.disconnect();
			return;
		}

		// Wait here for new PublisherUpdates until the connection is dropped
		consumeThread = Thread.currentThread();
		String oldThreadName = consumeThread.getName();
		consumeThread.setName("PubSub Consumer " + clientState.getKey());
		try {
			while (true) {
				if (Thread.interrupted())
					throw new InterruptedException();
				PublisherPack pk = clientState.getNextUpdate(
						HEARTBEAT_INTERVAL);
				if (closed)
					throw new EOFException();
				if (pk == null)
					pktLineOut.writeString("heartbeat");
				else {
					// Write repository line
					pktLineOut.writeString("update " + pk.getRepositoryName());
					for (Iterator<PublisherPackSlice> it = pk.getSlices();
							it.hasNext();)
						it.next().writeToStream(out);
					pktLineOut.writeString("sequence " + pk.getPackNumber());
					pk.release();
				}
				pktLineOut.flush();
			}
		} catch (IOException e) {
			clientState.rollbackUpdateStream();
		} catch (InterruptedException e) {
			// Nothing, interrupted by disconnect
		} finally {
			consumeThread.setName(oldThreadName);
			consumeThread = null;
			clientState.disconnect();
		}
	}

	/**
	 * <pre>
	 * restart [token]
	 * last-pack [number]
	 * [END]
	 * </pre>
	 *
	 * @throws IOException
	 */
	private void readRestart() throws IOException {
		String line;
		while ((line = in.readString()) != PacketLineIn.END) {
			if (line.startsWith("restart "))
				restartToken = line.substring("restart ".length());
			else if (line.startsWith("last-pack "))
				lastPackNumber = Integer.parseInt(line.substring(
						"last-pack ".length()));
		}
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
	 * </pre>
	 *
	 * @return true if this request is an advertisement to determine if the
	 *         publish-subscribe service exists and the client is authorized to
	 *         read all listed repositories.
	 * @throws IOException
	 */
	private boolean readSubscribeCommands() throws IOException {
		String line;
		ArrayList<SubscribeCommand> cmdList = null;
		Map<String, ObjectId> stateList = null;
		String repo = null;
		while (!(line = in.readString()).startsWith("done")) {
			if (line == PacketLineIn.END) {
				commands.put(repo, cmdList);
				refState.put(repo, stateList);
				repo = null;
				cmdList = null;
				stateList = null;
			} else if (line.startsWith("repository ")) {
				repo = line.substring("repository ".length());
				cmdList = new ArrayList<SubscribeCommand>();
				stateList = new HashMap<String, ObjectId>();
			} else {
				if (repo == null || cmdList == null || stateList == null)
					throw new IOException(MessageFormat.format(JGitText
							.get().invalidSubscribeRequest, line));
				if (line.startsWith("want "))
					cmdList.add(new SubscribeCommand(SUBSCRIBE, line.substring(
							"want ".length())));
				else if (line.startsWith("stop "))
					cmdList.add(new SubscribeCommand(UNSUBSCRIBE, line
							.substring("stop ".length())));
				else if (line.startsWith("have ")) {
					String[] parts = line.split(" ", 3);
					stateList.put(parts[2], ObjectId.fromString(parts[1]));
				}
			}
		}
		return line.endsWith(" advertisement");
	}

	/** @return restart token, or null if none exists */
	String getRestartToken() {
		return restartToken;
	}

	Map<String, List<SubscribeCommand>> getCommands() {
		return commands;
	}

	Map<String, Map<String, ObjectId>> getRefState() {
		return refState;
	}

	/** Close the output connection. */
	public synchronized void close() {
		if (!closed) {
			closed = true;
			try {
				out.close();
			} catch (IOException e) {
				// Do nothing.
			} finally {
				out = null;
			}
			// Break any thread waiting on new updates
			if (consumeThread != null) {
				consumeThread.interrupt();
			}
		}
	}

	/** @return true if this client connection is closed */
	public boolean isClosed() {
		return closed;
	}

	/** @return output stream to write packs to */
	public OutputStream getOutputStream() {
		return out;
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
