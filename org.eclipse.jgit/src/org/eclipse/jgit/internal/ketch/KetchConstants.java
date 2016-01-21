/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.ketch;

import org.eclipse.jgit.revwalk.FooterKey;

/** Frequently used constants in a Ketch system. */
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
