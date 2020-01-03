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

/**
 * Asynchronous operation handle.
 *
 * Callers that start an asynchronous operation are supplied with a handle that
 * may be used to attempt cancellation of the operation if the caller does not
 * wish to continue.
 */
public interface AsyncOperation {
	/**
	 * Cancels the running task.
	 *
	 * Attempts to cancel execution of this task. This attempt will fail if the
	 * task has already completed, already been cancelled, or could not be
	 * cancelled for some other reason. If successful, and this task has not
	 * started when cancel is called, this task should never run. If the task
	 * has already started, then the mayInterruptIfRunning parameter determines
	 * whether the thread executing this task should be interrupted in an
	 * attempt to stop the task.
	 *
	 * @param mayInterruptIfRunning
	 *            true if the thread executing this task should be interrupted;
	 *            otherwise, in-progress tasks are allowed to complete
	 * @return false if the task could not be cancelled, typically because it
	 *         has already completed normally; true otherwise
	 */
	boolean cancel(boolean mayInterruptIfRunning);

	/**
	 * Release resources used by the operation, including cancellation.
	 */
	void release();
}
