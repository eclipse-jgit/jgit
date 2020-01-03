/*
 * Copyright (C) 2009-2010, Google Inc. and others
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
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeoutInputStreamTest {
	private static final int timeout = 250;

	private PipedOutputStream out;

	private PipedInputStream in;

	private InterruptTimer timer;

	private TimeoutInputStream is;

	private long start;

	@Before
	public void setUp() throws Exception {
		out = new PipedOutputStream();
		in = new PipedInputStream(out);
		timer = new InterruptTimer();
		is = new TimeoutInputStream(in, timer);
		is.setTimeout(timeout);
	}

	@After
	public void tearDown() throws Exception {
		timer.terminate();
		for (Thread t : active())
			assertFalse(t instanceof InterruptTimer.AlarmThread);
	}

	@Test
	public void testTimeout_readByte_Success1() throws IOException {
		out.write('a');
		assertEquals('a', is.read());
	}

	@Test
	public void testTimeout_readByte_Success2() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		out.write(exp);
		assertEquals(exp[0], is.read());
		assertEquals(exp[1], is.read());
		assertEquals(exp[2], is.read());
		out.close();
		assertEquals(-1, is.read());
	}

	@Test
	public void testTimeout_readByte_Timeout() throws IOException {
		beginRead();
		try {
			is.read();
			fail("incorrectly read a byte");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
	}

	@Test
	public void testTimeout_readBuffer_Success1() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		final byte[] act = new byte[exp.length];
		out.write(exp);
		IO.readFully(is, act, 0, act.length);
		assertArrayEquals(exp, act);
	}

	@Test
	public void testTimeout_readBuffer_Success2() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		final byte[] act = new byte[exp.length];
		out.write(exp);
		IO.readFully(is, act, 0, 1);
		IO.readFully(is, act, 1, 1);
		IO.readFully(is, act, 2, 1);
		assertArrayEquals(exp, act);
	}

	@Test
	public void testTimeout_readBuffer_Timeout() throws IOException {
		beginRead();
		try {
			IO.readFully(is, new byte[512], 0, 512);
			fail("incorrectly read bytes");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
	}

	@Test
	public void testTimeout_skip_Success() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		out.write(exp);
		assertEquals(2, is.skip(2));
		assertEquals('c', is.read());
	}

	@Test
	public void testTimeout_skip_Timeout() throws IOException {
		beginRead();
		try {
			is.skip(1024);
			fail("incorrectly skipped bytes");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
	}

	private void beginRead() {
		start = now();
	}

	private void assertTimeout() {
		// Our timeout was supposed to be ~250 ms. Since this is a timing
		// test we can't assume we spent *exactly* the timeout period, as
		// there may be other activity going on in the system. Instead we
		// look for the delta between the start and end times to be within
		// 50 ms of the expected timeout.
		//
		final long wait = now() - start;
		assertTrue("waited only " + wait + " ms", timeout - wait < 50);
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

	private static long now() {
		return System.currentTimeMillis();
	}
}
