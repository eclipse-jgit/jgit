/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;

import org.eclipse.jgit.lib.Ref;

/**
 * Iterator over references inside a
 * {@link org.eclipse.jgit.internal.storage.reftable.Reftable}.
 */
public abstract class RefCursor implements AutoCloseable {
	/**
	 * Check if another reference is available.
	 *
	 * @return {@code true} if there is another result.
	 * @throws java.io.IOException
	 *             references cannot be read.
	 */
	public abstract boolean next() throws IOException;

	/**
	 * Seeks forward to the first ref record lexicographically beyond
	 * {@code prefixName} that doesn't start with {@code prefixName}. If there are
	 * no more results, skipping some refs won't add new results. E.g if we create a
	 * RefCursor that returns only results with a specific prefix, skipping that
	 * prefix won't give results that are not part of the original prefix.
	 *
	 * @param prefixName prefix that should be skipped. All previous refs before it
	 *                   will be skipped.
	 * @throws java.io.IOException references cannot be read.
	 */
	public abstract void seekPastPrefix(String prefixName) throws IOException;

	/**
	 * Get reference at the current position.
	 *
	 * @return reference at the current position.
	 */
	public abstract Ref getRef();

	/**
	 * Whether the current reference was deleted.
	 *
	 * @return {@code true} if the current reference was deleted.
	 */
	public boolean wasDeleted() {
		Ref r = getRef();
		return r.getStorage() == Ref.Storage.NEW && r.getObjectId() == null;
	}

	@Override
	public abstract void close();
}
