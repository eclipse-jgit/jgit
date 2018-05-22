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

package org.eclipse.jgit.http.server.resolver;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Controls access to bare files in a repository.
 * <p>
 * Older HTTP clients which do not speak the smart HTTP variant of the Git
 * protocol fetch from a repository by directly getting its objects and pack
 * files. This class, along with the {@code http.getanyfile} per-repository
 * configuration setting, can be used by
 * {@link org.eclipse.jgit.http.server.GitServlet} to control whether or not
 * these older clients are permitted to read these direct files.
 */
public class AsIsFileService {
	/** Always throws {@link ServiceNotEnabledException}. */
	public static final AsIsFileService DISABLED = new AsIsFileService() {
		@Override
		public void access(HttpServletRequest req, Repository db)
				throws ServiceNotEnabledException {
			throw new ServiceNotEnabledException();
		}
	};

	private static class ServiceConfig {
		final boolean enabled;

		ServiceConfig(Config cfg) {
			enabled = cfg.getBoolean("http", "getanyfile", true);
		}
	}

	/**
	 * Determine if {@code http.getanyfile} is enabled in the configuration.
	 *
	 * @param db
	 *            the repository to check.
	 * @return {@code false} if {@code http.getanyfile} was explicitly set to
	 *         {@code false} in the repository's configuration file; otherwise
	 *         {@code true}.
	 */
	protected static boolean isEnabled(Repository db) {
		return db.getConfig().get(ServiceConfig::new).enabled;
	}

	/**
	 * Determine if access to any bare file of the repository is allowed.
	 * <p>
	 * This method silently succeeds if the request is allowed, or fails by
	 * throwing a checked exception if access should be denied.
	 * <p>
	 * The default implementation of this method checks {@code http.getanyfile},
	 * throwing
	 * {@link org.eclipse.jgit.transport.resolver.ServiceNotEnabledException} if
	 * it was explicitly set to {@code false}, and otherwise succeeding
	 * silently.
	 *
	 * @param req
	 *            current HTTP request, in case information from the request may
	 *            help determine the access request.
	 * @param db
	 *            the repository the request would obtain a bare file from.
	 * @throws ServiceNotEnabledException
	 *             bare file access is not allowed on the target repository, by
	 *             any user, for any reason.
	 * @throws ServiceNotAuthorizedException
	 *             bare file access is not allowed for this HTTP request and
	 *             repository, such as due to a permission error.
	 */
	public void access(HttpServletRequest req, Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {
		if (!isEnabled(db))
			throw new ServiceNotEnabledException();
	}
}
