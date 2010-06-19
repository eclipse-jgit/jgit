/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Collection;

/**
 * Abstraction of arbitrary object storage.
 * <p>
 * An object database stores one or more Git objects, indexed by their unique
 * {@link ObjectId}.
 */
public abstract class ObjectDatabase {
	/** Initialize a new database instance for access. */
	protected ObjectDatabase() {
		// Protected to force extension.
	}

	/**
	 * Does this database exist yet?
	 *
	 * @return true if this database is already created; false if the caller
	 *         should invoke {@link #create()} to create this database location.
	 */
	public boolean exists() {
		return true;
	}

	/**
	 * Initialize a new object database at this location.
	 *
	 * @throws IOException
	 *             the database could not be created.
	 */
	public void create() throws IOException {
		// Assume no action is required.
	}

	/**
	 * Create a new {@code ObjectInserter} to insert new objects.
	 * <p>
	 * The returned inserter is not itself thread-safe, but multiple concurrent
	 * inserter instances created from the same {@code ObjectDatabase} must be
	 * thread-safe.
	 *
	 * @return writer the caller can use to create objects in this database.
	 */
	public abstract ObjectInserter newInserter();

	/**
	 * Close any resources held by this database.
	 */
	public abstract void close();

	/**
	 * Does the requested object exist in this database?
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return true if the specified object is stored in this database.
	 */
	public abstract boolean hasObject(AnyObjectId objectId);

	/**
	 * Open an object from this database.
	 *
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	public abstract ObjectLoader openObject(WindowCursor curs,
			AnyObjectId objectId) throws IOException;

	/**
	 * Open the object from all packs containing it.
	 *
	 * @param out
	 *            result collection of loaders for this object, filled with
	 *            loaders from all packs containing specified object
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param objectId
	 *            id of object to search for
	 * @throws IOException
	 */
	abstract void openObjectInAllPacks(final Collection<PackedObjectLoader> out,
			final WindowCursor curs, final AnyObjectId objectId)
			throws IOException;

	/**
	 * Create a new cached database instance over this database. This instance might
	 * optimize queries by caching some information about database. So some modifications
	 * done after instance creation might fail to be noticed.
	 *
	 * @return new cached database instance
	 */
	public ObjectDatabase newCachedDatabase() {
		return this;
	}
}
