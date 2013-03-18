/*
 * Copyright (C) 2010, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2010, JetBrains s.r.o.
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

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.util.FS;

/**
 * The cached instance of an {@link ObjectDirectory}.
 * <p>
 * This class caches the list of loose objects in memory, so the file system is
 * not queried with stat calls.
 */
class CachedObjectDirectory extends FileObjectDatabase {
	/**
	 * The set that contains unpacked objects identifiers, it is created when
	 * the cached instance is created.
	 */
	private final ObjectIdOwnerMap<UnpackedObjectId> unpackedObjects = new ObjectIdOwnerMap<UnpackedObjectId>();

	private final ObjectDirectory wrapped;

	private AlternateHandle[] alts;

	/**
	 * The constructor
	 *
	 * @param wrapped
	 *            the wrapped database
	 */
	CachedObjectDirectory(ObjectDirectory wrapped) {
		this.wrapped = wrapped;

		File objects = wrapped.getDirectory();
		String[] fanout = objects.list();
		if (fanout == null)
			fanout = new String[0];
		for (String d : fanout) {
			if (d.length() != 2)
				continue;
			String[] entries = new File(objects, d).list();
			if (entries == null)
				continue;
			for (String e : entries) {
				if (e.length() != Constants.OBJECT_ID_STRING_LENGTH - 2)
					continue;
				try {
					ObjectId id = ObjectId.fromString(d + e);
					unpackedObjects.add(new UnpackedObjectId(id));
				} catch (IllegalArgumentException notAnObject) {
					// ignoring the file that does not represent loose object
				}
			}
		}
	}

	@Override
	public void close() {
		// Don't close anything.
	}

	@Override
	public ObjectDatabase newCachedDatabase() {
		return this;
	}

	@Override
	FileObjectDatabase newCachedFileObjectDatabase() {
		return this;
	}

	@Override
	File getDirectory() {
		return wrapped.getDirectory();
	}

	@Override
	Config getConfig() {
		return wrapped.getConfig();
	}

	@Override
	FS getFS() {
		return wrapped.getFS();
	}

	@Override
	Set<ObjectId> getShallowCommits() throws IOException {
		return wrapped.getShallowCommits();
	}

	@Override
	AlternateHandle[] myAlternates() {
		if (alts == null) {
			AlternateHandle[] src = wrapped.myAlternates();
			alts = new AlternateHandle[src.length];
			for (int i = 0; i < alts.length; i++) {
				FileObjectDatabase s = src[i].db;
				alts[i] = new AlternateHandle(s.newCachedFileObjectDatabase());
			}
		}
		return alts;
	}

	@Override
	void resolve(Set<ObjectId> matches, AbbreviatedObjectId id)
			throws IOException {
		// In theory we could accelerate the loose object scan using our
		// unpackedObjects map, but its not worth the huge code complexity.
		// Scanning a single loose directory is fast enough, and this is
		// unlikely to be called anyway.
		//
		wrapped.resolve(matches, id);
	}

	@Override
	boolean tryAgain1() {
		return wrapped.tryAgain1();
	}

	@Override
	public boolean has(final AnyObjectId objectId) {
		return hasObjectImpl1(objectId);
	}

	@Override
	boolean hasObject1(AnyObjectId objectId) {
		return unpackedObjects.contains(objectId)
				|| wrapped.hasObject1(objectId);
	}

	@Override
	ObjectLoader openObject(final WindowCursor curs,
			final AnyObjectId objectId) throws IOException {
		return openObjectImpl1(curs, objectId);
	}

	@Override
	ObjectLoader openObject1(WindowCursor curs, AnyObjectId objectId)
			throws IOException {
		if (unpackedObjects.contains(objectId))
			return wrapped.openObject2(curs, objectId.name(), objectId);
		return wrapped.openObject1(curs, objectId);
	}

	@Override
	boolean hasObject2(String objectId) {
		return unpackedObjects.contains(ObjectId.fromString(objectId));
	}

	@Override
	ObjectLoader openObject2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException {
		if (unpackedObjects.contains(objectId))
			return wrapped.openObject2(curs, objectName, objectId);
		return null;
	}

	@Override
	long getObjectSize1(WindowCursor curs, AnyObjectId objectId) throws IOException {
		if (unpackedObjects.contains(objectId))
			return wrapped.getObjectSize2(curs, objectId.name(), objectId);
		return wrapped.getObjectSize1(curs, objectId);
	}

	@Override
	long getObjectSize2(WindowCursor curs, String objectName, AnyObjectId objectId)
			throws IOException {
		if (unpackedObjects.contains(objectId))
			return wrapped.getObjectSize2(curs, objectName, objectId);
		return -1;
	}

	@Override
	InsertLooseObjectResult insertUnpackedObject(File tmp, ObjectId objectId,
			boolean createDuplicate) throws IOException {
		InsertLooseObjectResult result = wrapped.insertUnpackedObject(tmp,
				objectId, createDuplicate);
		switch (result) {
		case INSERTED:
		case EXISTS_LOOSE:
			unpackedObjects.addIfAbsent(new UnpackedObjectId(objectId));
			break;

		case EXISTS_PACKED:
		case FAILURE:
			break;
		}
		return result;
	}

	@Override
	PackFile openPack(File pack) throws IOException {
		return wrapped.openPack(pack);
	}

	@Override
	void selectObjectRepresentation(PackWriter packer, ObjectToPack otp,
			WindowCursor curs) throws IOException {
		wrapped.selectObjectRepresentation(packer, otp, curs);
	}

	@Override
	Collection<PackFile> getPacks() {
		return wrapped.getPacks();
	}

	private static class UnpackedObjectId extends ObjectIdOwnerMap.Entry {
		UnpackedObjectId(AnyObjectId id) {
			super(id);
		}
	}
}
