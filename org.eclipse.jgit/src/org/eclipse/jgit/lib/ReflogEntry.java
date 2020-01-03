/*
 * Copyright (C) 2011-2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

/**
 * Parsed reflog entry
 *
 * @since 3.0
 */
public interface ReflogEntry {

	/**
	 * Prefix used in reflog messages when the ref was first created.
	 * <p>
	 * Does not have a corresponding constant in C git, but is untranslated like
	 * the other constants.
	 *
	 * @since 4.9
	 */
	String PREFIX_CREATED = "created"; //$NON-NLS-1$

	/**
	 * Prefix used in reflog messages when the ref was updated with a fast
	 * forward.
	 * <p>
	 * Untranslated, and exactly matches the
	 * <a href="https://git.kernel.org/pub/scm/git/git.git/tree/builtin/fetch.c?id=f3da2b79be9565779e4f76dc5812c68e156afdf0#n680">
	 * untranslated string in C git</a>.
	 *
	 * @since 4.9
	 */
	String PREFIX_FAST_FORWARD = "fast-forward"; //$NON-NLS-1$

	/**
	 * Prefix used in reflog messages when the ref was force updated.
	 * <p>
	 * Untranslated, and exactly matches the
	 * <a href="https://git.kernel.org/pub/scm/git/git.git/tree/builtin/fetch.c?id=f3da2b79be9565779e4f76dc5812c68e156afdf0#n695">
	 * untranslated string in C git</a>.
	 *
	 * @since 4.9
	 */
	String PREFIX_FORCED_UPDATE = "forced-update"; //$NON-NLS-1$

	/**
	 * Get the commit id before the change
	 *
	 * @return the commit id before the change
	 */
	ObjectId getOldId();

	/**
	 * Get the commit id after the change
	 *
	 * @return the commit id after the change
	 */
	ObjectId getNewId();

	/**
	 * Get user performing the change
	 *
	 * @return user performing the change
	 */
	PersonIdent getWho();

	/**
	 * Get textual description of the change
	 *
	 * @return textual description of the change
	 */
	String getComment();

	/**
	 * Parse checkout
	 *
	 * @return a {@link org.eclipse.jgit.lib.CheckoutEntry} with parsed
	 *         information about a branch switch, or null if the entry is not a
	 *         checkout
	 */
	CheckoutEntry parseCheckout();

}
