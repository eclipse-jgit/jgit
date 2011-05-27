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

package org.eclipse.jgit.storage.dht.spi.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtTimeoutException;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/**
 * Abstract buffer service built on top of an ExecutorService.
 * <p>
 * Writes are combined together into batches, to reduce RPC overhead when there
 * are many small writes occurring. Batches are sent asynchronously when they
 * reach 512 KiB worth of key/column/value data. The calling application is
 * throttled when the outstanding writes are equal to the buffer size, waiting
 * until the cluster has replied with success or failure.
 * <p>
 * This buffer implementation is not thread-safe, it assumes only one thread
 * will use the buffer instance. (It does however correctly synchronize with the
 * background tasks it spawns.)
 */
public abstract class AbstractWriteBuffer implements WriteBuffer {
	private final static int AUTO_FLUSH_SIZE = 512 * 1024;

	private final ExecutorService executor;

	private final int bufferSize;

	private final List<Future<?>> running;

	private final Object runningLock;

	private final Semaphore spaceAvailable;

	private int queuedCount;

	private boolean flushing;

	private Callable<?> finalTask;

	/**
	 * Initialize a buffer with a backing executor service.
	 *
	 * @param executor
	 *            service to run mutation tasks on.
	 * @param bufferSize
	 *            maximum number of bytes to have pending at once.
	 */
	protected AbstractWriteBuffer(ExecutorService executor, int bufferSize) {
		this.executor = executor;
		this.bufferSize = bufferSize;
		this.running = new LinkedList<Future<?>>();
		this.runningLock = new Object();
		this.spaceAvailable = new Semaphore(bufferSize);
	}

	/**
	 * Notify the buffer data is being added onto it.
	 * <p>
	 * This method waits until the buffer has sufficient space for the requested
	 * data, thereby throttling the calling application code. It returns true if
	 * its recommendation is for the buffer subclass to copy the data onto its
	 * internal buffer and defer starting until later. It returns false if the
	 * recommendation is to start the operation immediately, due to the large
	 * size of the request.
	 * <p>
	 * Buffer implementors should keep in mind that the return value is offered
	 * as advice only, they may choose to implement different behavior.
	 *
	 * @param size
	 *            an estimated number of bytes that the buffer will be
	 *            responsible for until the operation completes. This should
	 *            include the row keys and column headers, in addition to the
	 *            data values.
	 * @return true to enqueue the operation; false to start it right away.
	 * @throws DhtException
	 *             the current thread was interrupted before space became
	 *             available in the buffer.
	 */
	protected boolean add(int size) throws DhtException {
		acquireSpace(size);
		return size < AUTO_FLUSH_SIZE;
	}

	/**
	 * Notify the buffer bytes were enqueued.
	 *
	 * @param size
	 *            the estimated number of bytes that were enqueued.
	 * @throws DhtException
	 *             a previously started operation completed and failed.
	 */
	protected void queued(int size) throws DhtException {
		queuedCount += size;

		if (AUTO_FLUSH_SIZE < queuedCount) {
			startQueuedOperations(queuedCount);
			queuedCount = 0;
		}
	}

	/**
	 * Start all queued operations.
	 * <p>
	 * This method is invoked by {@link #queued(int)} or by {@link #flush()}
	 * when there is a non-zero number of bytes already enqueued as a result of
	 * prior {@link #add(int)} and {#link {@link #queued(int)} calls.
	 * <p>
	 * Implementors should use {@link #start(Callable, int)} to begin their
	 * mutation tasks in the background.
	 *
	 * @param bufferedByteCount
	 *            number of bytes that were already enqueued. This count should
	 *            be passed to {@link #start(Callable, int)}.
	 * @throws DhtException
	 *             a previously started operation completed and failed.
	 */
	protected abstract void startQueuedOperations(int bufferedByteCount)
			throws DhtException;

	public void flush() throws DhtException {
		try {
			flushing = true;

			if (0 < queuedCount) {
				startQueuedOperations(queuedCount);
				queuedCount = 0;
			}

			// If a task was created above, try to use the current thread
			// instead of burning an executor thread for the final work.

			if (finalTask != null) {
				try {
					waitFor(finalTask);
				} finally {
					finalTask = null;
				}
			}

			synchronized (runningLock) {
				checkRunningTasks(true);
			}
		} finally {
			flushing = false;
		}
	}

	public void abort() throws DhtException {
		synchronized (runningLock) {
			checkRunningTasks(true);
		}
	}

	private void acquireSpace(int sz) throws DhtException {
		try {
			final int permits = permitsForSize(sz);
			if (spaceAvailable.tryAcquire(permits))
				return;

			if (0 < queuedCount) {
				startQueuedOperations(queuedCount);
				queuedCount = 0;
			}

			spaceAvailable.acquire(permits);
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		}
	}

	private int permitsForSize(int size) {
		// Do not acquire more than the configured buffer size,
		// even if the actual write size is larger. Trying to
		// acquire more would never succeed.

		if (size <= 0)
			size = 1;
		return Math.min(size, bufferSize);
	}

	/**
	 * Start a mutation task.
	 *
	 * @param <T>
	 *            any type the task might return.
	 * @param task
	 *            the mutation task. The result of the task is discarded, so
	 *            callers should perform result validation within the task.
	 * @param size
	 *            number of bytes that are buffered within the task.
	 * @throws DhtException
	 *             a prior task has completed, and failed.
	 */
	protected <T> void start(final Callable<T> task, int size)
			throws DhtException {
		final int permits = permitsForSize(size);
		final Callable<T> op = new Callable<T>() {
			public T call() throws Exception {
				try {
					return task.call();
				} finally {
					spaceAvailable.release(permits);
				}
			}
		};

		if (flushing && finalTask == null) {
			// If invoked by flush(), don't start on an executor.
			//
			finalTask = op;
			return;
		}

		synchronized (runningLock) {
			if (!flushing)
				checkRunningTasks(false);
			running.add(executor.submit(op));
		}
	}

	/**
	 * Wrap a callback to update the buffer.
	 * <p>
	 * Flushing the buffer will wait for the returned callback to complete.
	 *
	 * @param <T>
	 *            any type the task might return.
	 * @param callback
	 *            callback invoked when the task has finished.
	 * @param size
	 *            number of bytes that are buffered within the task.
	 * @return wrapped callback that will update the buffer state when the
	 *         callback is invoked.
	 * @throws DhtException
	 *             a prior task has completed, and failed.
	 */
	protected <T> AsyncCallback<T> wrap(final AsyncCallback<T> callback,
			int size) throws DhtException {
		int permits = permitsForSize(size);
		WrappedCallback<T> op = new WrappedCallback<T>(callback, permits);
		synchronized (runningLock) {
			checkRunningTasks(false);
			running.add(op);
		}
		return op;
	}

	private void checkRunningTasks(boolean wait) throws DhtException {
		if (running.isEmpty())
			return;

		Iterator<Future<?>> itr = running.iterator();
		while (itr.hasNext()) {
			Future<?> task = itr.next();
			if (task.isDone() || wait) {
				itr.remove();
				waitFor(task);
			}
		}
	}

	private static void waitFor(Callable<?> task) throws DhtException {
		try {
			task.call();
		} catch (DhtException err) {
			throw err;
		} catch (Exception err) {
			throw new DhtException(err);
		}
	}

	private static void waitFor(Future<?> task) throws DhtException {
		try {
			task.get();

		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);

		} catch (ExecutionException err) {

			Throwable t = err;
			while (t != null) {
				if (t instanceof DhtException)
					throw (DhtException) t;
				t = t.getCause();
			}

			throw new DhtException(err);
		}
	}

	private final class WrappedCallback<T> implements AsyncCallback<T>,
			Future<T> {
		private final AsyncCallback<T> callback;

		private final int permits;

		private final CountDownLatch sync;

		private volatile boolean done;

		WrappedCallback(AsyncCallback<T> callback, int permits) {
			this.callback = callback;
			this.permits = permits;
			this.sync = new CountDownLatch(1);
		}

		public void onSuccess(T result) {
			try {
				callback.onSuccess(result);
			} finally {
				done();
			}
		}

		public void onFailure(DhtException error) {
			try {
				callback.onFailure(error);
			} finally {
				done();
			}
		}

		private void done() {
			spaceAvailable.release(permits);
			done = true;
			sync.countDown();
		}

		public boolean cancel(boolean mayInterrupt) {
			return false;
		}

		public T get() throws InterruptedException, ExecutionException {
			sync.await();
			return null;
		}

		public T get(long time, TimeUnit unit) throws InterruptedException,
				ExecutionException, TimeoutException {
			sync.await(time, unit);
			return null;
		}

		public boolean isCancelled() {
			return false;
		}

		public boolean isDone() {
			return done;
		}
	}
}
