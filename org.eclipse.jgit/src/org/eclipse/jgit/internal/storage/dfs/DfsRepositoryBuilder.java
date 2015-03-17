/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;

/**
 * Constructs a {@link DfsRepository}.
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

	/** @return options used by readers accessing the repository. */
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

	/** @return a description of the repository. */
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
	 */
	@Override
	public abstract R build() throws IOException;

	// We don't support local file IO and thus shouldn't permit these to set.

	@Override
	public B setGitDir(File gitDir) {
		if (gitDir != null)
			throw new IllegalArgumentException();
		return self();
	}

	@Override
	public B setObjectDirectory(File objectDirectory) {
		if (objectDirectory != null)
			throw new IllegalArgumentException();
		return self();
	}

	@Override
	public B addAlternateObjectDirectory(File other) {
		throw new UnsupportedOperationException(
				JGitText.get().unsupportedAlternates);
	}

	@Override
	public B setWorkTree(File workTree) {
		if (workTree != null)
			throw new IllegalArgumentException();
		return self();
	}

	@Override
	public B setIndexFile(File indexFile) {
		if (indexFile != null)
			throw new IllegalArgumentException();
		return self();
	}
}
