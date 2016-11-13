/*
 * Copyright (C) 2016, Google Inc.
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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.internal.JGitText;

/**
 * OutputStream isolated from interrupts.
 * <p>
 * Wraps another OutputStream to prevent interrupts during writes from being
 * visible to the {@code dst} OutputStream. This works around buggy or difficult
 * OutputStream implementations like JSch that cannot gracefully handle an
 * interrupt during write.
 * <p>
 * This stream creates a minimal buffer to store written bytes before handing
 * off the buffer to {@code dst}.
 */
public class IsolatedOutputStream extends OutputStream {
	private final Copier copier;
	private final ReentrantLock lock;
	private final Condition writeReady;
	private final Condition writeDone;

	private final byte[] buffer = new byte[4096];
	private int len;
	private boolean flush;
	private boolean close;

	private boolean pending;
	private IOException err;

	/**
	 * Wraps an OutputStream.
	 *
	 * @param dst
	 *            stream to send all writes to.
	 */
	public IsolatedOutputStream(OutputStream dst) {
		lock = new ReentrantLock();
		writeReady = lock.newCondition();
		writeDone = lock.newCondition();

		copier = new Copier(dst);
		copier.start();
	}

	@Override
	public void write(int ch) throws IOException {
		try {
			lock.lockInterruptibly();
			try {
				checkClosed();
				checkError();
				writeIfFull();
				buffer[len++] = (byte) ch;
			} finally {
				lock.unlock();
			}
		} catch (InterruptedException e) {
			throw interrupted(e);
		}
	}

	@Override
	public void write(byte[] d, int o, int n) throws IOException {
		try {
			lock.lockInterruptibly();
			try {
				checkClosed();
				checkError();
				while (n > 0) {
					writeIfFull();
					int c = Math.min(n, buffer.length - len);
					System.arraycopy(d, o, buffer, len, c);
					len += c;
					o += c;
					n -= c;
				}
			} finally {
				lock.unlock();
			}
		} catch (InterruptedException e) {
			throw interrupted(e);
		}
	}

	@Override
	public void flush() throws IOException {
		try {
			lock.lockInterruptibly();
			try {
				checkClosed();
				checkError();
				flush = true;
				execute();
			} finally {
				lock.unlock();
			}
		} catch (InterruptedException e) {
			throw interrupted(e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			lock.lockInterruptibly();
			try {
				if (!close) {
					if (err != null) {
						// If a prior write or flush suffered an error the
						// stream state is unpredictable and Copier may not
						// be able to retry the failed operation. Clear state
						// so close can be attempted by caller's finally block.
						len = 0;
						flush = false;
					}
					close = true;
					execute();
				}
			} finally {
				lock.unlock();
			}
			copier.join();
		} catch (InterruptedException e) {
			throw interrupted(e);
		}
	}

	private void checkClosed() throws IOException {
		if (close) {
			throw new IOException(JGitText.get().closed);
		}
	}

	private void checkError() throws IOException {
		if (err != null) {
			IOException e = new IOException(err.getMessage());
			e.initCause(err);
			throw e;
		}
	}

	private void writeIfFull() throws InterruptedException, IOException {
		if (len == buffer.length) {
			execute();
		}
	}

	private void execute() throws InterruptedException, IOException {
		pending = true;
		writeReady.signal();
		do {
			writeDone.await();
		} while (pending);
		checkError();
	}

	private class Copier extends Thread {
		private final OutputStream dst;
		private boolean copy = true;

		Copier(OutputStream dst) {
			this.dst = dst;

			String outer = IsolatedOutputStream.class.getSimpleName();
			setName(outer + "-Copier"); //$NON-NLS-1$
		}

		@Override
		public void run() {
			lock.lock();
			try {
				while (copy) {
					writeReady.awaitUninterruptibly();
					if (pending) {
						try {
							copy();
						} catch (IOException e) {
							err = e;
						} finally {
							pending = false;
							writeDone.signal();
						}
					}
				}
			} finally {
				lock.unlock();
			}
		}

		private void copy() throws IOException {
			try {
				if (len > 0) {
					dst.write(buffer, 0, len);
					len = 0;
				}
				if (flush) {
					flush = false;
					dst.flush();
				}
			} finally {
				if (close) {
					copy = false;
					dst.close();
				}
			}
		}
	}

	private static InterruptedIOException interrupted(InterruptedException c) {
		InterruptedIOException e = new InterruptedIOException();
		e.initCause(c);
		return e;
	}
}
