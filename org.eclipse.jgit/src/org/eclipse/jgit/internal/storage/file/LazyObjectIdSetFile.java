/*
 * Copyright (C) 2015, Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectIdSet;

/**
 * Lazily loads a set of ObjectIds, one per line.
 */
public class LazyObjectIdSetFile implements ObjectIdSet {
	private final File src;
	private ObjectIdOwnerMap<Entry> set;

	/**
	 * Create a new lazy set from a file.
	 *
	 * @param src
	 *            the source file.
	 */
	public LazyObjectIdSetFile(File src) {
		this.src = src;
	}

	/** {@inheritDoc} */
	@Override
	public boolean contains(AnyObjectId objectId) {
		if (set == null) {
			set = load();
		}
		return set.contains(objectId);
	}

	private ObjectIdOwnerMap<Entry> load() {
		ObjectIdOwnerMap<Entry> r = new ObjectIdOwnerMap<>();
		try (FileInputStream fin = new FileInputStream(src);
				Reader rin = new InputStreamReader(fin, UTF_8);
				BufferedReader br = new BufferedReader(rin)) {
			MutableObjectId id = new MutableObjectId();
			for (String line; (line = br.readLine()) != null;) {
				id.fromString(line);
				if (!r.contains(id)) {
					r.add(new Entry(id));
				}
			}
		} catch (IOException e) {
			// Ignore IO errors accessing the lazy set.
		}
		return r;
	}

	static class Entry extends ObjectIdOwnerMap.Entry {
		Entry(AnyObjectId id) {
			super(id);
		}
	}
}
