/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.util.io;

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
	public StreamCopyThread(final InputStream i, final OutputStream o) {
		setName(Thread.currentThread().getName() + "-StreamCopy"); //$NON-NLS-1$
		src = i;
		dst = o;
		writeLock = new Object();
	}

	/**
	 * Request the thread to flush the output stream as soon as possible.
	 * <p>
	 * This is an asynchronous request to the thread. The actual flush will
	 * happen at some future point in time, when the thread wakes up to process
	 * the request.
	 */
	@Deprecated
	public void flush() {
		synchronized (writeLock) {
			interrupt();
		}
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
