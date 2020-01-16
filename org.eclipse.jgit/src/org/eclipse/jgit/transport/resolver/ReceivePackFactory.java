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
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Create and configure {@link org.eclipse.jgit.transport.ReceivePack} service
 * instance.
 *
 * @param <C>
 *            type of connection
 */
public interface ReceivePackFactory<C> {
	/**
	 * A factory disabling the ReceivePack service for all repositories
	 */
	ReceivePackFactory<?> DISABLED = (Object req, Repository db) -> {
		throw new ServiceNotEnabledException();
	};

	/**
	 * Create and configure a new ReceivePack instance for a repository.
	 *
	 * @param req
	 *            current request, in case information from the request may help
	 *            configure the ReceivePack instance.
	 * @param db
	 *            the repository the receive would write into.
	 * @return the newly configured ReceivePack instance, must not be null.
	 * @throws ServiceNotEnabledException
	 *             this factory refuses to create the instance because it is not
	 *             allowed on the target repository, by any user.
	 * @throws ServiceNotAuthorizedException
	 *             this factory refuses to create the instance for this HTTP
	 *             request and repository, such as due to a permission error.
	 */
	ReceivePack create(C req, Repository db) throws ServiceNotEnabledException,
			ServiceNotAuthorizedException;
}
