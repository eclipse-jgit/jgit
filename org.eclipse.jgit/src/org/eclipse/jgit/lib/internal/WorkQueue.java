/*
 * Copyright (C) 2008-2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib.internal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * Simple work queue to run tasks in the background
 */
public class WorkQueue {
	private static final ScheduledThreadPoolExecutor executor;

	static final Object executorKiller;

	static {
		// To support garbage collection, start our thread but
		// swap out the thread factory. When our class is GC'd
		// the executorKiller will finalize and ask the executor
		// to shutdown, ending the worker.
		//
		int threads = 1;
		executor = new ScheduledThreadPoolExecutor(threads,
				new ThreadFactory() {
					private final ThreadFactory baseFactory = Executors
							.defaultThreadFactory();

					@Override
					public Thread newThread(Runnable taskBody) {
						Thread thr = baseFactory.newThread(taskBody);
						thr.setName("JGit-WorkQueue"); //$NON-NLS-1$
						thr.setContextClassLoader(null);
						thr.setDaemon(true);
						return thr;
					}
				});
		executor.setRemoveOnCancelPolicy(true);
		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		executor.prestartAllCoreThreads();

		// Now that the threads are running, its critical to swap out
		// our own thread factory for one that isn't in the ClassLoader.
		// This allows the class to GC.
		//
		executor.setThreadFactory(Executors.defaultThreadFactory());

		executorKiller = new Object() {
			@Override
			protected void finalize() {
				executor.shutdownNow();
			}
		};
	}

	/**
	 * Get the WorkQueue's executor
	 *
	 * @return the WorkQueue's executor
	 */
	public static ScheduledThreadPoolExecutor getExecutor() {
		return executor;
	}
}
