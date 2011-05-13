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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.RefData;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.DhtRefDatabase.DhtRef;
import org.eclipse.jgit.storage.dht.spi.Database;

class DhtRefUpdate extends RefUpdate {
	private final DhtRefDatabase refdb;

	private final RepositoryKey repo;

	private final Database db;

	private RefKey refKey;

	private RefData oldData;

	private RefData newData;

	private Ref dstRef;

	private RevWalk rw;

	DhtRefUpdate(DhtRefDatabase refdb, RepositoryKey repo, Database db, Ref ref) {
		super(ref);
		this.refdb = refdb;
		this.repo = repo;
		this.db = db;
	}

	@Override
	protected DhtRefDatabase getRefDatabase() {
		return refdb;
	}

	@Override
	protected DhtRepository getRepository() {
		return refdb.getRepository();
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
	protected boolean tryLock(boolean deref) throws IOException {
		dstRef = getRef();
		if (deref)
			dstRef = dstRef.getLeaf();

		refKey = RefKey.create(repo, dstRef.getName());
		oldData = ((DhtRef) dstRef).getRefData();

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
	protected Result doUpdate(Result desiredResult) throws IOException {
		try {
			newData = newData();
			boolean r = db.ref().compareAndPut(refKey, oldData, newData);
			if (r) {
				getRefDatabase().stored(dstRef.getName(), newData);
				return desiredResult;
			} else {
				getRefDatabase().clearCache();
				return Result.LOCK_FAILURE;
			}
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	@Override
	protected Result doDelete(Result desiredResult) throws IOException {
		try {
			boolean r = db.ref().compareAndRemove(refKey, oldData);
			if (r) {
				getRefDatabase().removed(dstRef.getName());
				return desiredResult;
			} else {
				getRefDatabase().clearCache();
				return Result.LOCK_FAILURE;
			}
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	@Override
	protected Result doLink(String target) throws IOException {
		try {
			RefData.Builder d = RefData.newBuilder(oldData);
			clearRefData(d);
			updateSequence(d);
			d.setSymref(target);
			newData = d.build();
			boolean r = db.ref().compareAndPut(refKey, oldData, newData);
			if (r) {
				getRefDatabase().stored(dstRef.getName(), newData);
				if (getRef().getStorage() == Ref.Storage.NEW)
					return Result.NEW;
				return Result.FORCED;
			} else {
				getRefDatabase().clearCache();
				return Result.LOCK_FAILURE;
			}
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	private RefData newData() throws IOException {
		RefData.Builder d = RefData.newBuilder(oldData);
		clearRefData(d);
		updateSequence(d);

		ObjectId newId = getNewObjectId();
		d.getTargetBuilder().setObjectName(newId.name());
		try {
			DhtReader ctx = (DhtReader) rw.getObjectReader();
			RevObject obj = rw.parseAny(newId);

			ChunkKey oKey = ctx.findChunk(newId);
			if (oKey != null)
				d.getTargetBuilder().setChunkKey(oKey.asString());

			if (obj instanceof RevTag) {
				ObjectId pId = rw.peel(obj);
				ChunkKey pKey = ctx.findChunk(pId);
				if (pKey != null)
					d.getPeeledBuilder().setChunkKey(pKey.asString());
				d.getPeeledBuilder().setObjectName(pId.name());
			}
		} catch (MissingObjectException e) {
			// Automatic peeling failed. Ignore the problem and deal with it
			// during reading later, this is the classical Git behavior on disk.
		}
		return d.build();
	}

	private static void clearRefData(RefData.Builder d) {
		// Clear fields individually rather than discarding the RefData.
		// This way implementation specific extensions are carried
		// through from the old version to the new version.
		d.clearSymref();
		d.clearTarget();
		d.clearPeeled();
		d.clearIsPeeled();
	}

	private static void updateSequence(RefData.Builder d) {
		d.setSequence(d.getSequence() + 1);
	}
}
