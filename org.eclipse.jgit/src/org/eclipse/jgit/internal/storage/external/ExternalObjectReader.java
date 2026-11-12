/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Reader for an {@link ExternalObjectDatabase}.
 * <p>
 * Every lookup asks the store. Abbreviated ids are not resolved: a store
 * cannot search by prefix cheaply, and abbreviations almost always name
 * commits, which are never stored here.
 */
class ExternalObjectReader extends ObjectReader {

	private final ExternalObjectDatabase db;

	ExternalObjectReader(ExternalObjectDatabase db) {
		this.db = db;
	}

	@Override
	public ObjectReader newReader() {
		return db.newReader();
	}

	@Override
	public Collection<ObjectId> resolve(AbbreviatedObjectId id)
			throws IOException {
		if (id.isComplete()) {
			return Collections.singleton(id.toObjectId());
		}
		return Collections.emptySet();
	}

	@Override
	public boolean has(AnyObjectId objectId) throws IOException {
		return db.has(objectId);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		ObjectLoader ldr = db.openObject(objectId);
		if (ldr == null) {
			if (typeHint == OBJ_ANY) {
				throw new MissingObjectException(objectId.copy(),
						JGitText.get().unknownObjectType2);
			}
			throw new MissingObjectException(objectId.copy(), typeHint);
		}
		if (typeHint != OBJ_ANY && ldr.getType() != typeHint) {
			throw new IncorrectObjectTypeException(objectId.copy(), typeHint);
		}
		return ldr;
	}

	@Override
	public long getObjectSize(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return open(objectId, typeHint).getSize();
	}

	@Override
	public Set<ObjectId> getShallowCommits() throws IOException {
		return Collections.emptySet();
	}

	@Override
	public void close() {
		// Nothing to release; loaders open the store on demand.
	}
}
