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

package org.eclipse.jgit.storage.dht;

import java.io.File;
import java.text.MessageFormat;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.storage.dht.spi.Database;

/**
 * Constructs a {@link DhtRepository}.
 *
 * @param <B>
 *            type of builder used by the DHT system.
 * @param <R>
 *            type of repository used by the DHT system.
 * @param <D>
 *            type of database used by the DHT system.
 */
public class DhtRepositoryBuilder<B extends DhtRepositoryBuilder, R extends DhtRepository, D extends Database>
		extends BaseRepositoryBuilder<B, R> {
	private D database;

	private DhtReaderOptions readerOptions;

	private DhtInserterOptions inserterOptions;

	private String name;

	private RepositoryKey key;

	/** Initializes an empty builder with no values set. */
	public DhtRepositoryBuilder() {
		setBare();
		setMustExist(true);
	}

	/** @return the database that stores the repositories. */
	public D getDatabase() {
		return database;
	}

	/**
	 * Set the cluster used to store the repositories.
	 *
	 * @param database
	 *            the database supplier.
	 * @return {@code this}
	 */
	public B setDatabase(D database) {
		this.database = database;
		return self();
	}

	/** @return options used by readers accessing the repository. */
	public DhtReaderOptions getReaderOptions() {
		return readerOptions;
	}

	/**
	 * Set the reader options.
	 *
	 * @param opt
	 *            new reader options object.
	 * @return {@code this}
	 */
	public B setReaderOptions(DhtReaderOptions opt) {
		readerOptions = opt;
		return self();
	}

	/** @return options used by writers accessing the repository. */
	public DhtInserterOptions getInserterOptions() {
		return inserterOptions;
	}

	/**
	 * Set the inserter options.
	 *
	 * @param opt
	 *            new inserter options object.
	 * @return {@code this}
	 */
	public B setInserterOptions(DhtInserterOptions opt) {
		inserterOptions = opt;
		return self();
	}

	/** @return name of the repository in the DHT. */
	public String getRepositoryName() {
		return name;
	}

	/**
	 * Set the name of the repository to open.
	 *
	 * @param name
	 *            the name.
	 * @return {@code this}.
	 */
	public B setRepositoryName(String name) {
		this.name = name;
		return self();
	}

	/** @return the repository's key. */
	public RepositoryKey getRepositoryKey() {
		return key;
	}

	/**
	 * @param key
	 * @return {@code this}
	 */
	public B setRepositoryKey(RepositoryKey key) {
		this.key = key;
		return self();
	}

	@Override
	public B setup() throws IllegalArgumentException, DhtException,
			RepositoryNotFoundException {
		if (getDatabase() == null)
			throw new IllegalArgumentException(DhtText.get().databaseRequired);

		if (getReaderOptions() == null)
			setReaderOptions(new DhtReaderOptions());
		if (getInserterOptions() == null)
			setInserterOptions(new DhtInserterOptions());

		if (getRepositoryKey() == null) {
			if (getRepositoryName() == null)
				throw new IllegalArgumentException(DhtText.get().nameRequired);

			RepositoryKey r;
			try {
				r = getDatabase().repositoryIndex().get(
						RepositoryName.create(name));
			} catch (TimeoutException e) {
				throw new DhtTimeoutException(MessageFormat.format(
						DhtText.get().timeoutLocatingRepository, name), e);
			}
			if (isMustExist() && r == null)
				throw new RepositoryNotFoundException(getRepositoryName());
			if (r != null)
				setRepositoryKey(r);
		}
		return self();
	}

	@Override
	@SuppressWarnings("unchecked")
	public R build() throws IllegalArgumentException, DhtException,
			RepositoryNotFoundException {
		return (R) new DhtRepository(setup());
	}

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
		throw new UnsupportedOperationException("Alternates not supported");
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
