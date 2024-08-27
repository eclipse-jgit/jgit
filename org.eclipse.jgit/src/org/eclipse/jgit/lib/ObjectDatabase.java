/*
 * Copyright (C) 2009, 2022 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;

/**
 * Abstraction of arbitrary object storage.
 * <p>
 * An object database stores one or more Git objects, indexed by their unique
 * {@link org.eclipse.jgit.lib.ObjectId}.
 */
public abstract class ObjectDatabase implements AutoCloseable {

	private static final Set<ObjectId> shallowCommits = Collections.emptySet();

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
	 * Get the shallow commits of the current repository
	 *
	 * @return the shallow commits of the current repository
	 *
	 * @throws IOException
	 *             the database could not be read
	 *
	 * @since 6.3
	 */
	public Set<ObjectId> getShallowCommits() throws IOException {
		return shallowCommits;
	}


	/**
	 * Update the shallow commits of the current repository
	 *
	 * @param shallowCommits the new shallow commits
	 *
	 * @throws IOException the database could not be updated
	 *
	 * @since 6.3
	 */
	public void setShallowCommits(Set<ObjectId> shallowCommits)
			throws IOException {
		if (!shallowCommits.isEmpty()) {
			throw new UnsupportedOperationException(
					"Shallow commits expected to be empty."); //$NON-NLS-1$
		}
	}

	/**
	 * Close any resources held by this database.
	 */
	@Override
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
		try (ObjectReader or = newReader()) {
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
		try (ObjectReader or = newReader()) {
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

	/**
	 * Get a quick, rough count of objects in this repository. Ignores loose
	 * objects. Returns {@code -1} if an exception occurs.
	 *
	 * @return quick, rough count of objects in this repository, {@code -1} if
	 *         an exception occurs
	 * @since 6.1
	 */
	public abstract long getApproximateObjectCount();

	/**
	 * Get the bitmap index with the newest creation date.
	 *
	 * @return the latest PackBitMapIndex or null if no bitmaps are found
	 */
	public abstract PackBitmapIndex getLatestBitmapIndex() throws IOException;
}
