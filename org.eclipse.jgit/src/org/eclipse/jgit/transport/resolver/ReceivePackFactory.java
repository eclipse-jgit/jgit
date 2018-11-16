/*
 * Copyright (C) 2009-2010, Google Inc.
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
	ReceivePackFactory<?> DISABLED = new ReceivePackFactory<Object>() {
		@Override
		public ReceivePack create(Object req, Repository db)
				throws ServiceNotEnabledException {
			throw new ServiceNotEnabledException();
		}
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
