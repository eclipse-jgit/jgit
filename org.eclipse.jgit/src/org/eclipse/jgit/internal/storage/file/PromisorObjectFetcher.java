/*
 * Copyright (C) 2026, Stuart Lang <stuart.b.lang@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Fetches objects that are missing locally from the promisor remote of a
 * partial clone.
 * <p>
 * In a partial (e.g. blobless) clone, some objects are intentionally absent and
 * are downloaded lazily, on first access, from the promisor remote recorded in
 * {@code extensions.partialClone}. An {@link ObjectDirectory} delegates to an
 * implementation of this interface whenever a requested object cannot be found
 * locally.
 */
@FunctionalInterface
interface PromisorObjectFetcher {
	/**
	 * Attempt to fetch the given objects from the promisor remote.
	 *
	 * @param ids
	 *            objects that were not found in the local object database
	 * @return {@code true} if a fetch was attempted and the caller should retry
	 *         the local lookup; {@code false} if no fetch was attempted (for
	 *         example because the repository is not a partial clone)
	 * @throws IOException
	 *             the fetch failed
	 */
	boolean fetch(Collection<ObjectId> ids) throws IOException;
}
