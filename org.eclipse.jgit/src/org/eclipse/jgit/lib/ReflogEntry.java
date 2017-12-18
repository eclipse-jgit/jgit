/*
 * Copyright (C) 2011-2013, Robin Rosenberg <robin.rosenberg@dewire.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
	public static final String PREFIX_CREATED = "created"; //$NON-NLS-1$

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
	public static final String PREFIX_FAST_FORWARD = "fast-forward"; //$NON-NLS-1$

	/**
	 * Prefix used in reflog messages when the ref was force updated.
	 * <p>
	 * Untranslated, and exactly matches the
	 * <a href="https://git.kernel.org/pub/scm/git/git.git/tree/builtin/fetch.c?id=f3da2b79be9565779e4f76dc5812c68e156afdf0#n695">
	 * untranslated string in C git</a>.
	 *
	 * @since 4.9
	 */
	public static final String PREFIX_FORCED_UPDATE = "forced-update"; //$NON-NLS-1$

	/**
	 * Get the commit id before the change
	 *
	 * @return the commit id before the change
	 */
	public abstract ObjectId getOldId();

	/**
	 * Get the commit id after the change
	 *
	 * @return the commit id after the change
	 */
	public abstract ObjectId getNewId();

	/**
	 * Get user performing the change
	 *
	 * @return user performing the change
	 */
	public abstract PersonIdent getWho();

	/**
	 * Get textual description of the change
	 *
	 * @return textual description of the change
	 */
	public abstract String getComment();

	/**
	 * Parse checkout
	 *
	 * @return a {@link org.eclipse.jgit.lib.CheckoutEntry} with parsed
	 *         information about a branch switch, or null if the entry is not a
	 *         checkout
	 */
	public abstract CheckoutEntry parseCheckout();

}
