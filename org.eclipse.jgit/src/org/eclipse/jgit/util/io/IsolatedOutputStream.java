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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.JGitText;

/**
 * OutputStream isolated from interrupts.
 * <p>
 * Wraps an OutputStream to prevent interrupts during writes from being made
 * visible to that stream instance. This works around buggy or difficult
 * OutputStream implementations like JSch that cannot gracefully handle an
 * interrupt during write.
 * <p>
 * Every write (or flush) requires a context switch to another thread. Callers
 * should wrap this stream with {@code BufferedOutputStream} using a suitable
 * buffer size to amortize the cost of context switches.
 */
public class IsolatedOutputStream extends OutputStream {
	private final OutputStream dst;
	private final ExecutorService copier;

	/**
	 * Wraps an OutputStream.
	 *
	 * @param out
	 *            stream to send all writes to.
	 */
	public IsolatedOutputStream(OutputStream out) {
		dst = out;
		copier = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
				new SynchronousQueue<Runnable>(), new NamedThreadFactory());
	}

	@Override
	public void write(int ch) throws IOException {
		write(new byte[] { (byte) ch }, 0, 1);
	}

	@Override
	public void write(final byte[] buf, final int pos, final int cnt)
			throws IOException {
		checkClosed();
		execute(new Callable<Void>() {
			@Override
			public Void call() throws IOException {
				dst.write(buf, pos, cnt);
				return null;
			}
		});
	}

	@Override
	public void flush() throws IOException {
		checkClosed();
		execute(new Callable<Void>() {
			@Override
			public Void call() throws IOException {
				dst.flush();
				return null;
			}
		});
	}

	@Override
	public void close() throws IOException {
		if (!copier.isShutdown()) {
			try {
				/*
				 * If a prior write or flush stalled and the caller broke out of
				 * the wait with an interrupt, they could be trying to close a
				 * stream that is still in-use. execute throws an IOException
				 * due to the thread still being busy, and dst will leak.
				 */
				execute(new Callable<Void>() {
					@Override
					public Void call() throws IOException {
						dst.close();
						return null;
					}
				});
			} finally {
				copier.shutdown();
			}
		}
	}

	private void checkClosed() throws IOException {
		if (copier.isShutdown()) {
			throw new IOException(JGitText.get().closed);
		}
	}

	private void execute(Callable<Void> task) throws IOException {
		try {
			copier.submit(task).get();
		} catch (InterruptedException e) {
			throw interrupted(e);
		} catch (RejectedExecutionException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}
	}

	private static InterruptedIOException interrupted(InterruptedException c) {
		InterruptedIOException e = new InterruptedIOException();
		e.initCause(c);
		return e;
	}

	private static class NamedThreadFactory implements ThreadFactory {
		private static final AtomicInteger cnt = new AtomicInteger();

		@Override
		public Thread newThread(Runnable r) {
			int n = cnt.incrementAndGet();
			String name = IsolatedOutputStream.class.getSimpleName() + '-' + n;
			return new Thread(r, name);
		}
	}
}
