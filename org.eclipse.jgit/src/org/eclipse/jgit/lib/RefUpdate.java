/*
 * Copyright (C) 2008-2010, Google Inc.
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

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Creates, updates or deletes any reference.
 */
public abstract class RefUpdate {
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
	private Result result = Result.NOT_ATTEMPTED;

	private final Ref ref;

	/**
	 * Is this RefUpdate detaching a symbolic ref?
	 *
	 * We need this info since this.ref will normally be peeled of in case of
	 * detaching a symbolic ref (HEAD for example).
	 *
	 * Without this flag we cannot decide whether the ref has to be updated or
	 * not in case when it was a symbolic ref and the newValue == oldValue.
	 */
	private boolean detachingSymbolicRef;

	private boolean checkConflicting = true;

	/**
	 * Construct a new update operation for the reference.
	 * <p>
	 * {@code ref.getObjectId()} will be used to seed {@link #getOldObjectId()},
	 * which callers can use as part of their own update logic.
	 *
	 * @param ref
	 *            the reference that will be updated by this operation.
	 */
	protected RefUpdate(final Ref ref) {
		this.ref = ref;
		oldValue = ref.getObjectId();
		refLogMessage = ""; //$NON-NLS-1$
	}

	/** @return the reference database this update modifies. */
	protected abstract RefDatabase getRefDatabase();

	/** @return the repository storing the database's objects. */
	protected abstract Repository getRepository();

	/**
	 * Try to acquire the lock on the reference.
	 * <p>
	 * If the locking was successful the implementor must set the current
	 * identity value by calling {@link #setOldObjectId(ObjectId)}.
	 *
	 * @param deref
	 *            true if the lock should be taken against the leaf level
	 *            reference; false if it should be taken exactly against the
	 *            current reference.
	 * @return true if the lock was acquired and the reference is likely
	 *         protected from concurrent modification; false if it failed.
	 * @throws IOException
	 *             the lock couldn't be taken due to an unexpected storage
	 *             failure, and not because of a concurrent update.
	 */
	protected abstract boolean tryLock(boolean deref) throws IOException;

	/** Releases the lock taken by {@link #tryLock} if it succeeded. */
	protected abstract void unlock();

	/**
	 * @param desiredResult
	 * @return {@code result}
	 * @throws IOException
	 */
	protected abstract Result doUpdate(Result desiredResult) throws IOException;

	/**
	 * @param desiredResult
	 * @return {@code result}
	 * @throws IOException
	 */
	protected abstract Result doDelete(Result desiredResult) throws IOException;

	/**
	 * @param target
	 * @return {@link Result#NEW} on success.
	 * @throws IOException
	 */
	protected abstract Result doLink(String target) throws IOException;

	/**
	 * Get the name of the ref this update will operate on.
	 *
	 * @return name of underlying ref.
	 */
	public String getName() {
		return getRef().getName();
	}

	/** @return the reference this update will create or modify. */
	public Ref getRef() {
		return ref;
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
	 * Tells this RefUpdate that it is actually detaching a symbolic ref.
	 */
	public void setDetachingSymbolicRef() {
		detachingSymbolicRef = true;
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

	/** @return {@code true} if the ref log message should show the result. */
	protected boolean isRefLogIncludingResult() {
		return refLogIncludeResult;
	}

	/**
	 * Set the message to include in the reflog.
	 *
	 * @param msg
	 *            the message to describe this change. It may be null if
	 *            appendStatus is null in order not to append to the reflog
	 * @param appendStatus
	 *            true if the status of the ref change (fast-forward or
	 *            forced-update) should be appended to the user supplied
	 *            message.
	 */
	public void setRefLogMessage(final String msg, final boolean appendStatus) {
		if (msg == null && !appendStatus)
			disableRefLog();
		else if (msg == null && appendStatus) {
			refLogMessage = ""; //$NON-NLS-1$
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
	 * Set the old value of the ref.
	 *
	 * @param old
	 *            the old value.
	 */
	protected void setOldObjectId(ObjectId old) {
		oldValue = old;
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
			throw new IllegalStateException(JGitText.get().aNewObjectIdIsRequired);
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
	 * return update(new RevWalk(getRepository()));
	 * </pre>
	 *
	 * @return the result status of the update.
	 * @throws IOException
	 *             an unexpected IO error occurred while writing changes.
	 */
	public Result update() throws IOException {
		RevWalk rw = new RevWalk(getRepository());
		try {
			return update(rw);
		} finally {
			rw.release();
		}
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
			return result = updateImpl(walk, new Store() {
				@Override
				Result execute(Result status) throws IOException {
					if (status == Result.NO_CHANGE)
						return status;
					return doUpdate(status);
				}
			});
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
	 * return delete(new RevWalk(getRepository()));
	 * </pre>
	 *
	 * @return the result status of the delete.
	 * @throws IOException
	 */
	public Result delete() throws IOException {
		RevWalk rw = new RevWalk(getRepository());
		try {
			return delete(rw);
		} finally {
			rw.release();
		}
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
		final String myName = getRef().getLeaf().getName();
		if (myName.startsWith(Constants.R_HEADS)) {
			Ref head = getRefDatabase().getRef(Constants.HEAD);
			while (head.isSymbolic()) {
				head = head.getTarget();
				if (myName.equals(head.getName()))
					return result = Result.REJECTED_CURRENT_BRANCH;
			}
		}

		try {
			return result = updateImpl(walk, new Store() {
				@Override
				Result execute(Result status) throws IOException {
					return doDelete(status);
				}
			});
		} catch (IOException x) {
			result = Result.IO_FAILURE;
			throw x;
		}
	}

	/**
	 * Replace this reference with a symbolic reference to another reference.
	 * <p>
	 * This exact reference (not its traversed leaf) is replaced with a symbolic
	 * reference to the requested name.
	 *
	 * @param target
	 *            name of the new target for this reference. The new target name
	 *            must be absolute, so it must begin with {@code refs/}.
	 * @return {@link Result#NEW} or {@link Result#FORCED} on success.
	 * @throws IOException
	 */
	public Result link(String target) throws IOException {
		if (!target.startsWith(Constants.R_REFS))
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().illegalArgumentNotA, Constants.R_REFS));
		if (checkConflicting && getRefDatabase().isNameConflicting(getName()))
			return Result.LOCK_FAILURE;
		try {
			if (!tryLock(false))
				return Result.LOCK_FAILURE;

			final Ref old = getRefDatabase().getRef(getName());
			if (old != null && old.isSymbolic()) {
				final Ref dst = old.getTarget();
				if (target.equals(dst.getName()))
					return result = Result.NO_CHANGE;
			}

			if (old != null && old.getObjectId() != null)
				setOldObjectId(old.getObjectId());

			final Ref dst = getRefDatabase().getRef(target);
			if (dst != null && dst.getObjectId() != null)
				setNewObjectId(dst.getObjectId());

			return result = doLink(target);
		} catch (IOException x) {
			result = Result.IO_FAILURE;
			throw x;
		} finally {
			unlock();
		}
	}

	private Result updateImpl(final RevWalk walk, final Store store)
			throws IOException {
		RevObject newObj;
		RevObject oldObj;

		if (checkConflicting && getRefDatabase().isNameConflicting(getName()))
			return Result.LOCK_FAILURE;
		try {
			if (!tryLock(true))
				return Result.LOCK_FAILURE;
			if (expValue != null) {
				final ObjectId o;
				o = oldValue != null ? oldValue : ObjectId.zeroId();
				if (!AnyObjectId.equals(expValue, o))
					return Result.LOCK_FAILURE;
			}
			if (oldValue == null)
				return store.execute(Result.NEW);

			newObj = safeParse(walk, newValue);
			oldObj = safeParse(walk, oldValue);
			if (newObj == oldObj && !detachingSymbolicRef)
				return store.execute(Result.NO_CHANGE);

			if (newObj instanceof RevCommit && oldObj instanceof RevCommit) {
				if (walk.isMergedInto((RevCommit) oldObj, (RevCommit) newObj))
					return store.execute(Result.FAST_FORWARD);
			}

			if (isForceUpdate())
				return store.execute(Result.FORCED);
			return Result.REJECTED;
		} finally {
			unlock();
		}
	}

	/**
	 * Enable/disable the check for conflicting ref names. By default conflicts
	 * are checked explicitly.
	 *
	 * @param check
	 */
	public void setCheckConflicting(boolean check) {
		checkConflicting = check;
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

	/**
	 * Handle the abstraction of storing a ref update. This is because both
	 * updating and deleting of a ref have merge testing in common.
	 */
	private abstract class Store {
		abstract Result execute(Result status) throws IOException;
	}
}
