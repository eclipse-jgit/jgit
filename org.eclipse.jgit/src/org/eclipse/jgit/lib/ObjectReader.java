/*
 * Copyright (C) 2010, Google Inc.
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

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.RevObject;

/** Reads an {@link ObjectDatabase} for a single thread. */
public abstract class ObjectReader {
	/** Type hint indicating the caller doesn't know the type. */
	protected static final int OBJ_ANY = -1;

	/**
	 * Does the requested object exist in this database?
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return true if the specified object is stored in this database.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public boolean hasObject(AnyObjectId objectId) throws IOException {
		try {
			openObject(objectId);
			return true;
		} catch (MissingObjectException notFound) {
			return false;
		}
	}

	/**
	 * Open an object from this database.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link ObjectLoader} for accessing the object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 */
	public ObjectLoader openObject(AnyObjectId objectId)
			throws MissingObjectException, IOException {
		return openObject(objectId, OBJ_ANY);
	}

	/**
	 * Open an object from this database.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 *@param typeHint
	 *            hint about the type of object being requested;
	 *            {@link #OBJ_ANY} if the object type is not known, or does not
	 *            matter to the caller.
	 * @return a {@link ObjectLoader} for accessing the object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 */
	public abstract ObjectLoader openObject(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IOException;

	/**
	 * Allocate a new {@code PackWriter} state structure for an object.
	 * <p>
	 * {@link PackWriter} allocates these objects to keep track of the
	 * per-object state, and how to load the objects efficiently into the
	 * generated stream. Implementers may override this method to provide their
	 * own subclass with additional object state, such as to remember what file
	 * and position contains the object's data.
	 * <p>
	 * The default implementation of this object does not provide very efficient
	 * packing support; it inflates the object on the fly through {@code
	 * openObject} and deflates it again into the generated stream.
	 *
	 * @param obj
	 *            identity of the object that will be packed. The object's
	 *            parsed status is undefined here. Implementers must not rely on
	 *            the object being parsed.
	 * @return a new instance for this object.
	 */
	public ObjectToPack newObjectToPack(RevObject obj) {
		return new ObjectToPack(obj, obj.getType());
	}

	/**
	 * Release any resources used by this reader.
	 * <p>
	 * A reader that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 */
	public void release() {
		// Do nothing.
	}
}
