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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftable.*;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

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
	private final ReftableDatabase reftableDatabase;

	private DfsReader ctx;

	private DfsReftableStack tableStack;

	/**
	 * Initialize the reference database for a repository.
	 *
	 * @param repo
	 *            the repository this database instance manages references for.
	 */
	protected DfsReftableDatabase(DfsRepository repo) {
		super(repo);
		reftableDatabase = new ReftableDatabase(() -> new MergedReftable(stack().readers()));
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasVersioning() {
		return true;
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
		return new DfsReftableBatchRefUpdate(this, odb);
	}

	/**
	 * Get configuration to write new reftables with.
	 *
	 * @return configuration to write new reftables with.
	 */
	public ReftableConfig getReftableConfig() {
		return new ReftableConfig(getRepository());
	}

	/**
	 * Get the lock protecting this instance's state.
	 *
	 * @return the lock protecting this instance's state.
	 */
	protected ReentrantLock getLock() {
		return reftableDatabase.getLock();
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
		return reftableDatabase.reader();
	}

	/**
	 * Obtain a handle to the stack of reftables.
	 *
	 * @return (possibly cached) handle to the stack.
	 * @throws java.io.IOException
	 *             if tables cannot be opened.
	 */
	protected DfsReftableStack stack() throws IOException {
		getLock().lock();
		try {
			if (tableStack == null) {
				DfsObjDatabase odb = getRepository().getObjectDatabase();
				if (ctx == null) {
					ctx = odb.newReader();
				}
				tableStack = DfsReftableStack.open(ctx,
						Arrays.asList(odb.getReftables()));
			}
			return tableStack;
		} finally {
			getLock().unlock();
		}
	}

	@Override
	public boolean isNameConflicting(String refName) throws IOException {
		return reftableDatabase.isNameConflicting(refName);
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		return reftableDatabase.exactRef(name);
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {

		return reftableDatabase.getRefs(prefix);
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> getRefsByPrefix(String prefix) throws IOException {

		return reftableDatabase.getRefsByPrefix(prefix);
	}

	/** {@inheritDoc} */
	@Override
	public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
		if (!getReftableConfig().isIndexObjects()) {
			return super.getTipsWithSha1(id);
		}
		return reftableDatabase.getTipsWithSha1(id);
	}

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
		Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null) {
			return ref;
		}
		return recreate(ref, doPeel(oldLeaf), hasVersioning());
	}

	@Override
	boolean exists() throws IOException {
		DfsObjDatabase odb = getRepository().getObjectDatabase();
		return odb.getReftables().length > 0;
	}

	@Override
	void clearCache() {
		getLock().lock();
		try {
			if (tableStack != null) {
				tableStack.close();
				tableStack = null;
			}
			if (ctx != null) {
				ctx.close();
				ctx = null;
			}
			reftableDatabase.clearCache();
		} finally {
			getLock().unlock();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected boolean compareAndPut(Ref oldRef, @Nullable Ref newRef)
			throws IOException {
		ReceiveCommand cmd = toCommand(oldRef, newRef);
		try (RevWalk rw = new RevWalk(getRepository())) {
			rw.setRetainBody(false);
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
		// Unnecessary; DfsReftableBatchRefUpdate calls clearCache().
	}

	@Override
	void removed(String refName) {
		// Unnecessary; DfsReftableBatchRefUpdate calls clearCache().
	}

	/** {@inheritDoc} */
	@Override
	protected void cachePeeledState(Ref oldLeaf, Ref newLeaf) {
		// Do not cache peeled state in reftable.
	}

}
