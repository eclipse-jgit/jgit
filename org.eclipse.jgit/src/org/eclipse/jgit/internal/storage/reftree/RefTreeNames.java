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

package org.eclipse.jgit.internal.storage.reftree;

import org.eclipse.jgit.lib.RefDatabase;

/**
 * Magic reference name logic for RefTrees.
 */
public class RefTreeNames {
	/**
	 * Suffix used on a {@link RefTreeDatabase#getTxnNamespace()} for user data.
	 * <p>
	 * A {@link RefTreeDatabase}'s namespace may include a subspace (e.g.
	 * {@code "refs/txn/stage/"}) containing commit objects from the usual user
	 * portion of the repository (e.g. {@code "refs/heads/"}). These should be
	 * packed by the garbage collector alongside other user content rather than
	 * with the RefTree.
	 */
	private static final String STAGE = "stage/"; //$NON-NLS-1$

	/**
	 * Determine if the reference is likely to be a RefTree.
	 *
	 * @param refdb
	 *            database instance.
	 * @param ref
	 *            reference name.
	 * @return {@code true} if the reference is a RefTree.
	 */
	public static boolean isRefTree(RefDatabase refdb, String ref) {
		if (refdb instanceof RefTreeDatabase) {
			RefTreeDatabase b = (RefTreeDatabase) refdb;
			if (ref.equals(b.getTxnCommitted())) {
				return true;
			}

			String namespace = b.getTxnNamespace();
			if (namespace != null
					&& ref.startsWith(namespace)
					&& !ref.startsWith(namespace + STAGE)) {
				return true;
			}
		}
		return false;
	}

	private RefTreeNames() {
	}
}
