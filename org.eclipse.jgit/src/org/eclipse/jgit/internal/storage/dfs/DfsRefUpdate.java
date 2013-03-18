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

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.lib.Ref.Storage;
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

	@Override
	protected DfsRefDatabase getRefDatabase() {
		return refdb;
	}

	@Override
	protected DfsRepository getRepository() {
		return refdb.getRepository();
	}

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

	@Override
	protected void unlock() {
		// No state is held while "locked".
	}

	@Override
	public Result update(RevWalk walk) throws IOException {
		try {
			rw = walk;
			return super.update(walk);
		} finally {
			rw = null;
		}
	}

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

	@Override
	protected Result doDelete(Result desiredResult) throws IOException {
		if (getRefDatabase().compareAndRemove(dstRef)) {
			getRefDatabase().removed(dstRef.getName());
			return desiredResult;
		}
		return Result.LOCK_FAILURE;
	}

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
