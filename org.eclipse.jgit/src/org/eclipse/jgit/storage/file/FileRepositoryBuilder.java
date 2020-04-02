/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Repository;

/**
 * Constructs a {@link org.eclipse.jgit.internal.storage.file.FileRepository}.
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
 * 		.readEnvironment() // scan environment GIT_* variables
 * 		.findGitDir() // scan up the file system tree
 * 		.build()
 * </pre>
 */
public class FileRepositoryBuilder extends
		BaseRepositoryBuilder<FileRepositoryBuilder, Repository> {
	/**
	 * {@inheritDoc}
	 * <p>
	 * Create a repository matching the configuration in this builder.
	 * <p>
	 * If an option was not set, the build method will try to default the option
	 * based on other options. If insufficient information is available, an
	 * exception is thrown to the caller.
	 *
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
	 * Convenience factory method to construct a
	 * {@link org.eclipse.jgit.internal.storage.file.FileRepository}.
	 *
	 * @param gitDir
	 *            {@code GIT_DIR}, the repository meta directory.
	 * @return a repository matching this configuration.
	 * @throws java.io.IOException
	 *             the repository could not be accessed to configure the rest of
	 *             the builder's parameters.
	 * @since 3.0
	 */
	public static Repository create(File gitDir) throws IOException {
		return new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment()
				.build();
	}
}
