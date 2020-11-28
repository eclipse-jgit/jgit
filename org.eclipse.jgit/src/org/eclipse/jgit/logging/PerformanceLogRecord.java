/*
 * Copyright (c) 2020, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.logging;

/**
 * Class to register a performance log record.
 *
 * @since 5.10
 */
public class PerformanceLogRecord {
	/** Name of the recorded event. */
	private String name;

	/** Duration of the recorded event in milliseconds. */
	private long durationMs;

	/**
	 * Create a new performance log record for an event.
	 *
	 * @param name
	 *            name of the event.
	 * @param durationMs
	 *            duration in milliseconds of the event.
	 */
	public PerformanceLogRecord(String name, long durationMs) {
		this.name = name;
		this.durationMs = durationMs;
	}

	/**
	 * Get the name of the recorded event.
	 *
	 * @return name of the recorded event.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the duration in milliseconds of the recorded event.
	 *
	 * @return duration in milliseconds of the recorded event.
	 */
	public long getDurationMs() {
		return durationMs;
	}
}