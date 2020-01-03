/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

/**
 * An {@link InMemoryRepository} whose refs can be made unreadable for testing
 * purposes.
 */
class RefsUnreadableInMemoryRepository extends InMemoryRepository {

	private final RefsUnreadableRefDatabase refs;

	private volatile boolean failing;

	RefsUnreadableInMemoryRepository(DfsRepositoryDescription repoDesc) {
		super(repoDesc);
		refs = new RefsUnreadableRefDatabase();
		failing = false;
	}

	/** {@inheritDoc} */
	@Override
	public RefDatabase getRefDatabase() {
		return refs;
	}

	/**
	 * Make the ref database unable to scan its refs.
	 * <p>
	 * It may be useful to follow a call to startFailing with a call to
	 * {@link RefDatabase#refresh()}, ensuring the next ref read fails.
	 */
	void startFailing() {
		failing = true;
	}

	private class RefsUnreadableRefDatabase extends MemRefDatabase {

		/** {@inheritDoc} */
		@Override
		public Ref exactRef(String name) throws IOException {
			if (failing) {
				throw new IOException("disk failed, no refs found");
			}
			return super.exactRef(name);
		}

		/** {@inheritDoc} */
		@Override
		public Map<String, Ref> getRefs(String prefix) throws IOException {
			if (failing) {
				throw new IOException("disk failed, no refs found");
			}

			return super.getRefs(prefix);
		}

		/** {@inheritDoc} */
		@Override
		public List<Ref> getRefsByPrefix(String prefix) throws IOException {
			if (failing) {
				throw new IOException("disk failed, no refs found");
			}

			return super.getRefsByPrefix(prefix);
		}

		/** {@inheritDoc} */
		@Override
		public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
			if (failing) {
				throw new IOException("disk failed, no refs found");
			}
			return super.getTipsWithSha1(id);
		}
	}
}
