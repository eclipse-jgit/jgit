/*
 * Copyright (c) 2020, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.hooks;

import org.eclipse.jgit.logging.PerformanceLogRecord;

import java.util.List;

/**
 * Hook invoked at the end of a monitored command to receive the collected
 * performance logs.
 * <p>
 * Implementors of the interface are responsible for associating the current
 * thread to a particular connection, if they need to also include connection
 * information.
 *
 * @since 5.10
 */
public interface PerformanceLogHook {
	/** A simple no-op hook. */
	PerformanceLogHook NULL = (List<PerformanceLogRecord> eventRecords) -> {
		// Do nothing.
	};

	/**
	 * Notifies the hook that a command is about to end.
	 *
	 * @param eventRecords
	 *            events list gathered for the executed command
	 */
	void onEndOfCommand(List<PerformanceLogRecord> eventRecords);
}
