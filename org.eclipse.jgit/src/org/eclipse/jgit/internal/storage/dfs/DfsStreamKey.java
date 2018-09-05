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

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Key used by {@link org.eclipse.jgit.internal.storage.dfs.DfsBlockCache} to disambiguate streams.
 */
public abstract class DfsStreamKey {
	/**
	 * Create a {@code DfsStreamKey}
	 *
	 * @param repo
	 *            description of the containing repository.
	 * @param name
	 *            compute the key from a string name.
	 * @param ext
	 *            pack file extension, or {@code null}.
	 * @return key for {@code name}
	 */
	public static DfsStreamKey of(DfsRepositoryDescription repo, String name,
			@Nullable PackExt ext) {
		return new ByteArrayDfsStreamKey(repo, name.getBytes(UTF_8), ext);
	}

	final int hash;

	final int packExtPos;

	/**
	 * Constructor for DfsStreamKey.
	 *
	 * @param hash
	 *            hash of the other identifying components of the key.
	 * @param ext
	 *            pack file extension, or {@code null}.
	 */
	protected DfsStreamKey(int hash, @Nullable PackExt ext) {
		// Multiply by 31 here so we can more directly combine with another
		// value without doing the multiply there.
		this.hash = hash * 31;
		this.packExtPos = ext == null ? 0 : ext.getPosition();
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return hash;
	}

	/** {@inheritDoc} */
	@Override
	public abstract boolean equals(Object o);

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	public String toString() {
		return String.format("DfsStreamKey[hash=%08x]", hash); //$NON-NLS-1$
	}

	private static final class ByteArrayDfsStreamKey extends DfsStreamKey {
		private final DfsRepositoryDescription repo;

		private final byte[] name;

		ByteArrayDfsStreamKey(DfsRepositoryDescription repo, byte[] name,
				@Nullable PackExt ext) {
			super(repo.hashCode() * 31 + Arrays.hashCode(name), ext);
			this.repo = repo;
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ByteArrayDfsStreamKey) {
				ByteArrayDfsStreamKey k = (ByteArrayDfsStreamKey) o;
				return hash == k.hash && repo.equals(k.repo)
						&& Arrays.equals(name, k.name);
			}
			return false;
		}
	}

	static final class ForReverseIndex extends DfsStreamKey {
		private final DfsStreamKey idxKey;

		ForReverseIndex(DfsStreamKey idxKey) {
			super(idxKey.hash + 1, null);
			this.idxKey = idxKey;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof ForReverseIndex
					&& idxKey.equals(((ForReverseIndex) o).idxKey);
		}
	}
}
