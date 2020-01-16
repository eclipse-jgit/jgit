/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

/**
 * Stops the driver loop of walker and finish with current results.
 *
 * @see org.eclipse.jgit.revwalk.filter.RevFilter
 */
public class StopWalkException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** Singleton instance for throwing within a filter. */
	public static final StopWalkException INSTANCE = new StopWalkException();

	private StopWalkException() {
		// Nothing.
	}
}
