/*
 * Copyright (C) 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Strict work monitor
 */
public final class StrictWorkMonitor implements ProgressMonitor {
	private int lastWork, totalWork;

	@Override
	public void start(int totalTasks) {
		// empty
	}

	@Override
	public void beginTask(String title, int total) {
		this.totalWork = total;
		lastWork = 0;
	}

	@Override
	public void update(int completed) {
		lastWork += completed;
	}

	@Override
	public void endTask() {
		assertEquals("Units of work recorded", totalWork, lastWork);
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public void showDuration(boolean enabled) {
		// not implemented
	}
}
