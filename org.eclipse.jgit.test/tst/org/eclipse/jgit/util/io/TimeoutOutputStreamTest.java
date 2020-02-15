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
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TimeoutOutputStreamTest {
	private static final int timeout = 250;

	private PipedOutputStream out;

	private FullPipeInputStream in;

	private InterruptTimer timer;

	private TimeoutOutputStream os;

	private long start;

	@Before
	public void setUp() throws Exception {
		out = new PipedOutputStream();
		in = new FullPipeInputStream(out);
		timer = new InterruptTimer();
		os = new TimeoutOutputStream(out, timer);
		os.setTimeout(timeout);
	}

	@After
	public void tearDown() throws Exception {
		timer.terminate();
		for (Thread t : active())
			assertFalse(t instanceof InterruptTimer.AlarmThread);
	}

	@Test
	public void testTimeout_writeByte_Success1() throws IOException {
		in.free(1);
		os.write('a');
		in.want(1);
		assertEquals('a', in.read());
	}

	@Test
	public void testTimeout_writeByte_Success2() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		final byte[] act = new byte[exp.length];
		in.free(exp.length);
		os.write(exp[0]);
		os.write(exp[1]);
		os.write(exp[2]);
		in.want(exp.length);
		in.read(act);
		assertArrayEquals(exp, act);
	}

	@Test
	public void testTimeout_writeByte_Timeout() throws IOException {
		beginWrite();
		try {
			os.write('\n');
			fail("incorrectly write a byte");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
	}

	@Test
	public void testTimeout_writeBuffer_Success1() throws IOException {
		final byte[] exp = new byte[] { 'a', 'b', 'c' };
		final byte[] act = new byte[exp.length];
		in.free(exp.length);
		os.write(exp);
		in.want(exp.length);
		in.read(act);
		assertArrayEquals(exp, act);
	}

	@Test
	public void testTimeout_writeBuffer_Timeout() throws IOException {
		beginWrite();
		try {
			os.write(new byte[512]);
			fail("incorrectly wrote bytes");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
	}

	@Test
	public void testTimeout_flush_Success() throws IOException {
		final boolean[] called = new boolean[1];
		os = new TimeoutOutputStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				fail("should not have written");
			}

			@Override
			public void flush() throws IOException {
				called[0] = true;
			}
		}, timer);
		os.setTimeout(timeout);
		os.flush();
		assertTrue(called[0]);
	}

	@Test
	public void testTimeout_flush_Timeout() throws IOException {
		final boolean[] called = new boolean[1];
		os = new TimeoutOutputStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				fail("should not have written");
			}

			@Override
			public void flush() throws IOException {
				called[0] = true;
				for (;;) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						InterruptedIOException e1 = new InterruptedIOException();
						e1.initCause(e);
						throw e1;
					}
				}
			}
		}, timer);
		os.setTimeout(timeout);

		beginWrite();
		try {
			os.flush();
			fail("incorrectly flushed");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
		assertTrue(called[0]);
	}

	@Test
	public void testTimeout_close_Success() throws IOException {
		final boolean[] called = new boolean[1];
		os = new TimeoutOutputStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				fail("should not have written");
			}

			@Override
			public void close() throws IOException {
				called[0] = true;
			}
		}, timer);
		os.setTimeout(timeout);
		os.close();
		assertTrue(called[0]);
	}

	@Test
	public void testTimeout_close_Timeout() throws IOException {
		final boolean[] called = new boolean[1];
		os = new TimeoutOutputStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				fail("should not have written");
			}

			@Override
			public void close() throws IOException {
				called[0] = true;
				for (;;) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						InterruptedIOException e1 = new InterruptedIOException();
						e1.initCause(e);
						throw e1;
					}
				}
			}
		}, timer);
		os.setTimeout(timeout);

		beginWrite();
		try {
			os.close();
			fail("incorrectly closed");
		} catch (InterruptedIOException e) {
			// expected
		}
		assertTimeout();
		assertTrue(called[0]);
	}

	private void beginWrite() {
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

	private static final class FullPipeInputStream extends PipedInputStream {
		FullPipeInputStream(PipedOutputStream src) throws IOException {
			super(src);
			src.write(new byte[PIPE_SIZE]);
		}

		void want(int cnt) throws IOException {
			IO.skipFully(this, PIPE_SIZE - cnt);
		}

		void free(int cnt) throws IOException {
			IO.skipFully(this, cnt);
		}
	}
}
