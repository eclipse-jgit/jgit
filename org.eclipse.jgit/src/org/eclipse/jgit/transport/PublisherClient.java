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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.SubscribeCommand.Command;

/**
 * A single client (connection) subscribed to one or more repositories.
 */
public class PublisherClient {
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
	 */
	public void subscribe(final InputStream myIn, final OutputStream myOut,
			final OutputStream messages) throws IOException {
		this.in = new PacketLineIn(myIn);
		this.out = new BufferedOutputStream(myOut);

		readFastRestart();
		readSubscribeSpec();
		readRefState();

		// Add client to each of the subscribed repos
		PublisherSession clientState = publisher.connectClient(this);
		PacketLineOut pktLineOut = new PacketLineOut(out);
		if (clientState == null) {
			pktLineOut.writeString("reconnect");
			pktLineOut.end();
			return;
		}
		// If restarting from a pack number, check that we have a reference
		// to that pack next in the stream. Clients will supply this number only
		// when they were in the process of receiving a pack
		if (lastPackNumber != -1) {
			PublisherPack nextPack = clientState.peekNextUpdate();
			if (nextPack == null
					|| nextPack.getPackNumber() != lastPackNumber) {
				pktLineOut.writeString("reconnect");
				pktLineOut.end();
				return;
			}
		}
		pktLineOut.writeString("fast-restart " + clientState.getKey());
		pktLineOut.end();

		// Wait here for new PublisherUpdates until the connection is dropped
		consumeThread = Thread.currentThread();
		String oldThreadName = consumeThread.getName();
		consumeThread.setName("PubSub Consumer " + clientState.getKey());
		try {
			while (true) {
				PublisherPack pk = clientState.getNextUpdate(
						HEARTBEAT_INTERVAL);
				if (pk == null) {
					pktLineOut.writeString("heartbeat");
					pktLineOut.end();
				} else {
					// Write repository line
					pktLineOut.writeString("update " + pk.getRepositoryName());
					PublisherPackSlice slice;
					for (Iterator<PublisherPackSlice> it = pk.getSlices();
							it.hasNext();) {
						slice = it.next();
						slice.writeToStream(out);
					}
					pktLineOut.writeString("sequence " + pk.getPackNumber());
					pktLineOut.end();
					pk.release();
				}
			}
		} catch (IOException e) {
			System.err.println("Client disconnected");
			clientState.rollbackUpdateStream();
			throw e;
		} catch (InterruptedException e) {
			System.err.println("Interrupted while polling");
			e.printStackTrace();
		} finally {
			consumeThread.setName(oldThreadName);
			consumeThread = null;
			clientState.disconnect();
		}
	}

	private void readFastRestart() throws IOException {
		String parts[] = in.readString().split(" ", 3);
		if (!parts[0].equals("fast-restart")) {
			return;
		}
		if (parts.length < 2)
			throw new IOException("Invalid fast-restart line");
		restartToken = parts[1];
		if (parts.length > 2)
			lastPackNumber = Integer.parseInt(parts[2]);
	}

	private void readSubscribeSpec() throws IOException {
		String line;
		ArrayList<SubscribeCommand> cmdList = null;
		String repo = null;
		while ((line = in.readString()) != PacketLineIn.END) {
			String parts[] = line.split(" ", 2);
			String type = parts[0];
			if (type.equals("repo")) {
				if (repo != null)
					commands.put(repo, cmdList);
				cmdList = new ArrayList<SubscribeCommand>();
				repo = parts[1];
			} else {
				if (cmdList == null)
					throw new IOException("Invalid subscribe request");
				if (type.equals("subscribe")) {
					cmdList.add(
							new SubscribeCommand(Command.SUBSCRIBE, parts[1]));
				} else if (type.equals("unsubscribe")) {
					cmdList.add(new SubscribeCommand(
							Command.UNSUBSCRIBE, parts[1]));
				}
			}
		}
		if (repo != null)
			commands.put(repo, cmdList); // Trailing add
	}

	private void readRefState() throws IOException {
		String line;
		Map<String, ObjectId> stateList = null;
		String repo = null;
		while ((line = in.readString()) != PacketLineIn.END) {
			String parts[] = line.split(" ", 2);
			String type = parts[0];
			if (type.equals("repo")) {
				if (repo != null)
					refState.put(repo, stateList);
				stateList = new HashMap<String, ObjectId>();
				repo = parts[1];
			} else {
				if (stateList == null)
					throw new IOException("Invalid subscribe request");
				stateList.put(parts[1], ObjectId.fromString(parts[0]));
			}
		}
		if (repo != null)
			refState.put(repo, stateList); // Trailing add
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
}
