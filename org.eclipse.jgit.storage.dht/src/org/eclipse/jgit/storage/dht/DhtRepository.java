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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.file.ReflogReader;

/**
 * A Git repository storing its objects and references in a DHT.
 * <p>
 * With the exception of repository creation, this class is thread-safe, but
 * readers created from it are not. When creating a new repository using the
 * {@link #create(boolean)} method, the newly constructed repository object does
 * not ensure the assigned {@link #getRepositoryKey()} will be visible to all
 * threads. Applications are encouraged to use their own synchronization when
 * sharing a Repository instance that was used to create a new repository.
 */
public class DhtRepository extends Repository {
	private final RepositoryName name;

	private final Database db;

	private final DhtRefDatabase refdb;

	private final DhtObjDatabase objdb;

	private final DhtConfig config;

	private RepositoryKey key;

	/**
	 * Initialize an in-memory representation of a DHT backed repository.
	 *
	 * @param builder
	 *            description of the repository and its data storage.
	 */
	public DhtRepository(DhtRepositoryBuilder builder) {
		super(builder);
		this.name = RepositoryName.create(builder.getRepositoryName());
		this.key = builder.getRepositoryKey();
		this.db = builder.getDatabase();

		this.refdb = new DhtRefDatabase(this, db);
		this.objdb = new DhtObjDatabase(this, builder);
		this.config = new DhtConfig();
	}

	/** @return database cluster that houses this repository (among others). */
	public Database getDatabase() {
		return db;
	}

	/** @return human readable name used to open this repository. */
	public RepositoryName getRepositoryName() {
		return name;
	}

	/** @return unique identity of the repository in the {@link #getDatabase()}. */
	public RepositoryKey getRepositoryKey() {
		return key;
	}

	@Override
	public StoredConfig getConfig() {
		return config;
	}

	@Override
	public DhtRefDatabase getRefDatabase() {
		return refdb;
	}

	@Override
	public DhtObjDatabase getObjectDatabase() {
		return objdb;
	}

	@Override
	public void create(boolean bare) throws IOException {
		if (!bare)
			throw new IllegalArgumentException(
					DhtText.get().repositoryMustBeBare);

		if (getObjectDatabase().exists())
			throw new DhtException(MessageFormat.format(
					DhtText.get().repositoryAlreadyExists, name.asString()));

		try {
			key = db.repository().nextKey();
			db.repositoryIndex().putUnique(name, key);
		} catch (TimeoutException err) {
			throw new DhtTimeoutException(MessageFormat.format(
					DhtText.get().timeoutLocatingRepository, name), err);
		}

		String master = Constants.R_HEADS + Constants.MASTER;
		RefUpdate.Result result = updateRef(Constants.HEAD, true).link(master);
		if (result != RefUpdate.Result.NEW)
			throw new IOException(result.name());
	}

	@Override
	public void scanForRepoChanges() {
		refdb.clearCache();
	}

	@Override
	public String toString() {
		return "DhtRepostitory[" + key + " / " + name + "]";
	}

	// TODO This method should be removed from the JGit API.
	@Override
	public ReflogReader getReflogReader(String refName) {
		throw new UnsupportedOperationException();
	}
}
