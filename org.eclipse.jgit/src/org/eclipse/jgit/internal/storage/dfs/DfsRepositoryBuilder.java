/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;

/**
 * Constructs a {@link org.eclipse.jgit.internal.storage.dfs.DfsRepository}.
 *
 * @param <B>
 *            type of the builder class.
 * @param <R>
 *            type of the repository class.
 */
public abstract class DfsRepositoryBuilder<B extends DfsRepositoryBuilder, R extends DfsRepository>
		extends BaseRepositoryBuilder<B, R> {
	private DfsReaderOptions readerOptions;

	private DfsRepositoryDescription repoDesc;

	/**
	 * Get options used by readers accessing the repository.
	 *
	 * @return options used by readers accessing the repository.
	 */
	public DfsReaderOptions getReaderOptions() {
		return readerOptions;
	}

	/**
	 * Set the reader options.
	 *
	 * @param opt
	 *            new reader options object.
	 * @return {@code this}
	 */
	public B setReaderOptions(DfsReaderOptions opt) {
		readerOptions = opt;
		return self();
	}

	/**
	 * Get the description of the repository.
	 *
	 * @return the description of the repository.
	 */
	public DfsRepositoryDescription getRepositoryDescription() {
		return repoDesc;
	}

	/**
	 * Set the repository description.
	 *
	 * @param desc
	 *            new repository description object.
	 * @return {@code this}
	 */
	public B setRepositoryDescription(DfsRepositoryDescription desc) {
		repoDesc = desc;
		return self();
	}

	/** {@inheritDoc} */
	@Override
	public B setup() throws IllegalArgumentException, IOException {
		super.setup();
		if (getReaderOptions() == null)
			setReaderOptions(new DfsReaderOptions());
		if (getRepositoryDescription() == null)
			setRepositoryDescription(new DfsRepositoryDescription());
		return self();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Create a repository matching the configuration in this builder.
	 * <p>
	 * If an option was not set, the build method will try to default the option
	 * based on other options. If insufficient information is available, an
	 * exception is thrown to the caller.
	 */
	@Override
	public abstract R build() throws IOException;

	// We don't support local file IO and thus shouldn't permit these to set.

	/** {@inheritDoc} */
	@Override
	public B setGitDir(File gitDir) {
		if (gitDir != null)
			throw new IllegalArgumentException();
		return self();
	}

	/** {@inheritDoc} */
	@Override
	public B setObjectDirectory(File objectDirectory) {
		if (objectDirectory != null)
			throw new IllegalArgumentException();
		return self();
	}

	/** {@inheritDoc} */
	@Override
	public B addAlternateObjectDirectory(File other) {
		throw new UnsupportedOperationException(
				JGitText.get().unsupportedAlternates);
	}

	/** {@inheritDoc} */
	@Override
	public B setWorkTree(File workTree) {
		if (workTree != null)
			throw new IllegalArgumentException();
		return self();
	}

	/** {@inheritDoc} */
	@Override
	public B setIndexFile(File indexFile) {
		if (indexFile != null)
			throw new IllegalArgumentException();
		return self();
	}
}
