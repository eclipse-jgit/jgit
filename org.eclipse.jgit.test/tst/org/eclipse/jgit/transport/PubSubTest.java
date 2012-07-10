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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.PublisherSession.SessionGenerator;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for publish-subscribe.
 */
public class PubSubTest extends LocalDiskRepositoryTestCase {
	class TestTransport extends Transport implements PackTransport {
		PipedOutputStream clientOut;

		PipedInputStream clientIn;

		PipedOutputStream serverOut;

		PipedInputStream serverIn;

		public TestTransport(URIish uri) {
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
					this);
			clientOut = new PipedOutputStream();
			clientIn = new PipedInputStream();
			serverOut = new PipedOutputStream();
			serverIn = new PipedInputStream();

			try {
				clientOut.connect(serverIn);
				serverOut.connect(clientIn);
			} catch (IOException e) {
				throw new TransportException(e.getMessage(), e);
			}
			c.init(new BufferedInputStream(clientIn, 8192), clientOut);

			final PublisherClient pc = new PublisherClient(publisher);

			new Thread() {
				public void run() {
					try {
						pc.subscribe(serverIn, serverOut, null);
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						try {
							serverOut.close();
						} catch (IOException e) {
							// Nothing
						}
					}
				}
			}.start();

			return c;
		}

		@Override
		public void close() {
			// Nothing
		}

		public InputStream getIn() {
			return clientIn;
		}

		public OutputStream getOut() {
			return clientOut;
		}
	}

	class TestSubscriber extends Subscriber {
		final CountDownLatch updateLatch;

		public TestSubscriber(URIish uri, int expectedUpdates)
				throws IOException {
			super(uri);
			updateLatch = new CountDownLatch(expectedUpdates);
		}

		@Override
		public void setLastPackNumber(String number) {
			super.setLastPackNumber(number);
			updateLatch.countDown();
		}

		public void await() throws InterruptedException {
			updateLatch.await();
		}
	}

	PublisherBuffer buffer;

	PublisherPackFactory packFactory;

	Publisher publisher;

	Repository serverDb;

	Git serverGit;

	TransportProtocol testProtocol;

	volatile CountDownLatch connectLatch;

	/**
	 * Inject a new transport type used for testing.
	 */
	private void setUpTestTransport() {
		testProtocol = new TransportProtocol() {
			@Override
			public String getName() {
				return "test";
			}

			@Override
			public boolean canHandle(
					URIish uri, Repository local, String remoteName) {
				return true;
			}

			@Override
			public Transport open(URIish uri)
					throws NotSupportedException, TransportException {
				return open(uri, null, null);
			}

			@Override
			public Transport open(
					URIish uri, Repository local, String remoteName)
					throws NotSupportedException, TransportException {
				return new TestTransport(uri);
			}
		};

		Transport.register(testProtocol);
	}

	private void setUpPublisher(int clients) throws Exception {
		serverDb = createWorkRepository();
		serverGit = new Git(serverDb);
		connectLatch = new CountDownLatch(clients);
		new FileWriter("foo.txt").append('T').close();
		new Git(serverDb).add().addFilepattern("foo.txt").call();
		new Git(serverDb).commit().setAll(true).setMessage("Init").call();
		buffer = new PublisherBuffer(64 * 1024);
		packFactory = new PublisherPackFactory(buffer);
		packFactory.setSliceSize(1024);
		packFactory.setSliceMemoryThreshold(3);
		publisher = new Publisher(new PublisherReverseResolver(),
			new RepositoryResolver<PublisherClient>() {
			public Repository open(PublisherClient req, String name)
					throws RepositoryNotFoundException,
					ServiceNotAuthorizedException, ServiceNotEnabledException,
					ServiceMayNotContinueException {
				if (!name.equals("testrepository")) {
					fail("Invalid open database request");
				}
				return serverDb;
			}
		}, packFactory, new SessionGenerator() {
			private AtomicInteger sid = new AtomicInteger();

			public String generate() {
				return "fixed-session-id-" + sid.incrementAndGet();
			}
		}) {
			@Override
			public synchronized PublisherSession connectClient(
					PublisherClient c) {
				PublisherSession s = super.connectClient(c);
				connectLatch.countDown();
				return s;
			}
		};
		buffer.startGC();
	}

	private TestSubscriber createSubscriber(
			String[] fetchSpecs, int expectedUpdates) throws Exception {
		Repository db = setUpClientDb(fetchSpecs);
		final PubSubConfig.Publisher pubConfig = new PubSubConfig.Publisher(
				"test://test.com/");
		pubConfig.addSubscriber(new PubSubConfig.Subscriber(
				pubConfig, "origin", db.getDirectory().getAbsolutePath()));
		final TestSubscriber s = new TestSubscriber(new URIish(
				"test://test.com/"), expectedUpdates);
		Thread t = new Thread() {
			public void run() {
				try {
					s.subscribe(
							s.sync(pubConfig), NullProgressMonitor.INSTANCE);
				} catch (Exception e) {
					fail(e.getMessage());
				}
			}
		};
		t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread t1, Throwable e) {
				fail(e.getMessage());
			}
		});
		t.start();

		return s;
	}

	/**
	 * Create a new client database with a remote "origin" section.
	 *
	 * @param fetchSpecs
	 * @return db
	 * @throws Exception
	 */
	private Repository setUpClientDb(String[] fetchSpecs) throws Exception {
		// Set up remote config section
		FileRepository db = createWorkRepository();
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "origin");
		rc.addURI(new URIish("test://test.com/testrepository"));
		for (String spec : fetchSpecs)
			rc.addFetchRefSpec(new RefSpec(spec));
		rc.update(fc);
		fc.save();
		return db;
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		setUpTestTransport();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		Transport.unregister(testProtocol);
		new File("foo.txt").delete();
		super.tearDown();
	}

	@Test
	public void testSingleUpdate() throws Exception {
		setUpPublisher(1);
		TestSubscriber client1 = createSubscriber(new String[] {
				"refs/heads/*:refs/remotes/origin/*",
				"refs/tags/*:refs/tags/*" }, 2);
		// Wait for client subscribe thread to connect
		connectLatch.await();

		ObjectId update = triggerUpdate("refs/heads/master");
		// Wait for update to be pushed out to client
		client1.await();

		Repository r = client1.getRepository("testrepository").getRepository();
		Ref ref = r.getRef("refs/pubsub/origin/heads/master");
		assertEquals(update.getName(), ref.getObjectId().getName());
	}

	@Test
	public void testMultipleUpdates() throws Exception {
		setUpPublisher(1);
		TestSubscriber client1 = createSubscriber(new String[] {
				"refs/heads/*:refs/remotes/origin/*",
				"refs/tags/*:refs/tags/*" }, 5);
		// Wait for client subscribe thread to connect
		connectLatch.await();

		ObjectId master = triggerUpdate("refs/heads/master");
		ObjectId branch1 = createBranch(
				"refs/heads/master", "branch1");
		branch1 = triggerUpdate("refs/heads/branch1");
		master = triggerUpdate("refs/heads/master");
		// Wait for update to be pushed out to client
		client1.await();

		Repository r = client1.getRepository("testrepository").getRepository();
		Ref ref = r.getRef("refs/pubsub/origin/heads/master");
		assertEquals(master.getName(), ref.getObjectId().getName());
		ref = r.getRef("refs/pubsub/origin/heads/branch1");
		assertEquals(branch1.getName(), ref.getObjectId().getName());
	}

	@Test
	public void testMultipleClients() throws Exception {
		setUpPublisher(2);
		TestSubscriber client1 = createSubscriber(new String[] {
				"refs/heads/*:refs/remotes/origin/*",
				"refs/tags/*:refs/tags/*" }, 6);
		TestSubscriber client2 = createSubscriber(new String[] {
				"refs/heads/master:refs/remotes/origin/master" }, 3);
		// Wait for client subscribe thread to connect
		connectLatch.await();

		ObjectId master = triggerUpdate("refs/heads/master");
		ObjectId branch1 = createBranch(
				"refs/heads/master", "branch1");
		branch1 = triggerUpdate("refs/heads/branch1");
		master = triggerUpdate("refs/heads/master");
		ObjectId tag1 = createTag("refs/heads/branch1", "tag1");
		// Wait for update to be pushed out to client
		client1.await();
		client2.await();

		Repository r1 = client1.getRepository("testrepository").getRepository();
		Repository r2 = client2.getRepository("testrepository").getRepository();
		Ref ref = r1.getRef("refs/pubsub/origin/heads/master");
		assertEquals(master.getName(), ref.getObjectId().getName());
		ref = r1.getRef("refs/pubsub/origin/heads/branch1");
		assertEquals(branch1.getName(), ref.getObjectId().getName());
		ref = r1.getRef("refs/pubsub/origin/tags/tag1");
		assertEquals(tag1.getName(), ref.getObjectId().getName());
		ref = r2.getRef("refs/pubsub/origin/heads/master");
		assertEquals(master.getName(), ref.getObjectId().getName());
		ref = r2.getRef("refs/pubsub/origin/heads/branch1");
		assertNull(ref);
		ref = r2.getRef("refs/pubsub/origin/tags/tag1");
		assertNull(ref);
	}

	private ObjectId createBranch(String from, String branch) throws Exception {
		ObjectId id = serverGit.branchCreate()
				.setName(branch)
				.setStartPoint(from)
				.call()
				.getLeaf()
				.getObjectId();
		List<ReceiveCommand> updates = new ArrayList<ReceiveCommand>();
		updates.add(new ReceiveCommand(ObjectId.zeroId(), id, "refs/heads/"
				+ branch));
		publisher.onPush(serverDb, updates);
		return id;
	}

	private ObjectId createTag(String from, String tag) throws Exception {
		ObjectId start = serverDb.getRef(from).getObjectId();
		RevCommit commit = serverGit.log()
				.add(start)
				.call()
				.iterator()
				.next();
		ObjectId id = serverGit.tag()
				.setName(tag)
				.setObjectId(commit)
				.setMessage("pubsub tag")
				.call()
				.getLeaf()
				.getObjectId();
		List<ReceiveCommand> updates = new ArrayList<ReceiveCommand>();
		updates.add(
				new ReceiveCommand(ObjectId.zeroId(), id, "refs/tags/" + tag));
		publisher.onPush(serverDb, updates);
		return id;
	}

	/**
	 * Checkout ref on server, add an empty file, commit, trigger push hook.
	 *
	 * @param ref
	 * @return objectid of the added commit
	 * @throws Exception
	 */
	private ObjectId triggerUpdate(String ref) throws Exception {
		serverGit.checkout().setName(ref).call();
		new FileWriter("foo.txt").append('U').close();
		RevCommit commit = new Git(serverDb).commit()
				.setAll(true).setMessage("pubsub push").call();
		ObjectId parent = commit.getParentCount() > 0 ? commit.getParent(0)
				: ObjectId.zeroId();
		List<ReceiveCommand> updates = new ArrayList<ReceiveCommand>();
		updates.add(new ReceiveCommand(parent, commit.getId(), ref));
		publisher.onPush(serverDb, updates);
		return commit.getId();
	}
}
