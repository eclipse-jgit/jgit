/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
