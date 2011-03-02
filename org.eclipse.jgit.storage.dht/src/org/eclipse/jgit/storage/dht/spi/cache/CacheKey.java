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

import org.eclipse.jgit.storage.dht.RowKey;
import org.eclipse.jgit.util.RawParseUtils;

/** Simple byte array based key for cache storage. */
public class CacheKey {
	private final Namespace ns;

	private final byte[] key;

	private volatile int hashCode;

	/**
	 * Wrap a database key.
	 *
	 * @param ns
	 *            the namespace the key is contained within.
	 * @param key
	 *            the key to wrap.
	 */
	public CacheKey(Namespace ns, RowKey key) {
		this(ns, key.asBytes());
	}

	/**
	 * Wrap a byte array.
	 *
	 * @param ns
	 *            the namespace the key is contained within.
	 * @param key
	 *            the key to wrap.
	 */
	public CacheKey(Namespace ns, byte[] key) {
		this.ns = ns;
		this.key = key;
	}

	/** @return namespace to segregate keys by. */
	public Namespace getNamespace() {
		return ns;
	}

	/** @return this key's bytes, within {@link #getNamespace()}. */
	public byte[] getBytes() {
		return key;
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			int h = 5381;
			for (int ptr = 0; ptr < key.length; ptr++)
				h = ((h << 5) + h) + (key[ptr] & 0xff);
			if (h == 0)
				h = 1;
			hashCode = h;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other instanceof CacheKey) {
			CacheKey m = (CacheKey) other;
			return ns.equals(m.ns) && Arrays.equals(key, m.key);
		}
		return false;
	}

	@Override
	public String toString() {
		return ns + ":" + RawParseUtils.decode(key);
	}
}
