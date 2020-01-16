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

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

final class DfsRefUpdate extends RefUpdate {
	private final DfsRefDatabase refdb;

	private Ref dstRef;

	private RevWalk rw;

	DfsRefUpdate(DfsRefDatabase refdb, Ref ref) {
		super(ref);
		this.refdb = refdb;
	}

	/** {@inheritDoc} */
	@Override
	protected DfsRefDatabase getRefDatabase() {
		return refdb;
	}

	/** {@inheritDoc} */
	@Override
	protected DfsRepository getRepository() {
		return refdb.getRepository();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean tryLock(boolean deref) throws IOException {
		dstRef = getRef();
		if (deref)
			dstRef = dstRef.getLeaf();

		if (dstRef.isSymbolic())
			setOldObjectId(null);
		else
			setOldObjectId(dstRef.getObjectId());

		return true;
	}

	/** {@inheritDoc} */
	@Override
	protected void unlock() {
		// No state is held while "locked".
	}

	/** {@inheritDoc} */
	@Override
	public Result update(RevWalk walk) throws IOException {
		try {
			rw = walk;
			return super.update(walk);
		} finally {
			rw = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	protected Result doUpdate(Result desiredResult) throws IOException {
		ObjectIdRef newRef;
		RevObject obj = rw.parseAny(getNewObjectId());
		if (obj instanceof RevTag) {
			newRef = new ObjectIdRef.PeeledTag(
					Storage.PACKED,
					dstRef.getName(),
					getNewObjectId(),
					rw.peel(obj).copy());
		} else {
			newRef = new ObjectIdRef.PeeledNonTag(
					Storage.PACKED,
					dstRef.getName(),
					getNewObjectId());
		}

		if (getRefDatabase().compareAndPut(dstRef, newRef)) {
			getRefDatabase().stored(newRef);
			return desiredResult;
		}
		return Result.LOCK_FAILURE;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doDelete(Result desiredResult) throws IOException {
		if (getRefDatabase().compareAndRemove(dstRef)) {
			getRefDatabase().removed(dstRef.getName());
			return desiredResult;
		}
		return Result.LOCK_FAILURE;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doLink(String target) throws IOException {
		final SymbolicRef newRef = new SymbolicRef(
				dstRef.getName(),
				new ObjectIdRef.Unpeeled(
						Storage.NEW,
						target,
						null));
		if (getRefDatabase().compareAndPut(dstRef, newRef)) {
			getRefDatabase().stored(newRef);
			if (dstRef.getStorage() == Ref.Storage.NEW)
				return Result.NEW;
			return Result.FORCED;
		}
		return Result.LOCK_FAILURE;
	}
}
