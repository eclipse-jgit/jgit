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

package org.eclipse.jgit.internal.storage.dfs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;

/** Key used by {@link DfsBlockCache} to disambiguate streams. */
public final class DfsStreamKey {
	private final byte[] name;
	final int hash;

	/**
	 * @param name
	 *            compute the key from a string name.
	 */
	public DfsStreamKey(String name) {
		this(name.getBytes(UTF_8));
	}

	/**
	 * @param name
	 *            compute the key from a byte array. The key takes ownership of
	 *            the passed {@code byte[] name}.
	 */
	public DfsStreamKey(byte[] name) {
		// Multiply by 31 here so we can more directly combine with another
		// value without doing the multiply there.
		this.hash = Arrays.hashCode(name) * 31;
		this.name = name;
	}

	/**
	 * Derive a new StreamKey based on this existing key.
	 *
	 * @param suffix
	 *            a derivation suffix.
	 * @return derived stream key.
	 */
	public DfsStreamKey derive(String suffix) {
		byte[] s = suffix.getBytes(UTF_8);
		byte[] n = Arrays.copyOf(name, name.length + s.length);
		System.arraycopy(s, 0, n, name.length, s.length);
		return new DfsStreamKey(n);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof DfsStreamKey) {
			DfsStreamKey k = (DfsStreamKey) o;
			return hash == k.hash && Arrays.equals(name, k.name);
		}
		return false;
	}

	@SuppressWarnings("boxing")
	@Override
	public String toString() {
		return String.format("DfsStreamKey[hash=%08x]", hash); //$NON-NLS-1$
	}
}
