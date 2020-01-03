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

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;

/**
 * Locate a Git {@link org.eclipse.jgit.lib.Repository} by name from the URL.
 *
 * @param <C>
 *            type of connection.
 */
public interface RepositoryResolver<C> {
	/**
	 * Resolver configured to open nothing.
	 */
	RepositoryResolver<?> NONE = (Object req, String name) -> {
		throw new RepositoryNotFoundException(name);
	};

	/**
	 * Locate and open a reference to a {@link Repository}.
	 * <p>
	 * The caller is responsible for closing the returned Repository.
	 *
	 * @param req
	 *            the current request, may be used to inspect session state
	 *            including cookies or user authentication.
	 * @param name
	 *            name of the repository, as parsed out of the URL.
	 * @return the opened repository instance, never null.
	 * @throws RepositoryNotFoundException
	 *             the repository does not exist or the name is incorrectly
	 *             formatted as a repository name.
	 * @throws ServiceNotAuthorizedException
	 *             the repository may exist, but HTTP access is not allowed
	 *             without authentication, i.e. this corresponds to an HTTP 401
	 *             Unauthorized.
	 * @throws ServiceNotEnabledException
	 *             the repository may exist, but HTTP access is not allowed on the
	 *             target repository, for the current user.
	 * @throws ServiceMayNotContinueException
	 *             the repository may exist, but HTTP access is not allowed for
	 *             the current request. The exception message contains a detailed
	 *             message that should be shown to the user.
	 */
	Repository open(C req, String name) throws RepositoryNotFoundException,
			ServiceNotAuthorizedException, ServiceNotEnabledException,
			ServiceMayNotContinueException;
}
