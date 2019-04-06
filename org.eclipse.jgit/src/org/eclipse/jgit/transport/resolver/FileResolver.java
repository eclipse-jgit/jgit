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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;

/**
 * Default resolver serving from the local filesystem.
 *
 * @param <C>
 *            type of connection
 */
public class FileResolver<C> implements RepositoryResolver<C> {
	private volatile boolean exportAll;

	private final Map<String, Repository> exports;

	private final Collection<File> exportBase;

	/**
	 * Initialize an empty file based resolver.
	 */
	public FileResolver() {
		exports = new ConcurrentHashMap<>();
		exportBase = new CopyOnWriteArrayList<>();
	}

	/**
	 * Create a new resolver for the given path.
	 *
	 * @param basePath
	 *            the base path all repositories are rooted under.
	 * @param exportAll
	 *            if true, exports all repositories, ignoring the check for the
	 *            {@code git-daemon-export-ok} files.
	 */
	public FileResolver(File basePath, boolean exportAll) {
		this();
		exportDirectory(basePath);
		setExportAll(exportAll);
	}

	/** {@inheritDoc} */
	@Override
	public Repository open(C req, String name)
			throws RepositoryNotFoundException, ServiceNotEnabledException {
		if (isUnreasonableName(name))
			throw new RepositoryNotFoundException(name);

		Repository db = exports.get(nameWithDotGit(name));
		if (db != null) {
			db.incrementOpen();
			return db;
		}

		for (File base : exportBase) {
			File dir = FileKey.resolve(new File(base, name), FS.DETECTED);
			if (dir == null)
				continue;

			try {
				FileKey key = FileKey.exact(dir, FS.DETECTED);
				db = RepositoryCache.open(key, true);
			} catch (IOException e) {
				throw new RepositoryNotFoundException(name, e);
			}

			try {
				if (isExportOk(req, name, db)) {
					// We have to leak the open count to the caller, they
					// are responsible for closing the repository if we
					// complete successfully.
					return db;
				} else
					throw new ServiceNotEnabledException();

			} catch (RuntimeException | IOException e) {
				db.close();
				throw new RepositoryNotFoundException(name, e);

			} catch (ServiceNotEnabledException e) {
				db.close();
				throw e;
			}
		}

		if (exportBase.size() == 1) {
			File dir = new File(exportBase.iterator().next(), name);
			throw new RepositoryNotFoundException(name,
					new RepositoryNotFoundException(dir));
		}

		throw new RepositoryNotFoundException(name);
	}

	/**
	 * Whether <code>git-daemon-export-ok</code> is required to export a
	 * repository
	 *
	 * @return false if <code>git-daemon-export-ok</code> is required to export
	 *         a repository; true if <code>git-daemon-export-ok</code> is
	 *         ignored.
	 * @see #setExportAll(boolean)
	 */
	public boolean isExportAll() {
		return exportAll;
	}

	/**
	 * Set whether or not to export all repositories.
	 * <p>
	 * If false (the default), repositories must have a
	 * <code>git-daemon-export-ok</code> file to be accessed through this
	 * daemon.
	 * <p>
	 * If true, all repositories are available through the daemon, whether or
	 * not <code>git-daemon-export-ok</code> exists.
	 *
	 * @param export a boolean.
	 */
	public void setExportAll(boolean export) {
		exportAll = export;
	}

	/**
	 * Add a single repository to the set that is exported by this daemon.
	 * <p>
	 * The existence (or lack-thereof) of <code>git-daemon-export-ok</code> is
	 * ignored by this method. The repository is always published.
	 *
	 * @param name
	 *            name the repository will be published under.
	 * @param db
	 *            the repository instance.
	 */
	public void exportRepository(String name, Repository db) {
		exports.put(nameWithDotGit(name), db);
	}

	/**
	 * Recursively export all Git repositories within a directory.
	 *
	 * @param dir
	 *            the directory to export. This directory must not itself be a
	 *            git repository, but any directory below it which has a file
	 *            named <code>git-daemon-export-ok</code> will be published.
	 */
	public void exportDirectory(File dir) {
		exportBase.add(dir);
	}

	/**
	 * Check if this repository can be served.
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
	 * @throws java.io.IOException
	 *             the repository could not be accessed, the caller will claim
	 *             the repository does not exist.
	 */
	protected boolean isExportOk(C req, String repositoryName, Repository db)
			throws IOException {
		if (isExportAll())
			return true;
		else if (db.getDirectory() != null)
			return new File(db.getDirectory(), "git-daemon-export-ok").exists(); //$NON-NLS-1$
		else
			return false;
	}

	private static String nameWithDotGit(String name) {
		if (name.endsWith(Constants.DOT_GIT_EXT))
			return name;
		return name + Constants.DOT_GIT_EXT;
	}

	private static boolean isUnreasonableName(String name) {
		if (name.length() == 0)
			return true; // no empty paths

		if (name.indexOf('\\') >= 0)
			return true; // no windows/dos style paths
		if (new File(name).isAbsolute())
			return true; // no absolute paths

		if (name.startsWith("../")) //$NON-NLS-1$
			return true; // no "l../etc/passwd"
		if (name.contains("/../")) //$NON-NLS-1$
			return true; // no "foo/../etc/passwd"
		if (name.contains("/./")) //$NON-NLS-1$
			return true; // "foo/./foo" is insane to ask
		if (name.contains("//")) //$NON-NLS-1$
			return true; // double slashes is sloppy, don't use it

		return false; // is a reasonable name
	}
}
