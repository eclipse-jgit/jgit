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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;

/**
 * The cached instance of an {@link ObjectDirectory}.
 * <p>
 * This class caches the list of loose objects in memory, so the file system is
 * not queried with stat calls.
 */
public class CachedObjectDirectory extends CachedObjectDatabase {
	/**
	 * The set that contains unpacked objects identifiers, it is created when
	 * the cached instance is created.
	 */
	private final ObjectIdSubclassMap<ObjectId> unpackedObjects = new ObjectIdSubclassMap<ObjectId>();

	/**
	 * The constructor
	 *
	 * @param wrapped
	 *            the wrapped database
	 */
	public CachedObjectDirectory(ObjectDirectory wrapped) {
		super(wrapped);
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
					unpackedObjects.add(ObjectId.fromString(d + e));
				} catch (IllegalArgumentException notAnObject) {
					// ignoring the file that does not represent loose object
				}
			}
		}
	}

	@Override
	protected ObjectLoader openObject2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException {
		if (unpackedObjects.get(objectId) == null)
			return null;
		return super.openObject2(curs, objectName, objectId);
	}

	@Override
	protected boolean hasObject1(AnyObjectId objectId) {
		if (unpackedObjects.get(objectId) != null)
			return true; // known to be loose
		return super.hasObject1(objectId);
	}

	@Override
	protected boolean hasObject2(String name) {
		return false; // loose objects were tested by hasObject1
	}
}
