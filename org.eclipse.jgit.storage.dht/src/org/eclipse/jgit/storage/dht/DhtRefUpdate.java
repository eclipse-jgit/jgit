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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.RefTransaction;

class DhtRefUpdate extends RefUpdate {
	private final DhtRefDatabase refdb;

	private final RepositoryKey repo;

	private final Database db;

	private RefKey refKey;

	private RefTransaction txn;

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
		Ref dst = getRef();
		if (deref)
			dst = dst.getLeaf();
		refKey = RefKey.create(repo, dst.getName());
		try {
			txn = db.ref().newTransaction(refKey);

			RefData old = txn.getOldData();
			if (old == null || old == RefData.NONE) {
				setOldObjectId(null);

			} else {
				TinyProtobuf.Decoder d = old.decode();
				DECODE: for (;;) {
					switch (d.next()) {
					case 0:
						break DECODE;

					case RefData.TAG_SYMREF:
						setOldObjectId(null);
						break DECODE;

					case RefData.TAG_TARGET:
						setOldObjectId(RefData.IdWithChunk.decode(d.message()));
						break DECODE;

					default:
						d.skip();
						continue;
					}
				}
			}

			return true;
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}
	}

	@Override
	protected void unlock() {
		if (txn != null) {
			txn.abort();
			txn = null;
		}
	}

	@Override
	protected Result doUpdate(Result desiredResult) throws IOException {
		try {
			boolean r = txn.compareAndPut(newData());
			txn = null;
			postUpdate(r);
			return r ? desiredResult : Result.LOCK_FAILURE;
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	private RefData newData() throws IOException {
		ObjectId nId = getNewObjectId();
		try {
			RevObject obj = rw.parseAny(nId);
			DhtReader ctx = (DhtReader) rw.getObjectReader();

			ChunkKey key = ctx.findChunk(nId);
			if (key != null)
				nId = new RefData.IdWithChunk(nId, key);

			if (obj instanceof RevTag) {
				ObjectId pId = rw.peel(obj);
				key = ctx.findChunk(pId);
				pId = key != null ? new RefData.IdWithChunk(pId, key) : pId;
				return RefData.peeled(nId, pId);
			} else if (obj != null)
				return RefData.peeled(nId, null);
			else
				return RefData.id(nId);
		} catch (MissingObjectException e) {
			return RefData.id(nId);
		}
	}

	@Override
	protected Result doDelete(Result desiredResult) throws IOException {
		try {
			boolean r = txn.compareAndRemove();
			txn = null;
			postUpdate(r);
			return r ? desiredResult : Result.LOCK_FAILURE;
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	@Override
	protected Result doLink(String target) throws IOException {
		try {
			boolean r = txn.compareAndPut(RefData.symbolic(target));
			txn = null;
			postUpdate(r);
			if (r) {
				if (getRef().getStorage() == Ref.Storage.NEW)
					return Result.NEW;
				return Result.FORCED;
			}
			return Result.LOCK_FAILURE;
		} catch (TimeoutException e) {
			return Result.IO_FAILURE;
		}
	}

	private void postUpdate(boolean success) {
		// TODO(spearce) Avoid clearing the entire cache.
		if (success)
			getRefDatabase().clearCache();
	}
}
