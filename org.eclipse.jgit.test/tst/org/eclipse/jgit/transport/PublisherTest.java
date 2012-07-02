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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.PublisherSession.SessionGenerator;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Test;

/** End-to-end stream emulation tests for Publisher and helper classes. */
public class PublisherTest extends SampleDataRepositoryTestCase {
	ObjectId OBJECT_ID1 = ObjectId.fromString(
			"ac7e7e44c1885efb472ad54a78327d66bfc4ecef");

	ObjectId OBJECT_ID2 = ObjectId.fromString(
			"2c349335b7f797072cf729c4f3bb0914ecb6dec9");

	/** The ID for refs/heads/a in the test repository */
	ObjectId OBJECT_HEADS_A = ObjectId.fromString(
			"6db9c2ebf75590eef973081736730a9ea169a0c4");

	String refName = "refs/heads/foobar";

	PublisherBuffer buffer;

	PublisherPackFactory packFactory;

	Publisher publisher;

	CountDownLatch connectLatch;

	private void setUpPublisher(int expectedClients) {
		// Every PublisherTestClient that is created makes 2 calls to
		// connectClient.
		connectLatch = new CountDownLatch(2 * expectedClients);
		buffer = new PublisherBuffer(64 * 1024);
		packFactory = new PublisherPackFactory(buffer);
		packFactory.setSliceSize(1024);
		packFactory.setSliceMemoryThreshold(3);
		publisher = new Publisher(new PublisherReverseResolver(),
			packFactory, new SessionGenerator() {
			private AtomicInteger sid = new AtomicInteger();

			public String generate() {
				return "fixed-session-id-" + sid.incrementAndGet();
			}
		}) {
			@Override
			public synchronized PublisherSession connectClient(
					PublisherClient c) throws ServiceNotAuthorizedException,
					ServiceNotEnabledException {
				PublisherSession s = super.connectClient(c);
				connectLatch.countDown();
				return s;
			}
		};
		buffer.startGC();
	}

	/**
	 * Test reading the packet-line encoded subscribe commands from a client.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSubscribe() throws Exception {
		setUpPublisher(1);
		PublisherClientTest pc = new PublisherClientTest(0, 100) {
			@Override
			protected void writeSubscribePacket(PacketLineOut pckLineOut)
					throws IOException {
				pckLineOut.writeString("restart " + state.getKey());
				pckLineOut.end();
				pckLineOut.writeString("repository testrepository");
				pckLineOut.writeString("want " + refName);
				pckLineOut.writeString(
						"have " + OBJECT_ID1.getName() + " " + refName);
				pckLineOut.end();
				pckLineOut.writeString("done");
			}

			@Override
			public Repository openRepository(String name)
					throws RepositoryNotFoundException,
					ServiceMayNotContinueException,
					ServiceNotAuthorizedException, ServiceNotEnabledException {
				if (!name.equals("testrepository"))
					fail("Invalid open database request");
				return db;
			}
		};

		connectLatch.await();

		assertEquals(pc.getPublisherState().getKey(), pc.getRestartToken());

		Map<String, List<SubscribeCommand>> cmds = pc.getCommands();
		assertTrue(cmds.containsKey("testrepository"));
		List<SubscribeCommand> subCmds = cmds.get("testrepository");
		assertEquals(1, subCmds.size());
		SubscribeCommand subCmd = subCmds.get(0);
		assertEquals(Command.SUBSCRIBE, subCmd.getCommand());
		assertEquals(refName, subCmd.getSpec());

		Map<String, Map<String, ObjectId>> s = pc.getRefState();
		assertTrue(s.containsKey("testrepository"));
		Map<String, ObjectId> specs = s.get("testrepository");
		assertEquals(1, specs.size());
		assertTrue(specs.containsKey(refName));
		assertEquals(OBJECT_ID1, specs.get(refName));
	}

	@Test
	public void testSimplePackStream() throws Exception {
		runPackStreamTest(1, 1, 1);
	}

	@Test
	public void testLargePackStream() throws Exception {
		runPackStreamTest(1, 1, 2000);
	}

	@Test
	public void testManyClientsPackStream() throws Exception {
		runPackStreamTest(2000, 1, 1);
	}

	@Test
	public void testMultiplePacks() throws Exception {
		runPackStreamTest(1, 20, 1);
	}

	@Test
	public void testEverythingPackStream() throws Exception {
		runPackStreamTest(25, 25, 200);
	}

	/**
	 * Check that the correct update is pushed at the start of the connection.
	 *
	 * @throws Exception
	 */
	@Test
	public void testInitialUpdate() throws Exception {
		setUpPublisher(1);
		PublisherClientTest c = new PublisherClientTest(0, 100) {
			@Override
			protected void writeSubscribePacket(PacketLineOut pckLineOut)
					throws IOException {
				pckLineOut.writeString("restart " + state.getKey());
				pckLineOut.end();
				pckLineOut.writeString("repository testrepository");
				pckLineOut.writeString("want refs/heads/a");
				pckLineOut.writeString("have " +
						ObjectId.zeroId().getName() + " refs/heads/a");
				pckLineOut.end();
				pckLineOut.writeString("done");
			}

			@Override
			public Repository openRepository(String name)
					throws RepositoryNotFoundException,
					ServiceMayNotContinueException,
					ServiceNotAuthorizedException, ServiceNotEnabledException {
				if (!name.equals("testrepository"))
					fail("Invalid open database request");
				return db;
			}
		};

		connectLatch.await();

		InputStream rawIn = new BufferedInputStream(c
				.getPipedInputStream(), 8192);
		PacketLineIn in = new PacketLineIn(rawIn);
		String line;
		String parts[];
		line = in.readString();
		parts = line.split(" ", 2);
		assertEquals("restart-token", parts[0]);
		assertEquals(c.getPublisherState().getKey(), parts[1]);
		line = in.readString();
		parts = line.split(" ", 2);
		assertEquals("heartbeat-interval", parts[0]);
		assertEquals(PacketLineIn.END, in.readString());
		int updatesLeft = 1;
		while (updatesLeft > 0) {
			line = in.readString();
			parts = line.split(" ", 2);
			if (parts[0].equals("heartbeat"))
				continue;
			assertEquals("update", parts[0]);
			assertEquals("testrepository", parts[1]);
			// Read until the end of the commands
			while ((line = in.readString()) != PacketLineIn.END) {
				parts = line.split(" ", 3);
				assertEquals(ObjectId.zeroId().name(), parts[0]);
				assertEquals(OBJECT_HEADS_A.name(), parts[1]);
				assertEquals("refs/heads/a", parts[2]);
			}
			updatesLeft--;
		}
	}

	private void runPackStreamTest(int clients, int updates, int update_size)
			throws Exception {
		setUpPublisher(clients);
		List<ReceiveCommand> refUpdates = Collections.nCopies(update_size,
				new ReceiveCommand(ObjectId.zeroId(), OBJECT_ID2, refName));

		List<PublisherSession> states = new ArrayList<
				PublisherSession>();
		List<PublisherClientTest> testClients = new ArrayList<
				PublisherTest.PublisherClientTest>();

		// Estimate size per ReceiveCommand
		int space = update_size * 100;

		for (int i = 0; i < clients; i++) {
			// Delay each write() by 5ms * client number
			PublisherClientTest t = new PublisherClientTest(i * 5, space) {
				@Override
				protected void writeSubscribePacket(PacketLineOut pckLineOut)
						throws IOException {
					pckLineOut.writeString("restart " + state.getKey());
					pckLineOut.end();
					pckLineOut.writeString("repository testrepository");
					pckLineOut.writeString("want " + refName);
					pckLineOut.writeString(
							"have " + OBJECT_ID1.getName() + " " + refName);
					pckLineOut.end();
					pckLineOut.writeString("done");
				}

				@Override
				public Repository openRepository(String name)
						throws RepositoryNotFoundException,
						ServiceMayNotContinueException,
						ServiceNotAuthorizedException,
						ServiceNotEnabledException {
					if (!name.equals("testrepository"))
						fail("Invalid open database request");
					return db;
				}
			};
			testClients.add(t);
			states.add(t.getPublisherState());
		}

		connectLatch.await();

		for (int i = 0; i < updates; i++) {
			Thread.sleep(500);
			publisher.onPush(db, refUpdates);
		}

		for (PublisherClientTest c : testClients) {
			try {
				InputStream rawIn = new BufferedInputStream(c
						.getPipedInputStream(), 8192);
				PacketLineIn in = new PacketLineIn(rawIn);
				String line;
				String parts[];
				line = in.readString();
				parts = line.split(" ", 2);
				assertEquals("restart-token", parts[0]);
				assertEquals(c.getPublisherState().getKey(), parts[1]);
				line = in.readString();
				parts = line.split(" ", 2);
				assertEquals("heartbeat-interval", parts[0]);
				assertEquals(PacketLineIn.END, in.readString());
				int updatesLeft = updates;
				while (updatesLeft > 0) {
					line = in.readString();
					if (line.startsWith("heartbeat"))
						continue;
					assertEquals("update testrepository", line);

					// Read PACK
					ReceivePack rp = new ReceivePack(db);
					rp.setExpectDataAfterPackFooter(true);
					rp.setAllowCreates(true);
					rp.setBiDirectionalPipe(false);
					rp.receive(rawIn, null, null);
					rp.unlockPack();
					rp.close();

					line = in.readString();
					parts = line.split(" ", 2);
					assertEquals("sequence", parts[0]);
					updatesLeft--;
				}
			} catch (Exception e) {
				throw new Exception(c.getPublisherState().getKey(), e);
			} finally {
				c.close();
			}
		}
	}

	abstract class PublisherClientTest extends PublisherClient {
		Thread subscribeThread;

		PublisherSession state;

		PipedOutputStream output;

		PipedInputStream input;

		/**
		 * Connect first so we can get a fast-restart key, then disconnect and
		 * let subscribe() reconnect us with the same PublisherClientState
		 * instance.
		 *
		 * @param delayWrite
		 * @param space
		 * @throws ServiceNotAuthorizedException
		 * @throws ServiceNotEnabledException
		 */
		public PublisherClientTest(final int delayWrite, int space)
				throws ServiceNotAuthorizedException,
				ServiceNotEnabledException {
			super(publisher);
			state = publisher.connectClient(this);
			state.disconnect();

			ByteArrayOutputStream fillIn = new ByteArrayOutputStream();
			PacketLineOut pckLineOut = new PacketLineOut(fillIn);
			try {
				writeSubscribePacket(pckLineOut);
				pckLineOut.flush();
			} catch (IOException e) {
				fail(e.toString());
			}
			byte buf[] = fillIn.toByteArray();

			final InputStream myIn = new ByteArrayInputStream(buf);
			output = new PipedOutputStream() {
				@Override
				public synchronized void write(byte[] b, int off, int len)
						throws IOException {
					try {
						Thread.sleep(delayWrite);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					super.write(b, off, len);
				}
			};
			input = new PipedInputStream(space + 1024);
			try {
				output.connect(input);
			} catch (IOException e1) {
				// Never happens
			}

			final PublisherClient me = this;
			subscribeThread = new Thread() {
				@Override
				public void run() {
					try {
						me.subscribe(myIn, output, null);
					} catch (Exception e) {
						fail(e.toString());
					}
				}
			};

			subscribeThread.start();
		}

		@Override
		public synchronized void close() {
			try {
				if (output != null) {
					output.close();
					input.close();
				}
			} catch (IOException e) {
				fail(e.getMessage());
			}
		}

		protected abstract void writeSubscribePacket(PacketLineOut pckLineOut)
				throws IOException;

		public Thread getSubscribeThread() {
			return subscribeThread;
		}

		public PublisherSession getPublisherState() {
			return state;
		}

		public PipedInputStream getPipedInputStream() {
			return input;
		}
	}
}
