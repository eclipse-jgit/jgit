/*
 * Copyright (C) 2017, Google Inc.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftable.MergedReftable;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.Reftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * A {@link org.eclipse.jgit.internal.storage.dfs.DfsRefDatabase} that uses
 * reftable for storage.
 * <p>
 * A {@code DfsRefDatabase} instance is thread-safe.
 * <p>
 * Implementors may wish to use
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsPackDescription#getMaxUpdateIndex()}
 * as the primary key identifier for a
 * {@link org.eclipse.jgit.internal.storage.pack.PackExt#REFTABLE} only pack
 * description, ensuring that when there are competing transactions one wins,
 * and one will fail.
 */
public class DfsReftableDatabase extends DfsRefDatabase {
	private final ReentrantLock lock = new ReentrantLock(true);

	private DfsReader ctx;

	private ReftableStack tableStack;

	private MergedReftable mergedTables;

	/**
	 * Initialize the reference database for a repository.
	 *
	 * @param repo
	 *            the repository this database instance manages references for.
	 */
	protected DfsReftableDatabase(DfsRepository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public BatchRefUpdate newBatchUpdate() {
		DfsObjDatabase odb = getRepository().getObjectDatabase();
		return new ReftableBatchRefUpdate(this, odb);
	}

	/**
	 * Get configuration to write new reftables with.
	 *
	 * @return configuration to write new reftables with.
	 */
	public ReftableConfig getReftableConfig() {
		return new ReftableConfig(getRepository().getConfig());
	}

	/**
	 * Get the lock protecting this instance's state.
	 *
	 * @return the lock protecting this instance's state.
	 */
	protected ReentrantLock getLock() {
		return lock;
	}

	/**
	 * Whether to compact reftable instead of extending the stack depth.
	 *
	 * @return {@code true} if commit of a new small reftable should try to
	 *         replace a prior small reftable by performing a compaction,
	 *         instead of extending the stack depth.
	 */
	protected boolean compactDuringCommit() {
		return true;
	}

	/**
	 * Obtain a handle to the merged reader.
	 *
	 * @return (possibly cached) handle to the merged reader.
	 * @throws java.io.IOException
	 *             if tables cannot be opened.
	 */
	protected Reftable reader() throws IOException {
		lock.lock();
		try {
			if (mergedTables == null) {
				mergedTables = new MergedReftable(stack().readers());
			}
			return mergedTables;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Obtain a handle to the stack of reftables.
	 *
	 * @return (possibly cached) handle to the stack.
	 * @throws java.io.IOException
	 *             if tables cannot be opened.
	 */
	protected ReftableStack stack() throws IOException {
		lock.lock();
		try {
			if (tableStack == null) {
				DfsObjDatabase odb = getRepository().getObjectDatabase();
				if (ctx == null) {
					ctx = odb.newReader();
				}
				tableStack = ReftableStack.open(ctx,
						Arrays.asList(odb.getReftables()));
			}
			return tableStack;
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean isNameConflicting(String refName) throws IOException {
		lock.lock();
		try {
			Reftable table = reader();

			// Cannot be nested within an existing reference.
			int lastSlash = refName.lastIndexOf('/');
			while (0 < lastSlash) {
				if (table.hasRef(refName.substring(0, lastSlash))) {
					return true;
				}
				lastSlash = refName.lastIndexOf('/', lastSlash - 1);
			}

			// Cannot be the container of an existing reference.
			return table.hasRef(refName + '/');
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		lock.lock();
		try {
			Reftable table = reader();
			Ref ref = table.exactRef(name);
			if (ref != null && ref.isSymbolic()) {
				return table.resolve(ref);
			}
			return ref;
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	public Ref getRef(String needle) throws IOException {
		for (String prefix : SEARCH_PATH) {
			Ref ref = exactRef(prefix + needle);
			if (ref != null) {
				return ref;
			}
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<>();
		lock.lock();
		try {
			Reftable table = reader();
			try (RefCursor rc = ALL.equals(prefix) ? table.allRefs()
					: table.seekRef(prefix)) {
				while (rc.next()) {
					Ref ref = table.resolve(rc.getRef());
					if (ref != null) {
						all.add(ref);
					}
				}
			}
		} finally {
			lock.unlock();
		}

		RefList<Ref> none = RefList.emptyList();
		return new RefMap(prefix, all.toRefList(), none, none);
	}

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
		Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null) {
			return ref;
		}
		return recreate(ref, doPeel(oldLeaf));
	}

	@Override
	boolean exists() throws IOException {
		DfsObjDatabase odb = getRepository().getObjectDatabase();
		return odb.getReftables().length > 0;
	}

	@Override
	void clearCache() {
		lock.lock();
		try {
			if (tableStack != null) {
				tableStack.close();
				tableStack = null;
			}
			if (ctx != null) {
				ctx.close();
				ctx = null;
			}
			mergedTables = null;
		} finally {
			lock.unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected boolean compareAndPut(Ref oldRef, @Nullable Ref newRef)
			throws IOException {
		ReceiveCommand cmd = toCommand(oldRef, newRef);
		try (RevWalk rw = new RevWalk(getRepository())) {
			newBatchUpdate().setAllowNonFastForwards(true).addCommand(cmd)
					.execute(rw, NullProgressMonitor.INSTANCE);
		}
		switch (cmd.getResult()) {
		case OK:
			return true;
		case REJECTED_OTHER_REASON:
			throw new IOException(cmd.getMessage());
		case LOCK_FAILURE:
		default:
			return false;
		}
	}

	private static ReceiveCommand toCommand(Ref oldRef, Ref newRef) {
		ObjectId oldId = toId(oldRef);
		ObjectId newId = toId(newRef);
		String name = toName(oldRef, newRef);

		if (oldRef != null && oldRef.isSymbolic()) {
			if (newRef != null) {
				if (newRef.isSymbolic()) {
					return ReceiveCommand.link(oldRef.getTarget().getName(),
							newRef.getTarget().getName(), name);
				} else {
					return ReceiveCommand.unlink(oldRef.getTarget().getName(),
							newId, name);
				}
			} else {
				return ReceiveCommand.unlink(oldRef.getTarget().getName(),
						ObjectId.zeroId(), name);
			}
		}

		if (newRef != null && newRef.isSymbolic()) {
			if (oldRef != null) {
				if (oldRef.isSymbolic()) {
					return ReceiveCommand.link(oldRef.getTarget().getName(),
							newRef.getTarget().getName(), name);
				} else {
					return ReceiveCommand.link(oldId,
							newRef.getTarget().getName(), name);
				}
			} else {
				return ReceiveCommand.link(ObjectId.zeroId(),
						newRef.getTarget().getName(), name);
			}
		}

		return new ReceiveCommand(oldId, newId, name);
	}

	private static ObjectId toId(Ref ref) {
		if (ref != null) {
			ObjectId id = ref.getObjectId();
			if (id != null) {
				return id;
			}
		}
		return ObjectId.zeroId();
	}

	private static String toName(Ref oldRef, Ref newRef) {
		return oldRef != null ? oldRef.getName() : newRef.getName();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean compareAndRemove(Ref oldRef) throws IOException {
		return compareAndPut(oldRef, null);
	}

	/** {@inheritDoc} */
	@Override
	protected RefCache scanAllRefs() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	void stored(Ref ref) {
		// Unnecessary; ReftableBatchRefUpdate calls clearCache().
	}

	@Override
	void removed(String refName) {
		// Unnecessary; ReftableBatchRefUpdate calls clearCache().
	}

	/** {@inheritDoc} */
	@Override
	protected void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
		// Do not cache peeled state in reftable.
	}
}
