/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Updates any locally stored ref.
 */
public class RefUpdate {
	/** Status of an update request. */
	public static enum Result {
		/** The ref update/delete has not been attempted by the caller. */
		NOT_ATTEMPTED,

		/**
		 * The ref could not be locked for update/delete.
		 * <p>
		 * This is generally a transient failure and is usually caused by
		 * another process trying to access the ref at the same time as this
		 * process was trying to update it. It is possible a future operation
		 * will be successful.
		 */
		LOCK_FAILURE,

		/**
		 * Same value already stored.
		 * <p>
		 * Both the old value and the new value are identical. No change was
		 * necessary for an update. For delete the branch is removed.
		 */
		NO_CHANGE,

		/**
		 * The ref was created locally for an update, but ignored for delete.
		 * <p>
		 * The ref did not exist when the update started, but it was created
		 * successfully with the new value.
		 */
		NEW,

		/**
		 * The ref had to be forcefully updated/deleted.
		 * <p>
		 * The ref already existed but its old value was not fully merged into
		 * the new value. The configuration permitted a forced update to take
		 * place, so ref now contains the new value. History associated with the
		 * objects not merged may no longer be reachable.
		 */
		FORCED,

		/**
		 * The ref was updated/deleted in a fast-forward way.
		 * <p>
		 * The tracking ref already existed and its old value was fully merged
		 * into the new value. No history was made unreachable.
		 */
		FAST_FORWARD,

		/**
		 * Not a fast-forward and not stored.
		 * <p>
		 * The tracking ref already existed but its old value was not fully
		 * merged into the new value. The configuration did not allow a forced
		 * update/delete to take place, so ref still contains the old value. No
		 * previous history was lost.
		 */
		REJECTED,

		/**
		 * Rejected because trying to delete the current branch.
		 * <p>
		 * Has no meaning for update.
		 */
		REJECTED_CURRENT_BRANCH,

		/**
		 * The ref was probably not updated/deleted because of I/O error.
		 * <p>
		 * Unexpected I/O error occurred when writing new ref. Such error may
		 * result in uncertain state, but most probably ref was not updated.
		 * <p>
		 * This kind of error doesn't include {@link #LOCK_FAILURE}, which is a
		 * different case.
		 */
		IO_FAILURE,

		/**
		 * The ref was renamed from another name
		 * <p>
		 */
		RENAMED
	}

	/** Repository the ref is stored in. */
	final RefDatabase db;

	/** Location of the loose file holding the value of this ref. */
	final File looseFile;

	/** New value the caller wants this ref to have. */
	private ObjectId newValue;

	/** Does this specification ask for forced updated (rewind/reset)? */
	private boolean force;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Message the caller wants included in the reflog. */
	private String refLogMessage;

	/** Should the Result value be appended to {@link #refLogMessage}. */
	private boolean refLogIncludeResult;

	/** Old value of the ref, obtained after we lock it. */
	private ObjectId oldValue;

	/** If non-null, the value {@link #oldValue} must have to continue. */
	private ObjectId expValue;

	/** Result of the update operation. */
	Result result = Result.NOT_ATTEMPTED;

	private final Ref ref;

	RefUpdate(final RefDatabase r, final Ref ref, final File f) {
		db = r;
		this.ref = ref;
		oldValue = ref.getObjectId();
		looseFile = f;
		refLogMessage = "";
	}

	/** @return the repository the updated ref resides in */
	public Repository getRepository() {
		return db.getRepository();
	}

	/**
	 * Get the name of the ref this update will operate on.
	 * 
	 * @return name of underlying ref.
	 */
	public String getName() {
		return ref.getName();
	}

	/**
	 * Get the requested name of the ref thit update will operate on
	 *
	 * @return original (requested) name of the underlying ref.
	 */
	public String getOrigName() {
		return ref.getOrigName();
	}

	/**
	 * Get the new value the ref will be (or was) updated to.
	 * 
	 * @return new value. Null if the caller has not configured it.
	 */
	public ObjectId getNewObjectId() {
		return newValue;
	}

	/**
	 * Set the new value the ref will update to.
	 * 
	 * @param id
	 *            the new value.
	 */
	public void setNewObjectId(final AnyObjectId id) {
		newValue = id.copy();
	}

	/**
	 * @return the expected value of the ref after the lock is taken, but before
	 *         update occurs. Null to avoid the compare and swap test. Use
	 *         {@link ObjectId#zeroId()} to indicate expectation of a
	 *         non-existant ref.
	 */
	public ObjectId getExpectedOldObjectId() {
		return expValue;
	}

	/**
	 * @param id
	 *            the expected value of the ref after the lock is taken, but
	 *            before update occurs. Null to avoid the compare and swap test.
	 *            Use {@link ObjectId#zeroId()} to indicate expectation of a
	 *            non-existant ref.
	 */
	public void setExpectedOldObjectId(final AnyObjectId id) {
		expValue = id != null ? id.toObjectId() : null;
	}

	/**
	 * Check if this update wants to forcefully change the ref.
	 * 
	 * @return true if this update should ignore merge tests.
	 */
	public boolean isForceUpdate() {
		return force;
	}

	/**
	 * Set if this update wants to forcefully change the ref.
	 * 
	 * @param b
	 *            true if this update should ignore merge tests.
	 */
	public void setForceUpdate(final boolean b) {
		force = b;
	}

	/** @return identity of the user making the change in the reflog. */
	public PersonIdent getRefLogIdent() {
		return refLogIdent;
	}

	/**
	 * Set the identity of the user appearing in the reflog.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the update occurs
	 * and the log record is written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 */
	public void setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
	}

	/**
	 * Get the message to include in the reflog.
	 * 
	 * @return message the caller wants to include in the reflog; null if the
	 *         update should not be logged.
	 */
	public String getRefLogMessage() {
		return refLogMessage;
	}

	/**
	 * Set the message to include in the reflog.
	 * 
	 * @param msg
	 *            the message to describe this change. It may be null
	 *            if appendStatus is null in order not to append to the reflog
	 * @param appendStatus
	 *            true if the status of the ref change (fast-forward or
	 *            forced-update) should be appended to the user supplied
	 *            message.
	 */
	public void setRefLogMessage(final String msg, final boolean appendStatus) {
		if (msg == null && !appendStatus)
			disableRefLog();
		else if (msg == null && appendStatus) {
			refLogMessage = "";
			refLogIncludeResult = true;
		} else {
			refLogMessage = msg;
			refLogIncludeResult = appendStatus;
		}
	}

	/** Don't record this update in the ref's associated reflog. */
	public void disableRefLog() {
		refLogMessage = null;
		refLogIncludeResult = false;
	}

	/**
	 * The old value of the ref, prior to the update being attempted.
	 * <p>
	 * This value may differ before and after the update method. Initially it is
	 * populated with the value of the ref before the lock is taken, but the old
	 * value may change if someone else modified the ref between the time we
	 * last read it and when the ref was locked for update.
	 * 
	 * @return the value of the ref prior to the update being attempted; null if
	 *         the updated has not been attempted yet.
	 */
	public ObjectId getOldObjectId() {
		return oldValue;
	}

	/**
	 * Get the status of this update.
	 * <p>
	 * The same value that was previously returned from an update method.
	 * 
	 * @return the status of the update.
	 */
	public Result getResult() {
		return result;
	}

	private void requireCanDoUpdate() {
		if (newValue == null)
			throw new IllegalStateException("A NewObjectId is required.");
	}

	/**
	 * Force the ref to take the new value.
	 * <p>
	 * This is just a convenient helper for setting the force flag, and as such
	 * the merge test is performed.
	 * 
	 * @return the result status of the update.
	 * @throws IOException
	 *             an unexpected IO error occurred while writing changes.
	 */
	public Result forceUpdate() throws IOException {
		force = true;
		return update();
	}

	/**
	 * Gracefully update the ref to the new value.
	 * <p>
	 * Merge test will be performed according to {@link #isForceUpdate()}.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * return update(new RevWalk(repository));
	 * </pre>
	 * 
	 * @return the result status of the update.
	 * @throws IOException
	 *             an unexpected IO error occurred while writing changes.
	 */
	public Result update() throws IOException {
		return update(new RevWalk(db.getRepository()));
	}

	/**
	 * Gracefully update the ref to the new value.
	 * <p>
	 * Merge test will be performed according to {@link #isForceUpdate()}.
	 * 
	 * @param walk
	 *            a RevWalk instance this update command can borrow to perform
	 *            the merge test. The walk will be reset to perform the test.
	 * @return the result status of the update.
	 * @throws IOException
	 *             an unexpected IO error occurred while writing changes.
	 */
	public Result update(final RevWalk walk) throws IOException {
		requireCanDoUpdate();
		try {
			return result = updateImpl(walk, new UpdateStore());
		} catch (IOException x) {
			result = Result.IO_FAILURE;
			throw x;
		}
	}

	/**
	 * Delete the ref.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * return delete(new RevWalk(repository));
	 * </pre>
	 * 
	 * @return the result status of the delete.
	 * @throws IOException
	 */
	public Result delete() throws IOException {
		return delete(new RevWalk(db.getRepository()));
	}

	/**
	 * Delete the ref.
	 * 
	 * @param walk
	 *            a RevWalk instance this delete command can borrow to perform
	 *            the merge test. The walk will be reset to perform the test.
	 * @return the result status of the delete.
	 * @throws IOException
	 */
	public Result delete(final RevWalk walk) throws IOException {
		if (getName().startsWith(Constants.R_HEADS)) {
			final Ref head = db.readRef(Constants.HEAD);
			if (head != null && getName().equals(head.getName()))
				return result = Result.REJECTED_CURRENT_BRANCH;
		}

		try {
			return result = updateImpl(walk, new DeleteStore());
		} catch (IOException x) {
			result = Result.IO_FAILURE;
			throw x;
		}
	}

	private Result updateImpl(final RevWalk walk, final Store store)
			throws IOException {
		final LockFile lock;
		RevObject newObj;
		RevObject oldObj;

		if (isNameConflicting())
			return Result.LOCK_FAILURE;
		lock = new LockFile(looseFile);
		if (!lock.lock())
			return Result.LOCK_FAILURE;
		try {
			oldValue = db.idOf(getName());
			if (expValue != null) {
				final ObjectId o;
				o = oldValue != null ? oldValue : ObjectId.zeroId();
				if (!AnyObjectId.equals(expValue, o))
					return Result.LOCK_FAILURE;
			}
			if (oldValue == null)
				return store.store(lock, Result.NEW);

			newObj = safeParse(walk, newValue);
			oldObj = safeParse(walk, oldValue);
			if (newObj == oldObj)
				return store.store(lock, Result.NO_CHANGE);

			if (newObj instanceof RevCommit && oldObj instanceof RevCommit) {
				if (walk.isMergedInto((RevCommit) oldObj, (RevCommit) newObj))
					return store.store(lock, Result.FAST_FORWARD);
			}

			if (isForceUpdate())
				return store.store(lock, Result.FORCED);
			return Result.REJECTED;
		} finally {
			lock.unlock();
		}
	}

	private boolean isNameConflicting() throws IOException {
		final String myName = getName();
		final int lastSlash = myName.lastIndexOf('/');
		if (lastSlash > 0)
			if (db.getRepository().getRef(myName.substring(0, lastSlash)) != null)
				return true;

		final String rName = myName + "/";
		for (Ref r : db.getAllRefs().values()) {
			if (r.getName().startsWith(rName))
				return true;
		}
		return false;
	}

	private static RevObject safeParse(final RevWalk rw, final AnyObjectId id)
			throws IOException {
		try {
			return id != null ? rw.parseAny(id) : null;
		} catch (MissingObjectException e) {
			// We can expect some objects to be missing, like if we are
			// trying to force a deletion of a branch and the object it
			// points to has been pruned from the database due to freak
			// corruption accidents (it happens with 'git new-work-dir').
			//
			return null;
		}
	}

	private Result updateStore(final LockFile lock, final Result status)
			throws IOException {
		if (status == Result.NO_CHANGE)
			return status;
		lock.setNeedStatInformation(true);
		lock.write(newValue);
		String msg = getRefLogMessage();
		if (msg != null) {
			if (refLogIncludeResult) {
				String strResult = toResultString(status);
				if (strResult != null) {
					if (msg.length() > 0)
						msg = msg + ": " + strResult;
					else
						msg = strResult;
				}
			}
			RefLogWriter.append(this, msg);
		}
		if (!lock.commit())
			return Result.LOCK_FAILURE;
		db.stored(this.ref.getOrigName(),  ref.getName(), newValue, lock.getCommitLastModified());
		return status;
	}

	private static String toResultString(final Result status) {
		switch (status) {
		case FORCED:
			return "forced-update";
		case FAST_FORWARD:
			return "fast forward";
		case NEW:
			return "created";
		default:
			return null;
		}
	}

	/**
	 * Handle the abstraction of storing a ref update. This is because both
	 * updating and deleting of a ref have merge testing in common.
	 */
	private abstract class Store {
		abstract Result store(final LockFile lock, final Result status)
				throws IOException;
	}

	class UpdateStore extends Store {

		@Override
		Result store(final LockFile lock, final Result status)
				throws IOException {
			return updateStore(lock, status);
		}
	}

	class DeleteStore extends Store {

		@Override
		Result store(LockFile lock, Result status) throws IOException {
			Storage storage = ref.getStorage();
			if (storage == Storage.NEW)
				return status;
			if (storage.isPacked())
				db.removePackedRef(ref.getName());

			final int levels = count(ref.getName(), '/') - 2;

			// Delete logs _before_ unlocking
			final File gitDir = db.getRepository().getDirectory();
			final File logDir = new File(gitDir, Constants.LOGS);
			deleteFileAndEmptyDir(new File(logDir, ref.getName()), levels);

			// We have to unlock before (maybe) deleting the parent directories
			lock.unlock();
			if (storage.isLoose())
				deleteFileAndEmptyDir(looseFile, levels);
			db.uncacheRef(ref.getName());
			return status;
		}

		private void deleteFileAndEmptyDir(final File file, final int depth)
				throws IOException {
			if (file.isFile()) {
				if (!file.delete())
					throw new IOException("File cannot be deleted: " + file);
				File dir = file.getParentFile();
				for  (int i = 0; i < depth; ++i) {
					if (!dir.delete())
						break; // ignore problem here
					dir = dir.getParentFile();
				}
			}
		}
	}

	UpdateStore newUpdateStore() {
		return new UpdateStore();
	}

	DeleteStore newDeleteStore() {
		return new DeleteStore();
	}

	static void deleteEmptyDir(File dir, int depth) {
		for (; depth > 0 && dir != null; depth--) {
			if (dir.exists() && !dir.delete())
				break;
			dir = dir.getParentFile();
		}
	}

	static int count(final String s, final char c) {
		int count = 0;
		for (int p = s.indexOf(c); p >= 0; p = s.indexOf(c, p + 1)) {
			count++;
		}
		return count;
	}
}
