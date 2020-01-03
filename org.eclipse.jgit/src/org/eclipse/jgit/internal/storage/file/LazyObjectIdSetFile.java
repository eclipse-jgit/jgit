/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
