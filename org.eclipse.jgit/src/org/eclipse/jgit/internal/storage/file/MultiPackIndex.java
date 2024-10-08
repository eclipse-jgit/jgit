/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * The MultiPackIndex is a supplemental data structure that accelerates objects
 * retrieval.
 */
public interface MultiPackIndex {

	/**
	 * Set the PackDirectory for this multi-pack-index
	 *
	 * @param packDir
	 * 		the PackDirectory for this multi-pack-index
	 */
	void setPackDir(PackDirectory packDir);

	/**
	 * Obtain the array of packfiles in the MultiPackIndex.
	 *
	 * @return array of packfiles in the MultiPackIndex.
	 */
	String[] getPackFileNames();

	/**
	 * Obtain the number of packfiles in the MultiPackIndex.
	 *
	 * @return number of packfiles in the MultiPackIndex.
	 */
	long getPackFilesCount();

	/**
	 * Are objects for this {@code Pack} contained within this
	 * multi-pack-index?
	 * <br>
	 * The {@code Pack} is not checked against the list of valid Packs as part
	 * of this check.
	 *
	 * @param pack
	 *        {@code Pack} to check for existence
	 * @return {@code true} if this multi-pack-index contains objects for this
	 * 		Pack
	 */
	boolean containsPack(Pack pack);

	/**
	 * Select the best object representation for a packer.
	 *
	 * @param curs
	 * 		cursor to read via
	 * @param objectId
	 * 		identity of the object to find a representation for
	 * @return An {@code Optional} {@code LocalObjectRepresentation} that is
	 * 		present when the object is found in this multi-pack-index
	 * @throws IOException
	 * 		IOException when ... FIXME
	 */
	Optional<LocalObjectRepresentation> representation(WindowCursor curs,
			AnyObjectId objectId) throws IOException;

	/**
	 * Get only the size of an object.
	 *
	 * @param objectId
	 * 		identity of the object to get the size of.
	 * @param curs
	 * 		cursor to read via
	 * @return size of object in bytes.
	 * @throws IOException
	 * 		IOException when ... FIXME
	 */
	long getObjectSize(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	/**
	 * Get the {@code Pack} this object is contained in
	 *
	 * @param objectId
	 * 		identity of the object to get the Pack for
	 * @return An {@code Optional} {@code Pack} that is present when the object
	 * 		is found in this multi-pack-index
	 */
	Optional<Pack> getPack(AnyObjectId objectId);

	/**
	 * Open an object for reading
	 *
	 * @param objectId
	 * 		identity of the object to open.
	 * @param curs
	 * 		cursor to read via
	 * @return an {@code ObjectLoader} for reading this object
	 * @throws IOException
	 * 		IOException when ... FIXME
	 */
	ObjectLoader open(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	/**
	 * Find objects matching the prefix abbreviation.
	 *
	 * @param matches
	 * 		set to add any located ObjectIds to. This is an output parameter.
	 * @param id
	 * 		prefix to search for.
	 * @param matchLimit
	 * 		maximum number of results to return. At most this many ObjectIds should
	 * 		be added to matches before returning.
	 */
	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id, int matchLimit);
}
