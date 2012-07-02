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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.transport.Publisher.PublisherException;
import org.eclipse.jgit.transport.PublisherStream.Window;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class PublisherStreamTest {

	PublisherPushTest obj1 = new PublisherPushTest("1");

	PublisherPushTest obj2 = new PublisherPushTest("2");

	PublisherPushTest obj3 = new PublisherPushTest("3");

	PublisherPushTest obj4 = new PublisherPushTest("4");

	class PublisherPushTest extends PublisherPush {

		private boolean closed;

		public PublisherPushTest(String id) {
			super(null, null, id, null);
		}

		@Override
		public synchronized void close() throws PublisherException {
			closed = true;
		}

		public boolean isClosed() {
			return closed;
		}
	}

	PublisherStream list;

	@Before
	public void setUp() {
		list = new PublisherStream();
	}

	@Test
	public void testInsertion() throws Exception {
		Window it = getIterator(2);
		list.add(obj1);
		list.add(obj2);
		list.add(obj3);
		assertListEquals(it, new PublisherPushTest[] { obj1, obj2, obj3 });
	}

	@Test
	public void testCreation() throws Exception {
		list.add(obj1);
		Window it = getIterator(2);
		list.add(obj2);
		list.add(obj3);
		assertListEquals(it, new PublisherPushTest[] { obj2, obj3 });
	}

	@Test
	public void testPrepend() throws Exception {
		list.add(obj1);
		Window it = getIterator(2);
		PublisherPushTest p1 = new PublisherPushTest("p1");
		PublisherPushTest p2 = new PublisherPushTest("p2");
		it.prepend(p2);
		it.prepend(p1);
		list.add(obj2);
		list.add(obj3);
		assertListEquals(it, new PublisherPushTest[] { p1, p2, obj2, obj3 });
	}

	@Test
	public void testRollback() throws Exception {
		Window it = getIterator(2);
		list.add(obj1);
		list.add(obj2);
		list.add(obj3);
		assertEquals(obj1, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertEquals(obj2, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertTrue(it.rollback("1"));
		assertEquals(obj2, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertEquals(obj3, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertFalse(it.rollback("1"));
	}

	@Test
	public void testRollbackMidStream() throws Exception {
		list.add(obj1);
		Window it = getIterator(2);
		PublisherPushTest p1 = new PublisherPushTest("p1");
		PublisherPushTest p2 = new PublisherPushTest("p2");
		it.prepend(p2);
		it.prepend(p1);
		list.add(obj2);
		list.add(obj3);
		assertEquals(p1, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertEquals(p2, it.next(1, TimeUnit.SECONDS));
		it.mark();
		assertTrue(it.rollback("p1"));
		assertListEquals(it, new PublisherPushTest[] { p2, obj2, obj3 });
	}

	@Test
	public void testRefCount() throws Exception {
		Window it = getIterator(2);
		list.add(obj1);
		list.add(obj2);
		list.add(obj3);
		assertEquals(obj1, it.next(1, TimeUnit.SECONDS));
		assertEquals(obj2, it.next(1, TimeUnit.SECONDS));
		assertEquals(obj3, it.next(1, TimeUnit.SECONDS));
		assertTrue(obj1.isClosed());
		assertTrue(obj2.isClosed());
		assertFalse(obj3.isClosed());
	}

	@Test
	public void testRefCountIterDelete() throws Exception {
		Window it = getIterator(2);
		list.add(obj1);
		list.add(obj2);
		list.add(obj3);
		it.release();
		assertTrue(obj1.isClosed());
		assertTrue(obj2.isClosed());
		assertTrue(obj3.isClosed());
	}

	@Test
	public void testRefCountIterMarkDelete() throws Exception {
		Window it = getIterator(2);
		list.add(obj1);
		list.add(obj2);
		list.add(obj3);
		it.next(1, TimeUnit.SECONDS);
		it.mark();
		it.release();
		assertTrue(obj1.isClosed());
		assertTrue(obj2.isClosed());
		assertTrue(obj3.isClosed());
	}

	@Test
	public void testRefCountIterMarkDelete2() throws Exception {
		list.add(obj1);
		Window it = getIterator(2);
		PublisherPushTest p1 = new PublisherPushTest("p1");
		PublisherPushTest p2 = new PublisherPushTest("p2");
		it.prepend(p2);
		it.prepend(p1);
		list.add(obj2);
		list.add(obj3);
		it.next(1, TimeUnit.SECONDS);
		it.mark();
		it.next(1, TimeUnit.SECONDS);
		it.release();
		assertTrue(obj1.isClosed());
		assertTrue(obj2.isClosed());
		assertTrue(obj3.isClosed());
		assertTrue(p1.isClosed());
		assertTrue(p2.isClosed());
	}

	@Test
	public void testConcurrentInsertion() throws Exception {
		final List<PublisherPushTest> items = new ArrayList<
				PublisherPushTest>();
		for (int i = 0; i < 50; i++)
			items.add(new PublisherPushTest(String.valueOf(i)));
		Window it = getIterator(2);
		// Add items
		Thread produceThread = new Thread() {
			public void run() {
				for (int i = 0; i < 50; i++) {
					try {
						list.add(items.get(i));
					} catch (PublisherException e1) {
						// Nothing
					}
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						fail("Unexpected interruption");
					}
				}
			}
		};
		produceThread.start();

		for (int i = 0; i < 50; i++)
			assertEquals(items.get(i), it.next(1, TimeUnit.SECONDS));
		produceThread.join();
		assertEmpty(it);
	}

	@Test
	public void testConcurrentInsertionMultipleConsumers() throws Throwable {
		final List<PublisherPushTest> items = new ArrayList<
				PublisherPushTest>();
		for (int i = 0; i < 50; i++)
			items.add(new PublisherPushTest(String.valueOf(i)));
		final CountDownLatch startLatch = new CountDownLatch(2);
		// Add items
		final Thread produceThread = new Thread() {
			public void run() {
				try {
					startLatch.await();
				} catch (InterruptedException e1) {
					// Nothing
				}
				for (int i = 0; i < 50; i++) {
					try {
						list.add(items.get(i));
					} catch (PublisherException e1) {
						// Nothing
					}
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						fail("Unexpected interruption");
					}
				}
			}
		};
		final List<Throwable> threadExceptions = new ArrayList<Throwable>();
		List<Thread> consumeThreads = new ArrayList<Thread>();
		produceThread.start();
		for (int j = 0; j < 2; j++) {
			Thread t = new Thread() {
				public void run() {
					try {
						Window it = getIterator(2);
						startLatch.countDown();
						for (int i = 0; i < 50; i++) {
							assertEquals(
									items.get(i), it.next(1, TimeUnit.SECONDS));
						}
						while (produceThread.isAlive()) {
							// Nothing
						}
						assertEmpty(it);
					} catch (Exception e) {
						fail("Unexpected exception " + e);
					}
				}
			};
			t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				public void uncaughtException(Thread t2, Throwable e) {
					threadExceptions.add(e);
				}
			});
			t.start();
			consumeThreads.add(t);
		}
		produceThread.join();
		for (Thread t : consumeThreads)
			t.join();
		for (Throwable e : threadExceptions)
			throw e;
	}

	@Test
	public void testConsumeTimeout() throws Exception {
		Window it = getIterator(2);

		// Timeout of 1 second, add first item after 1.5 seconds
		Thread produceThread = new Thread() {
			public void run() {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					// Nothing
				}
				try {
					list.add(obj1);
				} catch (PublisherException e) {
					// Nothing
				}
			}
		};
		produceThread.start();
		assertNull(it.next(1, TimeUnit.SECONDS));
		assertEquals(obj1, it.next(1, TimeUnit.SECONDS));
		produceThread.join();
		assertEquals(null, it.next(1, TimeUnit.SECONDS));
		assertEmpty(it);
	}

	private Window getIterator(int capacity) {
		return list.newWindow(capacity);
	}

	private void assertListEquals(Window it, PublisherPushTest[] expected)
			throws InterruptedException, PublisherException {
		for (int i = 0; i < expected.length; i++)
			assertEquals(expected[i].getPushId(),
					it.next(1, TimeUnit.SECONDS).getPushId());
		assertEmpty(it);
	}

	private static void assertEmpty(Window it)
			throws PublisherException, InterruptedException {
		assertNull(it.next(1, TimeUnit.NANOSECONDS));
	}
}
