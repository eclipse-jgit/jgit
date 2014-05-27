/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Repository;

/**
 * Constructs a {@link FileRepository}.
 * <p>
 * Applications must set one of {@link #setGitDir(File)} or
 * {@link #setWorkTree(File)}, or use {@link #readEnvironment()} or
 * {@link #findGitDir()} in order to configure the minimum property set
 * necessary to open a repository.
 * <p>
 * Single repository applications trying to be compatible with other Git
 * implementations are encouraged to use a model such as:
 *
 * <pre>
 * new FileRepositoryBuilder() //
 * 		.setGitDir(gitDirArgument) // --git-dir if supplied, no-op if null
 * 		.readEnviroment() // scan environment GIT_* variables
 * 		.findGitDir() // scan up the file system tree
 * 		.build()
 * </pre>
 */
public class FileRepositoryBuilder extends
		BaseRepositoryBuilder<FileRepositoryBuilder, Repository> {

	private AtomicReference<WindowCache> windowCache;

	public FileRepositoryBuilder setWindowCache(WindowCacheConfig cfg) {
		setWindowCache(new AtomicReference<WindowCache>(new WindowCache(cfg)));
		return self();
	}

	public FileRepositoryBuilder setWindowCache(
			AtomicReference<WindowCache> wc) {
		windowCache = wc;
		return self();
	}

	public AtomicReference<WindowCache> getWindowCache() {
		return windowCache;
	}

	@Override
	public FileRepositoryBuilder setup()
			throws IllegalArgumentException, IOException {
		super.setup();
		if (windowCache == null)
			windowCache = WindowCache.getDefaultCache();
		return self();
	}

	/**
	 * Create a repository matching the configuration in this builder.
	 * <p>
	 * If an option was not set, the build method will try to default the option
	 * based on other options. If insufficient information is available, an
	 * exception is thrown to the caller.
	 *
	 * @return a repository matching this configuration.
	 * @throws IllegalArgumentException
	 *             insufficient parameters were set.
	 * @throws IOException
	 *             the repository could not be accessed to configure the rest of
	 *             the builder's parameters.
	 * @since 3.0
	 */
	@Override
	public Repository build() throws IOException {
		FileRepository repo = new FileRepository(setup());
		if (isMustExist() && !repo.getObjectDatabase().exists())
			throw new RepositoryNotFoundException(getGitDir());
		return repo;
	}

	/**
	 * Convenience factory method to construct a {@link FileRepository}.
	 *
	 * @param gitDir
	 *            {@code GIT_DIR}, the repository meta directory.
	 * @return a repository matching this configuration.
	 * @throws IOException
	 *             the repository could not be accessed to configure the rest of
	 *             the builder's parameters.
	 * @since 3.0
	 */
	public static Repository create(File gitDir) throws IOException {
		return new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment()
				.build();
	}
}
