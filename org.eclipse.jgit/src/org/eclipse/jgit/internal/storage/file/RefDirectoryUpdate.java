/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.encode;

import java.io.IOException;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;

/** Updates any reference stored by {@link RefDirectory}. */
class RefDirectoryUpdate extends RefUpdate {
	private final RefDirectory database;

	private boolean shouldDeref;
	private LockFile lock;

	RefDirectoryUpdate(final RefDirectory r, final Ref ref) {
		super(ref);
		database = r;
	}

	@Override
	protected RefDirectory getRefDatabase() {
		return database;
	}

	@Override
	protected Repository getRepository() {
		return database.getRepository();
	}

	@Override
	protected boolean tryLock(boolean deref) throws IOException {
		shouldDeref = deref;
		Ref dst = getRef();
		if (deref)
			dst = dst.getLeaf();
		String name = dst.getName();
		lock = new LockFile(database.fileFor(name));
		if (lock.lock()) {
			dst = database.getRef(name);
			setOldObjectId(dst != null ? dst.getObjectId() : null);
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void unlock() {
		if (lock != null) {
			lock.unlock();
			lock = null;
		}
	}

	@Override
	protected Result doUpdate(final Result status) throws IOException {
		WriteConfig wc = database.getRepository().getConfig()
				.get(WriteConfig.KEY);

		lock.setFSync(wc.getFSyncRefFiles());
		lock.setNeedStatInformation(true);
		lock.write(getNewObjectId());

		String msg = getRefLogMessage();
		if (msg != null) {
			if (isRefLogIncludingResult()) {
				String strResult = toResultString(status);
				if (strResult != null) {
					if (msg.length() > 0)
						msg = msg + ": " + strResult; //$NON-NLS-1$
					else
						msg = strResult;
				}
			}
			database.log(this, msg, shouldDeref);
		}
		if (!lock.commit())
			return Result.LOCK_FAILURE;
		database.stored(this, lock.getCommitSnapshot());
		return status;
	}

	private String toResultString(Result status) {
		switch (status) {
		case FORCED:
			return ReflogEntry.PREFIX_FORCED_UPDATE;
		case FAST_FORWARD:
			return ReflogEntry.PREFIX_FAST_FORWARD;
		case NEW:
			return ReflogEntry.PREFIX_CREATED;
		default:
			return null;
		}
	}

	@Override
	protected Result doDelete(final Result status) throws IOException {
		if (getRef().getStorage() != Ref.Storage.NEW)
			database.delete(this);
		return status;
	}

	@Override
	protected Result doLink(final String target) throws IOException {
		WriteConfig wc = database.getRepository().getConfig()
				.get(WriteConfig.KEY);

		lock.setFSync(wc.getFSyncRefFiles());
		lock.setNeedStatInformation(true);
		lock.write(encode(RefDirectory.SYMREF + target + '\n'));

		String msg = getRefLogMessage();
		if (msg != null)
			database.log(this, msg, false);
		if (!lock.commit())
			return Result.LOCK_FAILURE;
		database.storedSymbolicRef(this, lock.getCommitSnapshot(), target);

		if (getRef().getStorage() == Ref.Storage.NEW)
			return Result.NEW;
		return Result.FORCED;
	}
}
