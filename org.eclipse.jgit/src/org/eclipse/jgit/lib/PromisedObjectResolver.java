/*
 * Copyright (C) 2026 Shawn Hartsock <shartsock@nvidia.com> and others
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
 * Fetches the content of promised objects absent from the local object
 * database.
 * <p>
 * A resolver is bound to one {@link PromisedObjectInventory} when registered
 * on a repository's object database and is only asked for objects that
 * inventory lists. The object database verifies the Git object id of every
 * returned content before accepting it; wrong bytes install nothing.
 * <p>
 * Failures are signalled with {@link PromisedObjectException} (not found,
 * integrity, timeout, authorization) or any other {@link IOException}, for
 * example a temporarily unavailable store. Resolvers run outside pack and
 * index locks and may be called concurrently for distinct object ids;
 * requests for one object id are coalesced.
 * <p>
 * Experimental API: not yet frozen and may change without notice.
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
	 *             for a typed outcome.
	 * @throws IOException
	 *             if the content cannot be fetched.
	 */
	PromisedObjectContent open(PromisedObjectRequest request)
			throws PromisedObjectException, IOException;
}
