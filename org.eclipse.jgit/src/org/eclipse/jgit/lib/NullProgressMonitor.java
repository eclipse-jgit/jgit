/*
 * Copyright (C) 2009, Alex Blewitt <alex.blewitt@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

/**
 * A NullProgressMonitor does not report progress anywhere.
 */
public class NullProgressMonitor implements ProgressMonitor {
	/** Immutable instance of a null progress monitor. */
	public static final NullProgressMonitor INSTANCE = new NullProgressMonitor();

	private NullProgressMonitor() {
		// Do not let others instantiate
	}

	/** {@inheritDoc} */
	@Override
	public void start(int totalTasks) {
		// Do not report.
	}

	/** {@inheritDoc} */
	@Override
	public void beginTask(String title, int totalWork) {
		// Do not report.
	}

	/** {@inheritDoc} */
	@Override
	public void update(int completed) {
		// Do not report.
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCancelled() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public void endTask() {
		// Do not report.
	}
}
