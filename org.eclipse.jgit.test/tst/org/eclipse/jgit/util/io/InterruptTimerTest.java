/*
 * Copyright (C) 2025, NIVIDIA CORPORATION
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InterruptTimerTest {
	private static final int MULTIPLIER = 1; // Increase if tests get flaky
	private static final int BUFFER = 5; // Increase if tests get flaky
	private static final int REPEATS = 100; // Increase to stress test more

	private static final int TOO_LONG = 3 * MULTIPLIER + BUFFER;
	private static final int SHORT_ENOUGH = 1 * MULTIPLIER;
	private static final int TIMEOUT_LONG_ENOUGH = TOO_LONG;
	private static final int TIMEOUT_TOO_SHORT = SHORT_ENOUGH;

	private InterruptTimer timer;

	@Before
	public void setUp() throws Exception {
		timer = new InterruptTimer();
	}

	@After
	public void tearDown() throws Exception {
		timer.terminate();
		for (Thread t : active())
			assertFalse(t instanceof InterruptTimer.AlarmThread);
	}

	@Test
	public void testShortEnough() throws IOException {
		int interrupted = 0;;
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
	public void testTooLong() throws IOException {
		int interrupted = 0;;
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
	public void testRepeatedShortEnough() throws IOException {
		int interrupted = 0;;
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
	public void testRepeatedTooLong() throws IOException {
		int interrupted = 0;;
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
	public void testRepeatedShortThanTooLong() throws IOException {
		int interrupted = 0;;
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
