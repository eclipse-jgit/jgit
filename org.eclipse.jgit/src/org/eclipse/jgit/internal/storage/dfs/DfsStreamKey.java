/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
