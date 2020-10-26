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
 * Hook invoked at the end of monitored commands consuming the collected
 * performance logs.
 *
 * @since 5.10
 */
public interface PerformanceLogHook {
	/** A simple no-op hook. */
	PerformanceLogHook NOOP = (List<PerformanceLogRecord> eventRecords) -> {
		// Do nothing.
	};

	/**
	 * Notifies that a command is about to end.
	 *
	 * @param eventRecords
	 *            events list gathered for the executed command
	 */
	void onEndOfCommand(List<PerformanceLogRecord> eventRecords);
}