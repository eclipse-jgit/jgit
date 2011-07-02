/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dfs;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.util.IO;

final class ReadAheadTask implements Callable<Void> {
	private final DfsBlockCache cache;

	private final ReadableChannel channel;

	private final List<BlockFuture> futures;

	private boolean running;

	ReadAheadTask(DfsBlockCache cache, ReadableChannel channel,
			List<BlockFuture> futures) {
		this.cache = cache;
		this.channel = channel;
		this.futures = futures;
	}

	public Void call() {
		int idx = 0;
		try {
			synchronized (this) {
				if (channel.isOpen())
					running = true;
				else
					return null;
			}

			long position = channel.position();
			for (; idx < futures.size() && !Thread.interrupted(); idx++) {
				BlockFuture f = futures.get(idx);
				if (cache.contains(f.pack, f.start)) {
					f.done();
					continue;
				}

				if (position != f.start)
					channel.position(f.start);

				int size = (int) (f.end - f.start);
				byte[] buf = new byte[size];
				if (IO.read(channel, buf, 0, size) != size)
					throw new EOFException();

				cache.put(new DfsBlock(f.pack, f.start, buf));
				f.done();
				position = f.end;
			}
		} catch (IOException err) {
			// Ignore read-ahead errors. These will be caught later on.
		} finally {
			for (; idx < futures.size(); idx++)
				futures.get(idx).abort();
			close();
		}
		return null;
	}

	void abort() {
		for (BlockFuture f : futures)
			f.abort();

		synchronized (this) {
			if (!running)
				close();
		}
	}

	private synchronized void close() {
		try {
			if (channel.isOpen())
				channel.close();
		} catch (IOException err) {
			// Ignore close errors on a read-only channel.
		}
	}

	static final class TaskFuture extends java.util.concurrent.FutureTask<Void> {
		final ReadAheadTask task;

		TaskFuture(ReadAheadTask task) {
			super(task);
			this.task = task;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (super.cancel(mayInterruptIfRunning)) {
				task.abort();
				return true;
			}
			return false;
		}
	}

	/** A scheduled read-ahead block load. */
	static final class BlockFuture implements Future<Void> {
		private static enum State {
			PENDING, DONE, CANCELLED;
		}

		private volatile State state;

		private volatile Future<?> task;

		private final CountDownLatch latch;

		final DfsPackKey pack;

		final long start;

		final long end;

		BlockFuture(DfsPackKey key, long start, long end) {
			this.state = State.PENDING;
			this.latch = new CountDownLatch(1);
			this.pack = key;
			this.start = start;
			this.end = end;
		}

		synchronized void setTask(Future<?> task) {
			if (state == State.PENDING)
				this.task = task;
		}

		boolean contains(DfsPackKey want, long pos) {
			return pack == want && start <= pos && pos < end;
		}

		synchronized void done() {
			if (state == State.PENDING) {
				latch.countDown();
				state = State.DONE;
				task = null;
			}
		}

		synchronized void abort() {
			if (state == State.PENDING) {
				latch.countDown();
				state = State.CANCELLED;
				task = null;
			}
		}

		public boolean cancel(boolean mayInterruptIfRunning) {
			Future<?> t = task;
			if (t == null)
				return false;

			boolean r = t.cancel(mayInterruptIfRunning);
			abort();
			return r;
		}

		public Void get() throws InterruptedException, ExecutionException {
			latch.await();
			return null;
		}

		public Void get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException,
				TimeoutException {
			if (latch.await(timeout, unit))
				return null;
			else
				throw new TimeoutException();
		}

		public boolean isCancelled() {
			State s = state;
			if (s == State.DONE)
				return false;
			if (s == State.CANCELLED)
				return true;

			Future<?> t = task;
			return t != null ? t.isCancelled() : true;
		}

		public boolean isDone() {
			return state == State.DONE;
		}
	}
}
