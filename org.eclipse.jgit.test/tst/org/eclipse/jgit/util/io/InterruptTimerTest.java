/*
 * Copyright (C) 2025, NVIDIA CORPORATION
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InterruptTimerTest {
	private static final int MULTIPLIER = 8; // Increase if tests get flaky
	private static final int BUFFER = 5; // Increase if tests get flaky
	private static final int REPEATS = 100; // Increase to stress test more

	private static final int SHORT_ENOUGH = 1;
	private static final int TOO_LONG = SHORT_ENOUGH * MULTIPLIER + BUFFER;
	private static final int TIMEOUT_LONG_ENOUGH = TOO_LONG;
	private static final int TIMEOUT_TOO_SHORT = SHORT_ENOUGH;

	private InterruptTimer timer;

	@Before
	public void setUp() {
		timer = new InterruptTimer();
	}

	@After
	public void tearDown() {
		timer.terminate();
		for (Thread t : active())
			assertFalse(t instanceof InterruptTimer.AlarmThread);
	}

	@Test
	public void testShortEnough() {
		int interrupted = 0;
		try {
			timer.begin(TIMEOUT_LONG_ENOUGH);
			Thread.sleep(SHORT_ENOUGH);
			timer.end();
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Not Interrupted", interrupted, 0);
	}

	@Test
	public void testTooLong() {
		int interrupted = 0;
		try {
			timer.begin(TIMEOUT_TOO_SHORT);
			Thread.sleep(TOO_LONG);
			timer.end();
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Interrupted", interrupted, 1);
	}

	@Test
	public void testNotInterruptedAfterEnd() {
		int interrupted = 0;
		try {
			timer.begin(TIMEOUT_LONG_ENOUGH);
			Thread.sleep(SHORT_ENOUGH);
			timer.end();
			Thread.sleep(TIMEOUT_LONG_ENOUGH * 3);
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Not Interrupted Even After End", interrupted, 0);
	}

	@Test
	public void testRestartBeforeTimeout() {
		int interrupted = 0;
		try {
			timer.begin(TIMEOUT_LONG_ENOUGH * 2);
			Thread.sleep(SHORT_ENOUGH);
			timer.end();
			timer.begin(TIMEOUT_LONG_ENOUGH);
			Thread.sleep(SHORT_ENOUGH);
			timer.end();
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Not Interrupted Even When Restarted Before Timeout", interrupted, 0);
	}

	@Test
	public void testSecondExpiresBeforeFirst() {
		int interrupted = 0;
		try {
			timer.begin(TIMEOUT_LONG_ENOUGH * 3);
			Thread.sleep(SHORT_ENOUGH);
			timer.end();
			timer.begin(TIMEOUT_TOO_SHORT);
			Thread.sleep(TOO_LONG);
			timer.end();
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Interrupted Even When Second Timeout Expired Before First", interrupted, 1);
	}

	@Test
	public void testRepeatedShortEnough() {
		int interrupted = 0;
		for (int i = 0; i < REPEATS; i++) {
			try {
				timer.begin(TIMEOUT_LONG_ENOUGH);
				Thread.sleep(SHORT_ENOUGH);
				timer.end();
			} catch (InterruptedException e) {
				interrupted++;
			}
		}
		assertEquals("Was Never Interrupted", interrupted, 0);
	}

	@Test
	public void testRepeatedTooLong() {
		int interrupted = 0;
		for (int i = 0; i < REPEATS; i++) {
			try {
				timer.begin(TIMEOUT_TOO_SHORT);
				Thread.sleep(TOO_LONG);
				timer.end();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				interrupted++;
			}
		}
		assertEquals("Was always Interrupted", interrupted, REPEATS);
	}

	@Test
	public void testRepeatedShortThanTooLong() {
		int interrupted = 0;
		for (int i = 0; i < REPEATS; i++) {
			try {
				timer.begin(TIMEOUT_LONG_ENOUGH);
				Thread.sleep(SHORT_ENOUGH);
				timer.end();
			} catch (InterruptedException e) {
				interrupted++;
			}
		}
		assertEquals("Was Not Interrupted Early", interrupted, 0);
		try {
			timer.begin(TIMEOUT_TOO_SHORT);
			Thread.sleep(TOO_LONG);
			timer.end();
		} catch (InterruptedException e) {
			interrupted++;
		}
		assertEquals("Was Interrupted On Long", interrupted, 1);
	}

	private static List<Thread> active() {
		Thread[] all = new Thread[16];
		int n = Thread.currentThread().getThreadGroup().enumerate(all);
		while (n == all.length) {
			all = new Thread[all.length * 2];
			n = Thread.currentThread().getThreadGroup().enumerate(all);
		}
		return Arrays.asList(all).subList(0, n);
	}
}
