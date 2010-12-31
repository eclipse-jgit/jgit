/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ThreadSafeProgressMonitorTest {
	@Test
	public void testFailsMethodsOnBackgroundThread()
			throws InterruptedException {
		final MockProgressMonitor mock = new MockProgressMonitor();
		final ThreadSafeProgressMonitor pm = new ThreadSafeProgressMonitor(mock);

		runOnThread(new Runnable() {
			public void run() {
				try {
					pm.start(1);
					fail("start did not fail on background thread");
				} catch (IllegalStateException notMainThread) {
					// Expected result
				}

				try {
					pm.beginTask("title", 1);
					fail("beginTask did not fail on background thread");
				} catch (IllegalStateException notMainThread) {
					// Expected result
				}

				try {
					pm.endTask();
					fail("endTask did not fail on background thread");
				} catch (IllegalStateException notMainThread) {
					// Expected result
				}
			}
		});

		// Ensure we didn't alter the mock above when checking threads.
		assertNull(mock.taskTitle);
		assertEquals(0, mock.value);
	}

	@Test
	public void testMethodsOkOnMainThread() {
		final MockProgressMonitor mock = new MockProgressMonitor();
		final ThreadSafeProgressMonitor pm = new ThreadSafeProgressMonitor(mock);

		pm.start(1);
		assertEquals(1, mock.value);

		pm.beginTask("title", 42);
		assertEquals("title", mock.taskTitle);
		assertEquals(42, mock.value);

		pm.update(1);
		assertEquals(43, mock.value);

		pm.update(2);
		assertEquals(45, mock.value);

		pm.endTask();
		assertNull(mock.taskTitle);
		assertEquals(0, mock.value);
	}

	@Test
	public void testUpdateOnBackgroundThreads() throws InterruptedException {
		final MockProgressMonitor mock = new MockProgressMonitor();
		final ThreadSafeProgressMonitor pm = new ThreadSafeProgressMonitor(mock);

		pm.startWorker();

		final CountDownLatch doUpdate = new CountDownLatch(1);
		final CountDownLatch didUpdate = new CountDownLatch(1);
		final CountDownLatch doEndWorker = new CountDownLatch(1);

		final Thread bg = new Thread() {
			public void run() {
				assertFalse(pm.isCancelled());

				await(doUpdate);
				pm.update(2);
				didUpdate.countDown();

				await(doEndWorker);
				pm.update(1);
				pm.endWorker();
			}
		};
		bg.start();

		pm.pollForUpdates();
		assertEquals(0, mock.value);
		doUpdate.countDown();

		await(didUpdate);
		pm.pollForUpdates();
		assertEquals(2, mock.value);

		doEndWorker.countDown();
		pm.waitForCompletion();
		assertEquals(3, mock.value);
	}

	private static void await(CountDownLatch cdl) {
		try {
			assertTrue("latch released", cdl.await(1000, TimeUnit.MILLISECONDS));
		} catch (InterruptedException ie) {
			fail("Did not expect to be interrupted");
		}
	}

	private static void runOnThread(Runnable task) throws InterruptedException {
		Thread t = new Thread(task);
		t.start();
		t.join(1000);
		assertFalse("thread has stopped", t.isAlive());
	}

	private static class MockProgressMonitor implements ProgressMonitor {
		String taskTitle;

		int value;

		public void update(int completed) {
			value += completed;
		}

		public void start(int totalTasks) {
			value = totalTasks;
		}

		public void beginTask(String title, int totalWork) {
			taskTitle = title;
			value = totalWork;
		}

		public void endTask() {
			taskTitle = null;
			value = 0;
		}

		public boolean isCancelled() {
			return false;
		}
	}
}
