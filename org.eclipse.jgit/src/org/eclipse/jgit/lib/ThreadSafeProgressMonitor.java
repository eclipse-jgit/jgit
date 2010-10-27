/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper around the general {@link ProgressMonitor} to make it thread safe.
 *
 * Updates to the underlying ProgressMonitor are made only from the thread that
 * allocated this wrapper. Callers are responsible for ensuring the allocating
 * thread uses {@link #pollForUpdates()} or {@link #waitForCompletion()} to
 * update the underlying ProgressMonitor.
 *
 * Only {@link #update(int)}, {@link #isCancelled()}, and {@link #endWorker()}
 * may be invoked from a worker thread. All other methods of the ProgressMonitor
 * interface can only be called from the thread that allocates this wrapper.
 */
public class ThreadSafeProgressMonitor implements ProgressMonitor {
	private final ProgressMonitor pm;

	private final ReentrantLock lock;

	private final Thread mainThread;

	private final AtomicInteger workers;

	private final AtomicInteger pendingUpdates;

	private final Semaphore process;

	/**
	 * Wrap a ProgressMonitor to be thread safe.
	 *
	 * @param pm
	 *            the underlying monitor to receive events.
	 */
	public ThreadSafeProgressMonitor(ProgressMonitor pm) {
		this.pm = pm;
		this.lock = new ReentrantLock();
		this.mainThread = Thread.currentThread();
		this.workers = new AtomicInteger(0);
		this.pendingUpdates = new AtomicInteger(0);
		this.process = new Semaphore(0);
	}

	public void start(int totalTasks) {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.start(totalTasks);
	}

	public void beginTask(String title, int totalWork) {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.beginTask(title, totalWork);
	}

	/** Notify the monitor a worker is starting. */
	public void startWorker() {
		startWorkers(1);
	}

	/**
	 * Notify the monitor of workers starting.
	 *
	 * @param count
	 *            the number of worker threads that are starting.
	 */
	public void startWorkers(int count) {
		workers.addAndGet(count);
	}

	/** Notify the monitor a worker is finished. */
	public void endWorker() {
		if (workers.decrementAndGet() == 0)
			process.release();
	}

	/**
	 * Non-blocking poll for pending updates.
	 *
	 * This method can only be invoked by the same thread that allocated this
	 * ThreadSafeProgressMonior.
	 */
	public void pollForUpdates() {
		assert isMainThread();
		doUpdates();
	}

	/**
	 * Process pending updates and wait for workers to finish.
	 *
	 * This method can only be invoked by the same thread that allocated this
	 * ThreadSafeProgressMonior.
	 *
	 * @throws InterruptedException
	 *             if the main thread is interrupted while waiting for
	 *             completion of workers.
	 */
	public void waitForCompletion() throws InterruptedException {
		assert isMainThread();
		while (0 < workers.get()) {
			doUpdates();
			process.acquire();
		}
		doUpdates();
	}

	private void doUpdates() {
		int cnt = pendingUpdates.getAndSet(0);
		if (0 < cnt)
			pm.update(cnt);
	}

	public void update(int completed) {
		int old = pendingUpdates.getAndAdd(completed);
		if (isMainThread())
			doUpdates();
		else if (old == 0)
			process.release();
	}

	public boolean isCancelled() {
		lock.lock();
		try {
			return pm.isCancelled();
		} finally {
			lock.unlock();
		}
	}

	public void endTask() {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.endTask();
	}

	private boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}
}
