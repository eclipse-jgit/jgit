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

import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;

/** Default resolver serving from a single root path in local filesystem. */
public class FileResolver implements RepositoryResolver {
	private final File basePath;

	private final boolean exportAll;

	/**
	 * Create a new resolver for the given path.
	 *
	 * @param basePath
	 *            the base path all repositories are rooted under.
	 * @param exportAll
	 *            if true, exports all repositories, ignoring the check for the
	 *            {@code git-daemon-export-ok} files.
	 */
	public FileResolver(final File basePath, final boolean exportAll) {
		this.basePath = basePath;
		this.exportAll = exportAll;
	}

	public Repository open(final HttpServletRequest req,
			final String repositoryName) throws RepositoryNotFoundException,
			ServiceNotEnabledException {
		if (isUnreasonableName(repositoryName))
			throw new RepositoryNotFoundException(repositoryName);

		final Repository db;
		try {
			final File gitdir = new File(basePath, repositoryName);
			db = RepositoryCache.open(FileKey.lenient(gitdir), true);
		} catch (IOException e) {
			throw new RepositoryNotFoundException(repositoryName, e);
		}

		try {
			if (isExportOk(req, repositoryName, db)) {
				// We have to leak the open count to the caller, they
				// are responsible for closing the repository if we
				// complete successfully.
				return db;
			} else
				throw new ServiceNotEnabledException();

		} catch (RuntimeException e) {
			db.close();
			throw new RepositoryNotFoundException(repositoryName, e);

		} catch (IOException e) {
			db.close();
			throw new RepositoryNotFoundException(repositoryName, e);

		} catch (ServiceNotEnabledException e) {
			db.close();
			throw e;
		}
	}

	/** @return {@code true} if all repositories are to be exported. */
	protected boolean isExportAll() {
		return exportAll;
	}

	/**
	 * Check if this repository can be served over HTTP.
	 * <p>
	 * The default implementation of this method returns true only if either
	 * {@link #isExportAll()} is true, or the {@code git-daemon-export-ok} file
	 * is present in the repository's directory.
	 *
	 * @param req
	 *            the current HTTP request.
	 * @param repositoryName
	 *            name of the repository, as present in the URL.
	 * @param db
	 *            the opened repository instance.
	 * @return true if the repository is accessible; false if not.
	 * @throws IOException
	 *             the repository could not be accessed, the caller will claim
	 *             the repository does not exist.
	 */
	protected boolean isExportOk(HttpServletRequest req, String repositoryName,
			Repository db) throws IOException {
		if (isExportAll())
			return true;
		else
			return new File(db.getDirectory(), "git-daemon-export-ok").exists();
	}

	private static boolean isUnreasonableName(final String name) {
		if (name.length() == 0)
			return true; // no empty paths

		if (name.indexOf('\\') >= 0)
			return true; // no windows/dos style paths
		if (new File(name).isAbsolute())
			return true; // no absolute paths

		if (name.startsWith("../"))
			return true; // no "l../etc/passwd"
		if (name.contains("/../"))
			return true; // no "foo/../etc/passwd"
		if (name.contains("/./"))
			return true; // "foo/./foo" is insane to ask
		if (name.contains("//"))
			return true; // double slashes is sloppy, don't use it

		return false; // is a reasonable name
	}
}
