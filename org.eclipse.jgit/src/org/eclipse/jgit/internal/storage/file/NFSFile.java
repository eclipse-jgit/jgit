/*
 * Copyright (c) 2018, The Linux Foundation
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

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

/**
 * NFSFile extends {@link java.io.File} to provide accurate functionality on NFS
 * filesystems where file attributes and existence are cached.
 *
 */
public class NFSFile extends File {
	private static final long serialVersionUID = 1L;

	private final Config config;

	/**
	 * Resolve this file to its actual path name that the JRE can use.
	 * <p>
	 * This method can be relatively expensive. Computing a translation may
	 * require forking an external process per path name translated. Callers
	 * should try to minimize the number of translations necessary by caching
	 * the results.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 require translation for Cygwin based paths.
	 *
	 * @param dir
	 *            directory relative to which the path name is.
	 * @param name
	 *            path name to translate.
	 * @param config
	 * @return the translated path. {@code new NFSFile(dir,name,config)} if this
	 *         platform does not require path name translation.
	 */
	public static NFSFile resolve(final File dir, final String name,
			final Config config) {
		final NFSFile abspn = new NFSFile(name, config);
		if (abspn.isAbsolute())
			return abspn;
		return new NFSFile(dir, name, config);
	}

	/**
	 * Wraps {@link File#File(File, String)}
	 *
	 * @param config
	 * @param parent
	 *            The parent pathname string
	 * @param child
	 *            The child pathname string
	 * @throws NullPointerException
	 *             If {@code child} is {@code null}
	 */
	public NFSFile(File parent, String child, Config config) {
		super(parent, child);
		this.config = config;
	}

	/**
	 * Wraps {@link File#File(String)}
	 *
	 * @param config
	 * @param pathname
	 *            A pathname string
	 * @throws NullPointerException
	 *             If the {@code pathname} argument is {@code null}
	 */
	public NFSFile(String pathname, Config config) {
		super(pathname);
		this.config = config;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Uses the value of
	 * {@code ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTATBEFORE} to optionally
	 * flush the NFS cache before checking file existence.
	 */
	@Override
	public boolean exists() {
		try {
			refreshFolderStats();
		} catch (IOException e) {
			return false; // contract of exists says to return false for any I/O
							// error
		}
		return super.exists();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Uses the value of
	 * {@code ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTATBEFORE} to optionally
	 * flush the NFS cache before checking the modification time.
	 */
	@Override
	public long lastModified() {
		try {
			refreshFolderStats();
		} catch (IOException e) {
			return 0L; // contract of lastModified says to return 0L for any I/O
						// error
		}
		return super.lastModified();
	}

	private void refreshFolderStats() throws IOException {
		boolean refreshFolderStat = config.getBoolean(
				ConfigConstants.CONFIG_CORE_SECTION,
				ConfigConstants.CONFIG_KEY_REFRESHFOLDERSTAT, false);
		if (refreshFolderStat) {
			try (DirectoryStream<Path> stream = Files
					.newDirectoryStream(this.toPath().getParent())) {
				// open and close the directory to invalidate NFS attribute
				// cache
			}
		}
	}

}
