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

import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.storage.dht.KeyUtils.format32;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse32;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import org.eclipse.jgit.lib.Constants;

/** Unique identifier of a reference in the DHT. */
public final class RefKey implements RowKey {
	/**
	 * @param repo
	 * @param name
	 * @return the key
	 */
	public static RefKey create(RepositoryKey repo, String name) {
		return new RefKey(repo.asInt(), name);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static RefKey fromBytes(byte[] key) {
		int repo = parse32(key, 0);
		String name = decode(key, 9, key.length);
		return new RefKey(repo, name);
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static RefKey fromString(String key) {
		int c = key.indexOf(':');
		int repo = parse32(Constants.encodeASCII(key.substring(0, c)), 0);
		String name = key.substring(c + 1);
		return new RefKey(repo, name);
	}

	private final int repo;

	private final String name;

	RefKey(int repo, String name) {
		this.repo = repo;
		this.name = name;
	}

	/** @return the repository this reference lives within. */
	public RepositoryKey getRepositoryKey() {
		return RepositoryKey.fromInt(repo);
	}

	/** @return the name of the reference. */
	public String getName() {
		return name;
	}

	public byte[] asBytes() {
		byte[] nameRaw = encode(name);
		byte[] r = new byte[9 + nameRaw.length];
		format32(r, 0, repo);
		r[8] = ':';
		System.arraycopy(nameRaw, 0, r, 9, nameRaw.length);
		return r;
	}

	public String asString() {
		return getRepositoryKey().asString() + ":" + name;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof RefKey) {
			RefKey thisRef = this;
			RefKey otherRef = (RefKey) other;
			return thisRef.repo == otherRef.repo
					&& thisRef.name.equals(otherRef.name);
		}
		return false;
	}

	@Override
	public String toString() {
		return "ref:" + asString();
	}
}
