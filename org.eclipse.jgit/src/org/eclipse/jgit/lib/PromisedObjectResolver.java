/*
 * Copyright (C) 2026, Kyln contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.IOException;

/**
 * Fetches the content of promised objects that are absent from the local
 * object database.
 * <p>
 * A resolver is bound to one {@link PromisedObjectInventory} when it is
 * registered on a repository's object database. It is only asked for objects
 * that the inventory explicitly lists as promised. Resolvers must return
 * content whose bytes verify against the requested object id; the object
 * database verifies the Git object identity before accepting any content and
 * rejects wrong bytes without modifying the object database.
 * <p>
 * Implementations signal failure with {@link PromisedObjectException} for
 * the typed outcomes not found, integrity, timeout and authorization, or with
 * any other {@link IOException} for e.g. a temporarily unavailable store.
 * Resolvers are invoked outside object database pack and index locks, but may
 * be called concurrently for distinct object ids; requests for the same object
 * id are coalesced by the object database.
 * <p>
 * This interface is part of an experimental prototyped feature (JGit promisor
 * resolution); the API is not yet frozen and may change without notice.
 *
 * @since 8.0
 */
public interface PromisedObjectResolver {

	/**
	 * Opens the content of one promised object.
	 *
	 * @param request
	 *            the promise to resolve; never {@code null}.
	 * @return content for the requested object; never {@code null}.
	 * @throws PromisedObjectException
	 *             for the typed not found, integrity, timeout and
	 *             authorization outcomes.
	 * @throws IOException
	 *             if the content cannot be fetched, e.g. the store is
	 *             temporarily unavailable.
	 */
	PromisedObjectContent open(PromisedObjectRequest request)
			throws PromisedObjectException, IOException;
}
