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
import java.util.HashSet;

/**
 * The cached instance of object directory. It caches list of unpacked objects.
 * So the file system is not queried for non-exiting objects.
 *
 * @author Constantine Plotnikov <constantine.plotnikov@gmail.com>
 */
public class CachedObjectDirectory extends CachedObjectDatabase {
	/**
	 * The set that contains unpacked objects identifiers, it is created when
	 * the cached instance is created.
	 */
	private final HashSet<AnyObjectId> unpackedObjects = new HashSet<AnyObjectId>();

	/**
	 * The constructor
	 *
	 * @param wrapped
	 *            the wrapped database
	 */
	public CachedObjectDirectory(ObjectDirectory wrapped) {
		super(wrapped);
		File objects = wrapped.getDirectory();
		String[] objectDirs = objects.list();
		if (objectDirs != null) {
			for (String d : objectDirs) {
				if (isHexString(2, d)) {
					String[] files = new File(objects, d).list();
					if (files != null) {
						for (String f : files) {
							if (isHexString(38, f)) {
								unpackedObjects.add(ObjectId.fromString(d + f));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Check if argument is a hex string of the specified size
	 *
	 * @param length
	 *            the expected length of the string
	 * @param s
	 *            the string to check
	 * @return true if the string is a correct hex string
	 */
	private static final boolean isHexString(int length, String s) {
		if (s.length() != length) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			char ch = s.charAt(i);
			if (!(('0' <= ch && ch <= '9') || ('a' <= ch && ch <= 'f') || ('A' <= ch && ch <= 'F'))) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected ObjectLoader openObject2(WindowCursor curs, String objectName,
			AnyObjectId objectId) throws IOException {
		return unpackedObjects.contains(objectId) ? super.openObject2(curs,
				objectName, objectId) : null;
	}
}
