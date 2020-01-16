/*
 * Copyright (C) 2012, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

/**
 * A convenient base class which provides empty method bodies for all
 * ProgressMonitor methods.
 * <p>
 * Could be used in scenarios when only some of the progress notifications are
 * important and others can be ignored.
 */
public abstract class EmptyProgressMonitor implements ProgressMonitor {

	/** {@inheritDoc} */
	@Override
	public void start(int totalTasks) {
		// empty
	}

	/** {@inheritDoc} */
	@Override
	public void beginTask(String title, int totalWork) {
		// empty
	}

	/** {@inheritDoc} */
	@Override
	public void update(int completed) {
		// empty
	}

	/** {@inheritDoc} */
	@Override
	public void endTask() {
		// empty
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCancelled() {
		return false;
	}

}
