/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper around the general {@link org.eclipse.jgit.lib.ProgressMonitor} to
 * make it thread safe.
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

	/** {@inheritDoc} */
	@Override
	public void start(int totalTasks) {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.start(totalTasks);
	}

	/** {@inheritDoc} */
	@Override
	public void beginTask(String title, int totalWork) {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.beginTask(title, totalWork);
	}

	/**
	 * Notify the monitor a worker is starting.
	 */
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

	/**
	 * Notify the monitor a worker is finished.
	 */
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
	 * @throws java.lang.InterruptedException
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

	/** {@inheritDoc} */
	@Override
	public void update(int completed) {
		if (0 == pendingUpdates.getAndAdd(completed))
			process.release();
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCancelled() {
		lock.lock();
		try {
			return pm.isCancelled();
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void endTask() {
		if (!isMainThread())
			throw new IllegalStateException();
		pm.endTask();
	}

	private boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}
}
