/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * A simple stopwatch which measures elapsed CPU time of the current thread. CPU
 * time is the time spent on executing your own code plus the time spent on
 * executing operating system calls triggered by your application.
 * <p>
 * This stopwatch needs a VM which supports getting CPU Time information for the
 * current thread. The static method createInstance() will take care to return
 * only a new instance of this class if the VM is capable of returning CPU time.
 */
public class CPUTimeStopWatch {
	private long start;

	private static ThreadMXBean mxBean=ManagementFactory.getThreadMXBean();

	/**
	 * use this method instead of the constructor to be sure that the underlying
	 * VM provides all features needed by this class.
	 *
	 * @return a new instance of {@link #CPUTimeStopWatch()} or
	 *         <code>null</code> if the VM does not support getting CPU time
	 *         information
	 */
	public static CPUTimeStopWatch createInstance() {
		return mxBean.isCurrentThreadCpuTimeSupported() ? new CPUTimeStopWatch()
				: null;
	}

	/**
	 * Starts the stopwatch. If the stopwatch is already started this will
	 * restart the stopwatch.
	 */
	public void start() {
		start = mxBean.getCurrentThreadCpuTime();
	}

	/**
	 * Stops the stopwatch and return the elapsed CPU time in nanoseconds.
	 * Should be called only on started stopwatches.
	 *
	 * @return the elapsed CPU time in nanoseconds. When called on non-started
	 *         stopwatches (either because {@link #start()} was never called or
	 *         {@link #stop()} was called after the last call to
	 *         {@link #start()}) this method will return 0.
	 */
	public long stop() {
		long cpuTime = readout();
		start = 0;
		return cpuTime;
	}

	/**
	 * Return the elapsed CPU time in nanoseconds. In contrast to
	 * {@link #stop()} the stopwatch will continue to run after this call.
	 *
	 * @return the elapsed CPU time in nanoseconds. When called on non-started
	 *         stopwatches (either because {@link #start()} was never called or
	 *         {@link #stop()} was called after the last call to
	 *         {@link #start()}) this method will return 0.
	 */
	public long readout() {
		return (start == 0) ? 0 : mxBean.getCurrentThreadCpuTime() - start;
	}
}
