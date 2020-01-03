/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import org.eclipse.jgit.revwalk.FooterKey;

/**
 * Frequently used constants in a Ketch system.
 */
public class KetchConstants {
	/**
	 * Default reference namespace holding {@link #ACCEPTED} and
	 * {@link #COMMITTED} references and the {@link #STAGE} sub-namespace.
	 */
	public static final String DEFAULT_TXN_NAMESPACE = "refs/txn/"; //$NON-NLS-1$

	/** Reference name holding the RefTree accepted by a follower. */
	public static final String ACCEPTED = "accepted"; //$NON-NLS-1$

	/** Reference name holding the RefTree known to be committed. */
	public static final String COMMITTED = "committed"; //$NON-NLS-1$

	/** Reference subdirectory holding proposed heads. */
	public static final String STAGE = "stage/"; //$NON-NLS-1$

	/** Footer containing the current term. */
	public static final FooterKey TERM = new FooterKey("Term"); //$NON-NLS-1$

	/** Section for Ketch configuration ({@code ketch}). */
	public static final String CONFIG_SECTION_KETCH = "ketch"; //$NON-NLS-1$

	/** Behavior for a replica ({@code remote.$name.ketch-type}) */
	public static final String CONFIG_KEY_TYPE = "ketch-type"; //$NON-NLS-1$

	/** Behavior for a replica ({@code remote.$name.ketch-commit}) */
	public static final String CONFIG_KEY_COMMIT = "ketch-commit"; //$NON-NLS-1$

	/** Behavior for a replica ({@code remote.$name.ketch-speed}) */
	public static final String CONFIG_KEY_SPEED = "ketch-speed"; //$NON-NLS-1$

	private KetchConstants() {
	}
}
