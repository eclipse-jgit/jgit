/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.resolver;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Create and configure {@link org.eclipse.jgit.transport.UploadPack} service
 * instance.
 *
 * @param <C>
 *            the connection type
 */
public interface UploadPackFactory<C> {
	/**
	 * A factory disabling the UploadPack service for all repositories.
	 */
	UploadPackFactory<?> DISABLED = (Object req, Repository db) -> {
		throw new ServiceNotEnabledException();
	};

	/**
	 * Create and configure a new UploadPack instance for a repository.
	 *
	 * @param req
	 *            current request, in case information from the request may help
	 *            configure the UploadPack instance.
	 * @param db
	 *            the repository the upload would read from.
	 * @return the newly configured UploadPack instance, must not be null.
	 * @throws ServiceNotEnabledException
	 *             this factory refuses to create the instance because it is not
	 *             allowed on the target repository, by any user.
	 * @throws ServiceNotAuthorizedException
	 *             this factory refuses to create the instance for this HTTP
	 *             request and repository, such as due to a permission error.
	 */
	UploadPack create(C req, Repository db) throws ServiceNotEnabledException,
			ServiceNotAuthorizedException;
}
