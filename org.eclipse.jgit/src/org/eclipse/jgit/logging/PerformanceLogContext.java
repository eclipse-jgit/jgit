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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 
 * Singleton that collects performance logs.
 *
 * @since 5.10
 */
public class PerformanceLogContext {
	/** Singleton instance that stores the statistics. */
	private static final PerformanceLogContext INSTANCE = new PerformanceLogContext();

	/** List that stores events as performance logs. */
	private final ThreadLocal<List<PerformanceLogRecord>> eventRecords = new ThreadLocal<>();

	private PerformanceLogContext() {
	}

	/**
	 * Get the instance of the context.
	 *
	 * @return instance of performance log context.
	 */
	public static PerformanceLogContext getInstance() {
		return INSTANCE;
	}

	/**
	 * Get the unmodifiable list of events as performance records.
	 *
	 * @return unmodifiable list of events as performance logs.
	 */
	public List<PerformanceLogRecord> getEventRecords() {

		return Collections.unmodifiableList(eventRecords.get());
	}

	/**
	 * Get modifiable list of events as performance records.
	 *
	 * @return modifiable list of events as performance logs.
	 */
	public List<PerformanceLogRecord> getModifiableEventRecords() {
		if (eventRecords.get() == null) {
			eventRecords.set(new ArrayList<>());
		}
		return eventRecords.get();
	}

	/**
	 * Adds a performance log record to the current list of events.
	 *
	 * @param record
	 *            performance log record that is going to be added.
	 */
	public void addEvent(PerformanceLogRecord record) {
		getModifiableEventRecords().add(record);
	}

	/**
	 * Removes all of the existing records from the current list of events.
	 */
	public void cleanEvents() {
		eventRecords.get().clear();
	}
}