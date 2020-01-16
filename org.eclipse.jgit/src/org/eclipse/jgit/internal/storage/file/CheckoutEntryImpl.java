/*
 * Copyright (C) 2011-2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.ReflogEntry;

/**
 * Parsed information about a checkout.
 */
public class CheckoutEntryImpl implements CheckoutEntry {
	static final String CHECKOUT_MOVING_FROM = "checkout: moving from "; //$NON-NLS-1$

	private String from;

	private String to;

	CheckoutEntryImpl(ReflogEntry reflogEntry) {
		String comment = reflogEntry.getComment();
		int p1 = CHECKOUT_MOVING_FROM.length();
		int p2 = comment.indexOf(" to ", p1); //$NON-NLS-1$
		int p3 = comment.length();
		from = comment.substring(p1,p2);
		to = comment.substring(p2 + " to ".length(), p3); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public String getFromBranch() {
		return from;
	}

	/** {@inheritDoc} */
	@Override
	public String getToBranch() {
		return to;
	}
}
