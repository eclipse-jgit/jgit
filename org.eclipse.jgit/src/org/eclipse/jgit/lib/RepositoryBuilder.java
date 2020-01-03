/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.File;

/**
 * Base class to support constructing a {@link org.eclipse.jgit.lib.Repository}.
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
 * new RepositoryBuilder() //
 * 		.setGitDir(gitDirArgument) // --git-dir if supplied, no-op if null
 * 		.readEnviroment() // scan environment GIT_* variables
 * 		.findGitDir() // scan up the file system tree
 * 		.build()
 * </pre>
 *
 * @see org.eclipse.jgit.storage.file.FileRepositoryBuilder
 */
public class RepositoryBuilder extends
		BaseRepositoryBuilder<RepositoryBuilder, Repository> {
	// Empty implementation, everything is inherited.
}
