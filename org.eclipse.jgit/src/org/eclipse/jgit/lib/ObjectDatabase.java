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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Abstraction of arbitrary object storage.
 * <p>
 * An object database stores one or more Git objects, indexed by their unique
 * {@link org.eclipse.jgit.lib.ObjectId}.
 */
public abstract class ObjectDatabase {
	/**
	 * Initialize a new database instance for access.
	 */
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
	 * @throws java.io.IOException
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
	 * Create a new {@code ObjectReader} to read existing objects.
	 * <p>
	 * The returned reader is not itself thread-safe, but multiple concurrent
	 * reader instances created from the same {@code ObjectDatabase} must be
	 * thread-safe.
	 *
	 * @return reader the caller can use to load objects from this database.
	 */
	public abstract ObjectReader newReader();

	/**
	 * Close any resources held by this database.
	 */
	public abstract void close();

	/**
	 * Does the requested object exist in this database?
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return true if the specified object is stored in this database.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public boolean has(AnyObjectId objectId) throws IOException {
		try (final ObjectReader or = newReader()) {
			return or.has(objectId);
		}
	}

	/**
	 * Open an object from this database.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link org.eclipse.jgit.lib.ObjectLoader} for accessing the object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public ObjectLoader open(AnyObjectId objectId)
			throws IOException {
		return open(objectId, ObjectReader.OBJ_ANY);
	}

	/**
	 * Open an object from this database.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link org.eclipse.jgit.lib.Constants#OBJ_BLOB};
	 *            {@link org.eclipse.jgit.lib.ObjectReader#OBJ_ANY} if the
	 *            object type is not known, or does not matter to the caller.
	 * @return a {@link org.eclipse.jgit.lib.ObjectLoader} for accessing the
	 *         object.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (final ObjectReader or = newReader()) {
			return or.open(objectId, typeHint);
		}
	}

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
