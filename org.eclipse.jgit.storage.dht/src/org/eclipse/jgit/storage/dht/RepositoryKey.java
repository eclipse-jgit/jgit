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

import static org.eclipse.jgit.storage.dht.KeyUtils.format32;
import static org.eclipse.jgit.storage.dht.KeyUtils.parse32;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import org.eclipse.jgit.lib.Constants;

/** */
public final class RepositoryKey implements RowKey {
	/**
	 * @param sequentialId
	 * @return the key
	 */
	public static RepositoryKey create(int sequentialId) {
		return new RepositoryKey(Integer.reverse(sequentialId));
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static RepositoryKey fromBytes(byte[] key) {
		return new RepositoryKey(parse32(key, 0));
	}

	/**
	 * @param key
	 * @return the key
	 */
	public static RepositoryKey fromString(String key) {
		return new RepositoryKey(parse32(Constants.encodeASCII(key), 0));
	}

	/**
	 * @param reverseId
	 * @return the key
	 */
	public static RepositoryKey fromInt(int reverseId) {
		return new RepositoryKey(reverseId);
	}

	private final int id;

	RepositoryKey(int id) {
		this.id = id;
	}

	/** @return 32 bit value describing the repository. */
	public int asInt() {
		return id;
	}

	public byte[] asBytes() {
		byte[] r = new byte[8];
		format32(r, 0, asInt());
		return r;
	}

	public String asString() {
		return decode(asBytes());
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof RepositoryKey)
			return id == ((RepositoryKey) other).id;
		return false;
	}

	@Override
	public String toString() {
		return "repository:" + asString();
	}
}
