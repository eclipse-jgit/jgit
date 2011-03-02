/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht.spi.cache;

import java.util.Arrays;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.dht.RowKey;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;
import org.eclipse.jgit.util.RawParseUtils;

/** Defines a space within the cache cluster. */
public class Namespace {
	/** Namespace used by the {@link ChunkTable}. */
	public static final Namespace CHUNK = create("chunk");

	/** Namespace used by the {@link ChunkTable} for meta field only. */
	public static final Namespace CHUNK_META = create("chunkMeta");

	/** Namespace used by the {@link ObjectIndexTable}. */
	public static final Namespace OBJECT_INDEX = create("objectIndex");

	/** Namespace used by the {@link RepositoryIndexTable}. */
	public static final Namespace REPOSITORY_INDEX = create("repositoryIndex");

	/** Namespace used by the cached pack information. */
	public static final Namespace CACHED_PACK = create("cachedPack");

	/**
	 * Create a namespace from a string name.
	 *
	 * @param name
	 *            the name to wrap.
	 * @return the namespace.
	 */
	public static Namespace create(String name) {
		return new Namespace(Constants.encode(name));
	}

	/**
	 * Create a namespace from a byte array.
	 *
	 * @param name
	 *            the name to wrap.
	 * @return the namespace.
	 */
	public static Namespace create(byte[] name) {
		return new Namespace(name);
	}

	private final byte[] name;

	private volatile int hashCode;

	private Namespace(byte[] name) {
		this.name = name;
	}

	/** @return this namespace, encoded in UTF-8. */
	public byte[] getBytes() {
		return name;
	}

	/**
	 * Construct a MemKey within this namespace.
	 *
	 * @param key
	 *            the key to include.
	 * @return key within this namespace.
	 */
	public CacheKey key(byte[] key) {
		return new CacheKey(this, key);
	}

	/**
	 * Construct a MemKey within this namespace.
	 *
	 * @param key
	 *            the key to include.
	 * @return key within this namespace.
	 */
	public CacheKey key(RowKey key) {
		return new CacheKey(this, key);
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			int h = 5381;
			for (int ptr = 0; ptr < name.length; ptr++)
				h = ((h << 5) + h) + (name[ptr] & 0xff);
			if (h == 0)
				h = 1;
			hashCode = h;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this)
			return true;
		if (other instanceof Namespace)
			return Arrays.equals(name, ((Namespace) other).name);
		return false;
	}

	@Override
	public String toString() {
		return RawParseUtils.decode(name);
	}
}
