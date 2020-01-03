/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

		runOnThread(() -> {
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
		pm.pollForUpdates();
		assertEquals(43, mock.value);

		pm.update(2);
		pm.pollForUpdates();
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
			@Override
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

		@Override
		public void update(int completed) {
			value += completed;
		}

		@Override
		public void start(int totalTasks) {
			value = totalTasks;
		}

		@Override
		public void beginTask(String title, int totalWork) {
			taskTitle = title;
			value = totalWork;
		}

		@Override
		public void endTask() {
			taskTitle = null;
			value = 0;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}
	}
}
