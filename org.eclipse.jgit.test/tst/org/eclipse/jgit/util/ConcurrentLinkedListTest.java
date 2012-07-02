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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.util.ConcurrentLinkedList.ConcurrentIterator;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class ConcurrentLinkedListTest {

	ConcurrentLinkedList<Integer> list;

	@Before
	public void setUp() {
		list = new ConcurrentLinkedList<Integer>();
	}

	@Test
	public void testInsertion() throws Exception {
		list.put(10);
		list.put(20);
		list.put(30);
		assertListEquals(new int[] { 10, 20, 30 });
	}

	@Test
	public void testDeletion() throws Exception {
		testInsertion();
		Iterator<Integer> it = list.getWriteIterator();
		int items = 0;
		while (it.hasNext()) {
			it.next();
			it.remove();
			items++;
		}
		assertEquals(3, items);
		// The tail node shouldn't be removed
		assertListEquals(new int[] { 30 });
	}

	@Test
	public void testConcurrentInsertion() throws Exception {
		// Add items
		Thread produceThread = new Thread() {
			public void run() {
				for (int i = 0; i < 50; i++) {
					list.put(i);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						fail("Unexpected interruption");
					}
				}
			}
		};
		produceThread.start();
		ConcurrentIterator<Integer> it = list.getHeadIterator();
		for (int i = 0; i < 50; i++) {
			assertEquals(new Integer(i), it.next(1, TimeUnit.SECONDS));
		}
		produceThread.join();
		assertTrue(!it.hasNext());
	}

	@Test
	public void testConcurrentInsertionMultipleConsumers() throws Throwable {
		// Add items
		final Thread produceThread = new Thread() {
			public void run() {
				for (int i = 0; i < 50; i++) {
					list.put(i);
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
					ConcurrentIterator<Integer> it = list.getHeadIterator();
					for (int i = 0; i < 50; i++) {
						try {
							assertEquals(new Integer(i),
									it.next(1, TimeUnit.SECONDS));
						} catch (InterruptedException e) {
							fail("Unexpected interruption");
						} catch (TimeoutException e) {
							fail("Unexpected timeout");
						}
					}
					while (produceThread.isAlive()) {
						// Nothing
					}
					assertTrue(!it.hasNext());
				}
			};
			t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable e) {
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
		// Timeout of 1 second, add first item after 1.5 seconds
		Thread produceThread = new Thread() {
			public void run() {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					// Nothing
				}
				list.put(1);
			}
		};
		produceThread.start();
		ConcurrentIterator<Integer> it = list.getHeadIterator();
		try {
			it.next(1, TimeUnit.SECONDS);
			fail("Should have timed out");
		} catch (TimeoutException e) {
			// Expected
		}
		assertEquals(new Integer(1), it.next(1, TimeUnit.SECONDS));
		produceThread.join();
		assertTrue(!it.hasNext());
	}

	private void assertListEquals(int[] expected)
			throws InterruptedException, TimeoutException {
		ConcurrentIterator<Integer> it = list.getHeadIterator();
		for (int i = 0; i < expected.length; i++)
			assertEquals(
					new Integer(expected[i]), it.next(1, TimeUnit.SECONDS));
		assertTrue(!it.hasNext());
	}
}
