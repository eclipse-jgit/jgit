/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	RefDirectoryUpdate(RefDirectory r, Ref ref) {
		super(ref);
		database = r;
	}

	/** {@inheritDoc} */
	@Override
	protected RefDirectory getRefDatabase() {
		return database;
	}

	/** {@inheritDoc} */
	@Override
	protected Repository getRepository() {
		return database.getRepository();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean tryLock(boolean deref) throws IOException {
		shouldDeref = deref;
		Ref dst = getRef();
		if (deref)
			dst = dst.getLeaf();
		String name = dst.getName();
		lock = new LockFile(database.fileFor(name));
		if (lock.lock()) {
			dst = database.findRef(name);
			setOldObjectId(dst != null ? dst.getObjectId() : null);
			return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void unlock() {
		if (lock != null) {
			lock.unlock();
			lock = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	protected Result doUpdate(Result status) throws IOException {
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
			database.log(isForceRefLog(), this, msg, shouldDeref);
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

	/** {@inheritDoc} */
	@Override
	protected Result doDelete(Result status) throws IOException {
		if (getRef().getStorage() != Ref.Storage.NEW)
			database.delete(this);
		return status;
	}

	/** {@inheritDoc} */
	@Override
	protected Result doLink(String target) throws IOException {
		WriteConfig wc = database.getRepository().getConfig()
				.get(WriteConfig.KEY);

		lock.setFSync(wc.getFSyncRefFiles());
		lock.setNeedStatInformation(true);
		lock.write(encode(RefDirectory.SYMREF + target + '\n'));

		String msg = getRefLogMessage();
		if (msg != null)
			database.log(isForceRefLog(), this, msg, false);
		if (!lock.commit())
			return Result.LOCK_FAILURE;
		database.storedSymbolicRef(this, lock.getCommitSnapshot(), target);

		if (getRef().getStorage() == Ref.Storage.NEW)
			return Result.NEW;
		return Result.FORCED;
	}
}
