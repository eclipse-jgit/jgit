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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the protocol implementation of BasePackSubscribeConnection.
 *
 * Default publisherConfig has a single Subscriber
 * {@code http://android.googlesource.com/testrepository} subscribed to
 * {@code refs/heads/master} and {@code refs/tags/*}
 */
public class SubscribeConnectionTest extends SampleDataRepositoryTestCase {
	class TestTransport extends Transport implements PackTransport {
		protected TestTransport(URIish uri) {
			super(uri);
		}

		@Override
		public FetchConnection openFetch()
				throws NotSupportedException, TransportException {
			return null;
		}

		@Override
		public PushConnection openPush()
				throws NotSupportedException, TransportException {
			return null;
		}

		@Override
		public SubscribeConnection openSubscribe()
				throws NotSupportedException, TransportException {
			BasePackSubscribeConnection c = new BasePackSubscribeConnection(
					this) {
				@Override
				public void doSubscribeAdvertisment(
						Subscriber s) throws IOException {
					// Nothing
				}
				
				@Override
				public void doSubscribe(Subscriber s, Map<
						String,
						List<SubscribeCommand>> subscribeCommands,
						ProgressMonitor monitor)
						throws InterruptedException, TransportException,
						IOException {
					init(new ByteArrayInputStream(publisherOut.toByteArray()),
							testOut);
					super.doSubscribe(s, subscribeCommands, monitor);
				}
			};
			return c;
		}

		@Override
		public void close() {
			// Nothing
		}
	}

	Subscriber subscriber;

	TransportProtocol newProtocol;

	// the output of the subscribe connection
	ByteArrayOutputStream testOut;

	ByteArrayInputStream testIn;

	PacketLineIn testLineIn;

	// the output of the progress monitor
	ByteArrayOutputStream progressOut;

	ByteArrayInputStream progressIn;

	// the faked publisher response
	ByteArrayOutputStream publisherOut;

	PacketLineOut publisherLineOut;

	ProgressMonitor progressMonitor;

	PubSubConfig.Publisher publisherConfig;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		// Set up refs/remotes/ by doing a local fetch
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "self");
		rc.addURI(new URIish(db.getWorkTree().getAbsolutePath()));
		rc.addFetchRefSpec(new RefSpec("refs/heads/*:refs/remotes/origin/*"));
		rc.update(fc);
		fc.save();
		new Git(db).fetch().setRemote("self").call();

		// Add fetch specs to origin
		String directory = db.getDirectory().getAbsolutePath();
		fc = db.getConfig();
		fc.load();
		rc = new RemoteConfig(fc, "origin");
		rc.addFetchRefSpec(
				new RefSpec("refs/heads/master:refs/remotes/origin/master"));
		rc.addFetchRefSpec(new RefSpec("refs/tags/*:refs/tags/*"));
		rc.addURI(new URIish("http://example.com/testrepository"));
		rc.update(fc);
		fc.save();
		publisherConfig = new PubSubConfig.Publisher("http://example.com/");
		PubSubConfig.Subscriber subscribeConfig = new PubSubConfig.Subscriber(
				publisherConfig, "origin", directory);
		publisherConfig.addSubscriber(subscribeConfig);

		publisherOut = new ByteArrayOutputStream();
		publisherLineOut = new PacketLineOut(publisherOut);
		testOut = new ByteArrayOutputStream();
		progressOut = new ByteArrayOutputStream();
		progressMonitor = new TextProgressMonitor(new OutputStreamWriter(
				progressOut));

		final TestTransport transport = new TestTransport(publisherConfig
				.getUri());

		newProtocol = new TransportProtocol() {
			@Override
			public String getName() {
				return "http-test";
			}

			@Override
			public boolean canHandle(
					URIish uri, Repository local, String remoteName) {
				return true;
			}

			@Override
			public Transport open(URIish uri)
					throws NotSupportedException, TransportException {
				return transport;
			}

			@Override
			public Transport open(
					URIish uri, Repository local, String remoteName)
					throws NotSupportedException, TransportException {
				return transport;
			}
		};

		Transport.register(newProtocol);
		subscriber = new Subscriber(publisherConfig.getUri());
		subscriber.setTimeout(1000);
	}

	@After
	@Override
	public void tearDown() throws Exception {
		Transport.unregister(newProtocol);
		super.tearDown();
	}

	@Test
	public void testBadRestart() throws Exception {
		// Setup client
		subscriber.setRestartToken("badtoken");
		subscriber.setLastPackNumber("0");
		// Setup server response
		publisherLineOut.writeString("reconnect");
		try {
			executeSubscribe();
		} catch (TransportException e) {
			assertEquals("Invalid restart token", e.getMessage());
		}

		// Check subscribe output
		assertEquals("restart badtoken", testLineIn.readString());
		assertEquals("last-pack 0", testLineIn.readString());
		assertEquals(PacketLineIn.END, testLineIn.readString());
		assertEquals("repository testrepository", testLineIn.readString());
		assertEquals("want refs/heads/master", testLineIn.readString());
		assertEquals("want refs/tags/*", testLineIn.readString());
		String line;
		while ((line = testLineIn.readString()) != PacketLineIn.END) {
			assertTrue(line.startsWith("have "));
		}
		assertEquals("done", testLineIn.readString());
	}

	@Test
	public void testCleanStart() throws Exception {
		// Setup server response
		publisherLineOut.writeString("restart-token server-token");
		publisherLineOut.writeString("heartbeat-interval 10");
		publisherLineOut.end();
		writeHeartbeat();
		try {
			executeSubscribe();
		} catch (TransportException e) {
			throw e;
		} catch (IOException e) {
			// Stream timeout
		}
		assertEquals("server-token", subscriber.getRestartToken());
		assertTrue(null == subscriber.getLastPackNumber());
	}

	@Test
	public void testChangeToken() throws Exception {
		// Setup server response
		publisherLineOut.writeString("restart-token server-token");
		publisherLineOut.writeString("heartbeat-interval 10");
		publisherLineOut.end();
		writeHeartbeat();
		publisherLineOut.writeString("change-restart-token new-server-token");
		try {
			executeSubscribe();
		} catch (TransportException e) {
			throw e;
		} catch (IOException e) {
			// Stream timeout
		}
		assertEquals("new-server-token", subscriber.getRestartToken());
		assertTrue(null == subscriber.getLastPackNumber());
	}

	@Test
	public void testSingleUpdate() throws Exception {
		// Setup server response
		publisherLineOut.writeString("restart-token server-token");
		publisherLineOut.writeString("heartbeat-interval 10");
		publisherLineOut.end();
		writeHeartbeat();
		// Add refs/tags/pubsubtest
		ObjectId id = db.getRef("refs/heads/master").getLeaf().getObjectId();
		writeUpdate(ObjectId.zeroId(), id, "refs/tags/pubsubtest", "1234");

		try {
			executeSubscribe();
		} catch (InterruptedIOException e) {
			// Stream timeout
		}
		String tagId = db.getRef("refs/pubsub/origin/tags/pubsubtest")
				.getLeaf().getObjectId().name();
		assertEquals(id.name(), tagId);
		assertEquals("1234", subscriber.getLastPackNumber());
	}

	@Test
	public void testMultiUpdate() throws Exception {
		// Setup server response
		publisherLineOut.writeString("restart-token server-token");
		publisherLineOut.writeString("heartbeat-interval 10");
		publisherLineOut.end();
		// Create refs/heads/pubsub1
		ObjectId id1 = db.getRef("refs/heads/master").getLeaf().getObjectId();
		writeUpdate(ObjectId.zeroId(), id1, "refs/heads/pubsub1", "1234");

		writeHeartbeat();
		writeHeartbeat();

		// Create refs/heads/pubsub2
		ObjectId id2 = db.getRef("refs/heads/b").getLeaf().getObjectId();
		writeUpdate(ObjectId.zeroId(), id2, "refs/heads/pubsub2", "5678");

		try {
			executeSubscribe();
		} catch (InterruptedIOException e) {
			// Stream timeout
		}
		String pubsubId1 = db.getRef("refs/pubsub/origin/heads/pubsub1")
				.getLeaf().getObjectId().name();
		assertEquals(id1.name(), pubsubId1);
		String pubsubId2 = db.getRef("refs/pubsub/origin/heads/pubsub2")
				.getLeaf().getObjectId().name();
		assertEquals(id2.name(), pubsubId2);
		assertEquals("5678", subscriber.getLastPackNumber());
	}

	@Test
	public void testBadUpdate() throws Exception {
		// Setup server response
		publisherLineOut.writeString("restart-token server-token");
		publisherLineOut.writeString("heartbeat-interval 10");
		publisherLineOut.end();
		writeHeartbeat();
		// Add refs/heads/master (bad command, master already exists)
		ObjectId id = db.getRef("refs/heads/master").getLeaf().getObjectId();
		writeUpdate(ObjectId.zeroId(), id, "refs/heads/master", "1234");

		try {
			executeSubscribe();
			fail("Should have failed creating refs/heads/master");
		} catch (TransportException e) {
			// Expected
		} catch (InterruptedIOException e) {
			// Stream timeout
		}
	}

	private void writeHeartbeat() throws IOException {
		publisherLineOut.writeString("heartbeat");
	}

	private void writeUpdate(
			ObjectId from, ObjectId to, String refName, String sequence)
			throws IOException {
		publisherLineOut.writeString("update testrepository");
		publisherLineOut.writeString(
				from.name() + " " + to.name() + " " + refName);
		publisherLineOut.end();
		PackWriter pw = new PackWriter(db);
		pw.setThin(true);
		pw.preparePack(NullProgressMonitor.INSTANCE,
				new HashSet<ObjectId>(Collections.nCopies(1, to)),
				new HashSet<ObjectId>(Collections.nCopies(1, from)));
		pw.writePack(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE,
				publisherOut);
		pw.release();
		publisherLineOut.writeString("sequence " + sequence);
	}

	private void executeSubscribe() throws Exception {
		try {
			subscriber.subscribe(
					subscriber.sync(publisherConfig), progressMonitor);
		} catch (EOFException e) {
			// Nothing, end of stream
		} finally {
			testIn = new ByteArrayInputStream(testOut.toByteArray());
			testLineIn = new PacketLineIn(testIn);
			progressIn = new ByteArrayInputStream(progressOut.toByteArray());
		}
	}
}
