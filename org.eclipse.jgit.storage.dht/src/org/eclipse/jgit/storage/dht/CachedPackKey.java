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

package org.eclipse.jgit.storage.dht;

import static org.eclipse.jgit.util.RawParseUtils.decode;

import java.text.MessageFormat;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.lib.ObjectId;

/** Unique identifier of a {@link CachedPackInfo} in the DHT. */
public final class CachedPackKey implements RowKey {
	static final int KEYLEN = 81;

	/**
	 * @param key
	 * @return the key
	 */
	public static CachedPackKey fromBytes(byte[] key) {
		return fromBytes(key, 0, key.length);
	}

	/**
	 * @param key
	 * @param ptr
	 * @param len
	 * @return the key
	 */
	public static CachedPackKey fromBytes(byte[] key, int ptr, int len) {
		if (len != KEYLEN)
			throw new IllegalArgumentException(MessageFormat.format(
					DhtText.get().invalidChunkKey, decode(key, ptr, ptr + len)));

		ObjectId name = ObjectId.fromString(key, ptr);
		ObjectId vers = ObjectId.fromString(key, ptr + 41);
		return new CachedPackKey(name, vers);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static CachedPackKey fromString(String key) {
		int d = key.indexOf('.');
		ObjectId name = ObjectId.fromString(key.substring(0, d));
		ObjectId vers = ObjectId.fromString(key.substring(d + 1));
		return new CachedPackKey(name, vers);
	}

	/**
	 * @param info
	 * @return the key
	 */
	public static CachedPackKey fromInfo(CachedPackInfo info) {
		ObjectId name = ObjectId.fromString(info.getName());
		ObjectId vers = ObjectId.fromString(info.getVersion());
		return new CachedPackKey(name, vers);
	}

	private final ObjectId name;

	private final ObjectId version;

	CachedPackKey(ObjectId name, ObjectId version) {
		this.name = name;
		this.version = version;
	}

	/** @return unique SHA-1 name of the pack. */
	public ObjectId getName() {
		return name;
	}

	/** @return unique version of the pack. */
	public ObjectId getVersion() {
		return version;
	}

	public byte[] asBytes() {
		byte[] r = new byte[KEYLEN];
		name.copyTo(r, 0);
		r[40] = '.';
		version.copyTo(r, 41);
		return r;
	}

	public String asString() {
		return name.name() + "." + version.name();
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof CachedPackKey) {
			CachedPackKey key = (CachedPackKey) other;
			return name.equals(key.name) && version.equals(key.version);
		}
		return false;
	}

	@Override
	public String toString() {
		return "cached-pack:" + asString();
	}
}
