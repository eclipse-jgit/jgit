/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/**
 * Thread to copy from an input stream to an output stream.
 */
public class StreamCopyThread extends Thread {
	private static final int BUFFER_SIZE = 1024;

	private final InputStream src;

	private final OutputStream dst;

	private volatile boolean done;

	/** Lock held by flush to avoid interrupting a write. */
	private final Object writeLock;

	/**
	 * Create a thread to copy data from an input stream to an output stream.
	 *
	 * @param i
	 *            stream to copy from. The thread terminates when this stream
	 *            reaches EOF. The thread closes this stream before it exits.
	 * @param o
	 *            stream to copy into. The destination stream is automatically
	 *            closed when the thread terminates.
	 */
	public StreamCopyThread(InputStream i, OutputStream o) {
		setName(Thread.currentThread().getName() + "-StreamCopy"); //$NON-NLS-1$
		src = i;
		dst = o;
		writeLock = new Object();
	}

	/**
	 * Request that the thread terminate, and wait for it.
	 * <p>
	 * This method signals to the copy thread that it should stop as soon as
	 * there is no more IO occurring.
	 *
	 * @throws java.lang.InterruptedException
	 *             the calling thread was interrupted.
	 */
	public void halt() throws InterruptedException {
		for (;;) {
			join(250 /* milliseconds */);
			if (isAlive()) {
				done = true;
				interrupt();
			} else
				break;
		}
	}

	/** {@inheritDoc} */
	@Override
	public void run() {
		try {
			final byte[] buf = new byte[BUFFER_SIZE];
			boolean readInterrupted = false;
			for (;;) {
				try {
					if (readInterrupted) {
						synchronized (writeLock) {
							boolean interruptedAgain = Thread.interrupted();
							dst.flush();
							if (interruptedAgain) {
								interrupt();
							}
						}
						readInterrupted = false;
					}

					if (done)
						break;

					final int n;
					try {
						n = src.read(buf);
					} catch (InterruptedIOException wakey) {
						readInterrupted = true;
						continue;
					}
					if (n < 0)
						break;

					synchronized (writeLock) {
						boolean writeInterrupted = Thread.interrupted();
						dst.write(buf, 0, n);
						if (writeInterrupted) {
							interrupt();
						}
					}
				} catch (IOException e) {
					break;
				}
			}
		} finally {
			try {
				src.close();
			} catch (IOException e) {
				// Ignore IO errors on close
			}
			try {
				dst.close();
			} catch (IOException e) {
				// Ignore IO errors on close
			}
		}
	}
}
