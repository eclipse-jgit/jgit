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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.FS;

abstract class FileObjectDatabase extends ObjectDatabase {
	static enum InsertLooseObjectResult {
		INSERTED, EXISTS_PACKED, EXISTS_LOOSE, FAILURE;
	}

	@Override
	public ObjectReader newReader() {
		return new WindowCursor(this);
	}

	@Override
	public ObjectDirectoryInserter newInserter() {
		return new ObjectDirectoryInserter(this, getConfig());
	}

	/**
	 * Does the requested object exist in this database?
	 * <p>
	 * Alternates (if present) are searched automatically.
	 *
	 * @param objectId
	 *            identity of the object to test for existence of.
	 * @return true if the specified object is stored in this database, or any
	 *         of the alternate databases.
	 */
	public boolean has(final AnyObjectId objectId) {
		return hasObjectImpl1(objectId) || hasObjectImpl2(objectId.name());
	}

	/**
	 * Compute the location of a loose object file.
	 *
	 * @param objectId
	 *            identity of the loose object to map to the directory.
	 * @return location of the object, if it were to exist as a loose object.
	 */
	File fileFor(final AnyObjectId objectId) {
		return fileFor(objectId.name());
	}

	File fileFor(final String objectName) {
		final String d = objectName.substring(0, 2);
		final String f = objectName.substring(2);
		return new File(new File(getDirectory(), d), f);
	}

	final boolean hasObjectImpl1(final AnyObjectId objectId) {
		if (hasObject1(objectId))
			return true;

		for (final AlternateHandle alt : myAlternates()) {
			if (alt.db.hasObjectImpl1(objectId))
				return true;
		}

		return tryAgain1() && hasObject1(objectId);
	}

	final boolean hasObjectImpl2(final String objectId) {
		if (hasObject2(objectId))
			return true;

		for (final AlternateHandle alt : myAlternates()) {
			if (alt.db.hasObjectImpl2(objectId))
				return true;
		}

		return false;
	}

	abstract void resolve(Set<ObjectId> matches, AbbreviatedObjectId id)
			throws IOException;

	abstract Config getConfig();

	abstract FS getFS();

	abstract Set<ObjectId> getShallowCommits() throws IOException;

	/**
	 * Open an object from this database.
	 * <p>
	 * Alternates (if present) are searched automatically.
	 *
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	ObjectLoader openObject(final WindowCursor curs, final AnyObjectId objectId)
			throws IOException {
		ObjectLoader ldr;

		ldr = openObjectImpl1(curs, objectId);
		if (ldr != null)
			return ldr;

		ldr = openObjectImpl2(curs, objectId.name(), objectId);
		if (ldr != null)
			return ldr;

		return null;
	}

	final ObjectLoader openObjectImpl1(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		ObjectLoader ldr;

		ldr = openObject1(curs, objectId);
		if (ldr != null)
			return ldr;

		for (final AlternateHandle alt : myAlternates()) {
			ldr = alt.db.openObjectImpl1(curs, objectId);
			if (ldr != null)
				return ldr;
		}

		if (tryAgain1()) {
			ldr = openObject1(curs, objectId);
			if (ldr != null)
				return ldr;
		}

		return null;
	}

	final ObjectLoader openObjectImpl2(final WindowCursor curs,
			final String objectName, final AnyObjectId objectId)
			throws IOException {
		ObjectLoader ldr;

		ldr = openObject2(curs, objectName, objectId);
		if (ldr != null)
			return ldr;

		for (final AlternateHandle alt : myAlternates()) {
			ldr = alt.db.openObjectImpl2(curs, objectName, objectId);
			if (ldr != null)
				return ldr;
		}

		return null;
	}

	long getObjectSize(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		long sz = getObjectSizeImpl1(curs, objectId);
		if (0 <= sz)
			return sz;
		return getObjectSizeImpl2(curs, objectId.name(), objectId);
	}

	final long getObjectSizeImpl1(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		long sz;

		sz = getObjectSize1(curs, objectId);
		if (0 <= sz)
			return sz;

		for (final AlternateHandle alt : myAlternates()) {
			sz = alt.db.getObjectSizeImpl1(curs, objectId);
			if (0 <= sz)
				return sz;
		}

		if (tryAgain1()) {
			sz = getObjectSize1(curs, objectId);
			if (0 <= sz)
				return sz;
		}

		return -1;
	}

	final long getObjectSizeImpl2(final WindowCursor curs,
			final String objectName, final AnyObjectId objectId)
			throws IOException {
		long sz;

		sz = getObjectSize2(curs, objectName, objectId);
		if (0 <= sz)
			return sz;

		for (final AlternateHandle alt : myAlternates()) {
			sz = alt.db.getObjectSizeImpl2(curs, objectName, objectId);
			if (0 <= sz)
				return sz;
		}

		return -1;
	}

	abstract void selectObjectRepresentation(PackWriter packer,
			ObjectToPack otp, WindowCursor curs) throws IOException;

	abstract File getDirectory();

	abstract Collection<? extends CachedPack> getCachedPacks()
			throws IOException;

	abstract AlternateHandle[] myAlternates();

	abstract boolean tryAgain1();

	abstract boolean hasObject1(AnyObjectId objectId);

	abstract boolean hasObject2(String objectId);

	abstract ObjectLoader openObject1(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	abstract ObjectLoader openObject2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException;

	abstract long getObjectSize1(WindowCursor curs, AnyObjectId objectId)
			throws IOException;

	abstract long getObjectSize2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException;

	abstract InsertLooseObjectResult insertUnpackedObject(File tmp,
			ObjectId id, boolean createDuplicate) throws IOException;

	abstract PackFile openPack(File pack) throws IOException;

	abstract FileObjectDatabase newCachedFileObjectDatabase();

	abstract Collection<PackFile> getPacks();

	static class AlternateHandle {
		final FileObjectDatabase db;

		AlternateHandle(FileObjectDatabase db) {
			this.db = db;
		}

		@SuppressWarnings("unchecked")
		Collection<CachedPack> getCachedPacks() throws IOException {
			return (Collection<CachedPack>) db.getCachedPacks();
		}

		void close() {
			db.close();
		}
	}

	static class AlternateRepository extends AlternateHandle {
		final FileRepository repository;

		AlternateRepository(FileRepository r) {
			super(r.getObjectDatabase());
			repository = r;
		}

		void close() {
			repository.close();
		}
	}
}
