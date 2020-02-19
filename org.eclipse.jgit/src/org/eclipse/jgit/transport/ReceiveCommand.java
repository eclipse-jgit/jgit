/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A command being processed by
 * {@link org.eclipse.jgit.transport.ReceivePack}.
 * <p>
 * This command instance roughly translates to the server side representation of
 * the {@link org.eclipse.jgit.transport.RemoteRefUpdate} created by the client.
 */
public class ReceiveCommand {
	/** Type of operation requested. */
	public enum Type {
		/** Create a new ref; the ref must not already exist. */
		CREATE,

		/**
		 * Update an existing ref with a fast-forward update.
		 * <p>
		 * During a fast-forward update no changes will be lost; only new
		 * commits are inserted into the ref.
		 */
		UPDATE,

		/**
		 * Update an existing ref by potentially discarding objects.
		 * <p>
		 * The current value of the ref is not fully reachable from the new
		 * value of the ref, so a successful command may result in one or more
		 * objects becoming unreachable.
		 */
		UPDATE_NONFASTFORWARD,

		/** Delete an existing ref; the ref should already exist. */
		DELETE;
	}

	/** Result of the update command. */
	public enum Result {
		/** The command has not yet been attempted by the server. */
		NOT_ATTEMPTED,

		/** The server is configured to deny creation of this ref. */
		REJECTED_NOCREATE,

		/** The server is configured to deny deletion of this ref. */
		REJECTED_NODELETE,

		/** The update is a non-fast-forward update and isn't permitted. */
		REJECTED_NONFASTFORWARD,

		/** The update affects <code>HEAD</code> and cannot be permitted. */
		REJECTED_CURRENT_BRANCH,

		/**
		 * One or more objects aren't in the repository.
		 * <p>
		 * This is severe indication of either repository corruption on the
		 * server side, or a bug in the client wherein the client did not supply
		 * all required objects during the pack transfer.
		 */
		REJECTED_MISSING_OBJECT,

		/** Other failure; see {@link ReceiveCommand#getMessage()}. */
		REJECTED_OTHER_REASON,

		/** The ref could not be locked and updated atomically; try again. */
		LOCK_FAILURE,

		/** The change was completed successfully. */
		OK;
	}

	/**
	 * Filter a collection of commands according to result.
	 *
	 * @param in
	 *            commands to filter.
	 * @param want
	 *            desired status to filter by.
	 * @return a copy of the command list containing only those commands with
	 *         the desired status.
	 * @since 4.2
	 */
	public static List<ReceiveCommand> filter(Iterable<ReceiveCommand> in,
			Result want) {
		List<ReceiveCommand> r;
		if (in instanceof Collection)
			r = new ArrayList<>(((Collection<?>) in).size());
		else
			r = new ArrayList<>();
		for (ReceiveCommand cmd : in) {
			if (cmd.getResult() == want)
				r.add(cmd);
		}
		return r;
	}

	/**
	 * Filter a list of commands according to result.
	 *
	 * @param commands
	 *            commands to filter.
	 * @param want
	 *            desired status to filter by.
	 * @return a copy of the command list containing only those commands with
	 *         the desired status.
	 * @since 2.0
	 */
	public static List<ReceiveCommand> filter(List<ReceiveCommand> commands,
			Result want) {
		return filter((Iterable<ReceiveCommand>) commands, want);
	}

	/**
	 * Set unprocessed commands as failed due to transaction aborted.
	 * <p>
	 * If a command is still
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#NOT_ATTEMPTED} it
	 * will be set to
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#REJECTED_OTHER_REASON}.
	 *
	 * @param commands
	 *            commands to mark as failed.
	 * @since 4.2
	 */
	public static void abort(Iterable<ReceiveCommand> commands) {
		for (ReceiveCommand c : commands) {
			if (c.getResult() == NOT_ATTEMPTED) {
				c.setResult(REJECTED_OTHER_REASON,
						JGitText.get().transactionAborted);
			}
		}
	}

	/**
	 * Check whether a command failed due to transaction aborted.
	 *
	 * @param cmd
	 *            command.
	 * @return whether the command failed due to transaction aborted, as in
	 *         {@link #abort(Iterable)}.
	 * @since 4.9
	 */
	public static boolean isTransactionAborted(ReceiveCommand cmd) {
		return cmd.getResult() == REJECTED_OTHER_REASON
				&& cmd.getMessage().equals(JGitText.get().transactionAborted);
	}

	/**
	 * Create a command to switch a reference from object to symbolic.
	 *
	 * @param oldId
	 *            expected oldId. May be {@code zeroId} to create.
	 * @param newTarget
	 *            new target; must begin with {@code "refs/"}.
	 * @param name
	 *            name of the reference to make symbolic.
	 * @return command instance.
	 * @since 4.10
	 */
	public static ReceiveCommand link(@NonNull ObjectId oldId,
			@NonNull String newTarget, @NonNull String name) {
		return new ReceiveCommand(oldId, newTarget, name);
	}

	/**
	 * Create a command to switch a symbolic reference's target.
	 *
	 * @param oldTarget
	 *            expected old target. May be null to create.
	 * @param newTarget
	 *            new target; must begin with {@code "refs/"}.
	 * @param name
	 *            name of the reference to make symbolic.
	 * @return command instance.
	 * @since 4.10
	 */
	public static ReceiveCommand link(@Nullable String oldTarget,
			@NonNull String newTarget, @NonNull String name) {
		return new ReceiveCommand(oldTarget, newTarget, name);
	}

	/**
	 * Create a command to switch a reference from symbolic to object.
	 *
	 * @param oldTarget
	 *            expected old target.
	 * @param newId
	 *            new object identifier. May be {@code zeroId()} to delete.
	 * @param name
	 *            name of the reference to convert from symbolic.
	 * @return command instance.
	 * @since 4.10
	 */
	public static ReceiveCommand unlink(@NonNull String oldTarget,
			@NonNull ObjectId newId, @NonNull String name) {
		return new ReceiveCommand(oldTarget, newId, name);
	}

	private final ObjectId oldId;

	private final String oldSymref;

	private final ObjectId newId;

	private final String newSymref;

	private final String name;

	private Type type;

	private boolean typeIsCorrect;

	private Ref ref;

	private Result status = Result.NOT_ATTEMPTED;

	private String message;

	private boolean customRefLog;

	private String refLogMessage;

	private boolean refLogIncludeResult;

	private Boolean forceRefLog;

	/**
	 * Create a new command for
	 * {@link org.eclipse.jgit.transport.ReceivePack}.
	 *
	 * @param oldId
	 *            the expected old object id; must not be null. Use
	 *            {@link org.eclipse.jgit.lib.ObjectId#zeroId()} to indicate a
	 *            ref creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link org.eclipse.jgit.lib.ObjectId#zeroId()} to indicate a
	 *            ref deletion.
	 * @param name
	 *            name of the ref being affected.
	 */
	public ReceiveCommand(final ObjectId oldId, final ObjectId newId,
			final String name) {
		if (oldId == null) {
			throw new IllegalArgumentException(
					JGitText.get().oldIdMustNotBeNull);
		}
		if (newId == null) {
			throw new IllegalArgumentException(
					JGitText.get().newIdMustNotBeNull);
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().nameMustNotBeNullOrEmpty);
		}
		this.oldId = oldId;
		this.oldSymref = null;
		this.newId = newId;
		this.newSymref = null;
		this.name = name;

		type = Type.UPDATE;
		if (ObjectId.zeroId().equals(oldId)) {
			type = Type.CREATE;
		}
		if (ObjectId.zeroId().equals(newId)) {
			type = Type.DELETE;
		}
	}

	/**
	 * Create a new command for
	 * {@link org.eclipse.jgit.transport.ReceivePack}.
	 *
	 * @param oldId
	 *            the old object id; must not be null. Use
	 *            {@link org.eclipse.jgit.lib.ObjectId#zeroId()} to indicate a
	 *            ref creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link org.eclipse.jgit.lib.ObjectId#zeroId()} to indicate a
	 *            ref deletion.
	 * @param name
	 *            name of the ref being affected.
	 * @param type
	 *            type of the command. Must be
	 *            {@link org.eclipse.jgit.transport.ReceiveCommand.Type#CREATE}
	 *            if {@code
	 *            oldId} is zero, or
	 *            {@link org.eclipse.jgit.transport.ReceiveCommand.Type#DELETE}
	 *            if {@code newId} is zero.
	 * @since 2.0
	 */
	public ReceiveCommand(final ObjectId oldId, final ObjectId newId,
			final String name, final Type type) {
		if (oldId == null) {
			throw new IllegalArgumentException(
					JGitText.get().oldIdMustNotBeNull);
		}
		if (newId == null) {
			throw new IllegalArgumentException(
					JGitText.get().newIdMustNotBeNull);
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().nameMustNotBeNullOrEmpty);
		}
		this.oldId = oldId;
		this.oldSymref = null;
		this.newId = newId;
		this.newSymref = null;
		this.name = name;
		switch (type) {
		case CREATE:
			if (!ObjectId.zeroId().equals(oldId)) {
				throw new IllegalArgumentException(
						JGitText.get().createRequiresZeroOldId);
			}
			break;
		case DELETE:
			if (!ObjectId.zeroId().equals(newId)) {
				throw new IllegalArgumentException(
						JGitText.get().deleteRequiresZeroNewId);
			}
			break;
		case UPDATE:
		case UPDATE_NONFASTFORWARD:
			if (ObjectId.zeroId().equals(newId)
					|| ObjectId.zeroId().equals(oldId)) {
				throw new IllegalArgumentException(
						JGitText.get().updateRequiresOldIdAndNewId);
			}
			break;
		default:
			throw new IllegalStateException(
					JGitText.get().enumValueNotSupported0);
		}
		this.type = type;
	}

	/**
	 * Create a command to switch a reference from object to symbolic.
	 *
	 * @param oldId
	 *            the old object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref creation.
	 * @param newSymref
	 *            new target, must begin with {@code "refs/"}. Use {@code null}
	 *            to indicate a ref deletion.
	 * @param name
	 *            name of the reference to make symbolic.
	 * @since 4.10
	 */
	private ReceiveCommand(ObjectId oldId, String newSymref, String name) {
		if (oldId == null) {
			throw new IllegalArgumentException(
					JGitText.get().oldIdMustNotBeNull);
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().nameMustNotBeNullOrEmpty);
		}
		this.oldId = oldId;
		this.oldSymref = null;
		this.newId = ObjectId.zeroId();
		this.newSymref = newSymref;
		this.name = name;
		if (AnyObjectId.isEqual(ObjectId.zeroId(), oldId)) {
			type = Type.CREATE;
		} else if (newSymref != null) {
			type = Type.UPDATE;
		} else {
			type = Type.DELETE;
		}
		typeIsCorrect = true;
	}

	/**
	 * Create a command to switch a reference from symbolic to object.
	 *
	 * @param oldSymref
	 *            expected old target. Use {@code null} to indicate a ref
	 *            creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref deletion.
	 * @param name
	 *            name of the reference to convert from symbolic.
	 * @since 4.10
	 */
	private ReceiveCommand(String oldSymref, ObjectId newId, String name) {
		if (newId == null) {
			throw new IllegalArgumentException(
					JGitText.get().newIdMustNotBeNull);
		}
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().nameMustNotBeNullOrEmpty);
		}
		this.oldId = ObjectId.zeroId();
		this.oldSymref = oldSymref;
		this.newId = newId;
		this.newSymref = null;
		this.name = name;
		if (oldSymref == null) {
			type = Type.CREATE;
		} else if (!AnyObjectId.isEqual(ObjectId.zeroId(), newId)) {
			type = Type.UPDATE;
		} else {
			type = Type.DELETE;
		}
		typeIsCorrect = true;
	}

	/**
	 * Create a command to switch a symbolic reference's target.
	 *
	 * @param oldTarget
	 *            expected old target. Use {@code null} to indicate a ref
	 *            creation.
	 * @param newTarget
	 *            new target. Use {@code null} to indicate a ref deletion.
	 * @param name
	 *            name of the reference to make symbolic.
	 * @since 4.10
	 */
	private ReceiveCommand(@Nullable String oldTarget, String newTarget, String name) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException(
					JGitText.get().nameMustNotBeNullOrEmpty);
		}
		this.oldId = ObjectId.zeroId();
		this.oldSymref = oldTarget;
		this.newId = ObjectId.zeroId();
		this.newSymref = newTarget;
		this.name = name;
		if (oldTarget == null) {
			if (newTarget == null) {
				throw new IllegalArgumentException(
						JGitText.get().bothRefTargetsMustNotBeNull);
			}
			type = Type.CREATE;
		} else if (newTarget != null) {
			type = Type.UPDATE;
		} else {
			type = Type.DELETE;
		}
		typeIsCorrect = true;
	}

	/**
	 * Get the old value the client thinks the ref has.
	 *
	 * @return the old value the client thinks the ref has.
	 */
	public ObjectId getOldId() {
		return oldId;
	}

	/**
	 * Get expected old target for a symbolic reference.
	 *
	 * @return expected old target for a symbolic reference.
	 * @since 4.10
	 */
	@Nullable
	public String getOldSymref() {
		return oldSymref;
	}

	/**
	 * Get the requested new value for this ref.
	 *
	 * @return the requested new value for this ref.
	 */
	public ObjectId getNewId() {
		return newId;
	}

	/**
	 * Get requested new target for a symbolic reference.
	 *
	 * @return requested new target for a symbolic reference.
	 * @since 4.10
	 */
	@Nullable
	public String getNewSymref() {
		return newSymref;
	}

	/**
	 * Get the name of the ref being updated.
	 *
	 * @return the name of the ref being updated.
	 */
	public String getRefName() {
		return name;
	}

	/**
	 * Get the type of this command; see {@link Type}.
	 *
	 * @return the type of this command; see {@link Type}.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Get the ref, if this was advertised by the connection.
	 *
	 * @return the ref, if this was advertised by the connection.
	 */
	public Ref getRef() {
		return ref;
	}

	/**
	 * Get the current status code of this command.
	 *
	 * @return the current status code of this command.
	 */
	public Result getResult() {
		return status;
	}

	/**
	 * Get the message associated with a failure status.
	 *
	 * @return the message associated with a failure status.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the message to include in the reflog.
	 * <p>
	 * Overrides the default set by {@code setRefLogMessage} on any containing
	 * {@link org.eclipse.jgit.lib.BatchRefUpdate}.
	 *
	 * @param msg
	 *            the message to describe this change. If null and appendStatus is
	 *            false, the reflog will not be updated.
	 * @param appendStatus
	 *            true if the status of the ref change (fast-forward or
	 *            forced-update) should be appended to the user supplied message.
	 * @since 4.9
	 */
	public void setRefLogMessage(String msg, boolean appendStatus) {
		customRefLog = true;
		if (msg == null && !appendStatus) {
			disableRefLog();
		} else if (msg == null && appendStatus) {
			refLogMessage = ""; //$NON-NLS-1$
			refLogIncludeResult = true;
		} else {
			refLogMessage = msg;
			refLogIncludeResult = appendStatus;
		}
	}

	/**
	 * Don't record this update in the ref's associated reflog.
	 * <p>
	 * Equivalent to {@code setRefLogMessage(null, false)}.
	 *
	 * @since 4.9
	 */
	public void disableRefLog() {
		customRefLog = true;
		refLogMessage = null;
		refLogIncludeResult = false;
	}

	/**
	 * Force writing a reflog for the updated ref.
	 *
	 * @param force whether to force.
	 * @since 4.9
	 */
	public void setForceRefLog(boolean force) {
		forceRefLog = Boolean.valueOf(force);
	}

	/**
	 * Check whether this command has a custom reflog message setting that should
	 * override defaults in any containing
	 * {@link org.eclipse.jgit.lib.BatchRefUpdate}.
	 * <p>
	 * Does not take into account whether {@code #setForceRefLog(boolean)} has
	 * been called.
	 *
	 * @return whether a custom reflog is set.
	 * @since 4.9
	 */
	public boolean hasCustomRefLog() {
		return customRefLog;
	}

	/**
	 * Check whether log has been disabled by {@link #disableRefLog()}.
	 *
	 * @return true if disabled.
	 * @since 4.9
	 */
	public boolean isRefLogDisabled() {
		return refLogMessage == null;
	}

	/**
	 * Get the message to include in the reflog.
	 *
	 * @return message the caller wants to include in the reflog; null if the
	 *         update should not be logged.
	 * @since 4.9
	 */
	@Nullable
	public String getRefLogMessage() {
		return refLogMessage;
	}

	/**
	 * Check whether the reflog message should include the result of the update,
	 * such as fast-forward or force-update.
	 *
	 * @return true if the message should include the result.
	 * @since 4.9
	 */
	public boolean isRefLogIncludingResult() {
		return refLogIncludeResult;
	}

	/**
	 * Check whether the reflog should be written regardless of repo defaults.
	 *
	 * @return whether force writing is enabled; {@code null} if
	 *         {@code #setForceRefLog(boolean)} was never called.
	 * @since 4.9
	 */
	@Nullable
	public Boolean isForceRefLog() {
		return forceRefLog;
	}

	/**
	 * Set the status of this command.
	 *
	 * @param s
	 *            the new status code for this command.
	 */
	public void setResult(Result s) {
		setResult(s, null);
	}

	/**
	 * Set the status of this command.
	 *
	 * @param s
	 *            new status code for this command.
	 * @param m
	 *            optional message explaining the new status.
	 */
	public void setResult(Result s, String m) {
		status = s;
		message = m;
	}

	/**
	 * Update the type of this command by checking for fast-forward.
	 * <p>
	 * If the command's current type is UPDATE, a merge test will be performed
	 * using the supplied RevWalk to determine if {@link #getOldId()} is fully
	 * merged into {@link #getNewId()}. If some commits are not merged the
	 * update type is changed to
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Type#UPDATE_NONFASTFORWARD}.
	 *
	 * @param walk
	 *            an instance to perform the merge test with. The caller must
	 *            allocate and release this object.
	 * @throws java.io.IOException
	 *             either oldId or newId is not accessible in the repository
	 *             used by the RevWalk. This usually indicates data corruption,
	 *             and the command cannot be processed.
	 */
	public void updateType(RevWalk walk) throws IOException {
		if (typeIsCorrect)
			return;
		if (type == Type.UPDATE && !AnyObjectId.isEqual(oldId, newId)) {
			RevObject o = walk.parseAny(oldId);
			RevObject n = walk.parseAny(newId);
			if (!(o instanceof RevCommit)
					|| !(n instanceof RevCommit)
					|| !walk.isMergedInto((RevCommit) o, (RevCommit) n))
				setType(Type.UPDATE_NONFASTFORWARD);
		}
		typeIsCorrect = true;
	}

	/**
	 * Execute this command during a receive-pack session.
	 * <p>
	 * Sets the status of the command as a side effect.
	 *
	 * @param rp
	 *            receive-pack session.
	 * @since 5.6
	 */
	public void execute(ReceivePack rp) {
		try {
			String expTarget = getOldSymref();
			boolean detach = getNewSymref() != null
					|| (type == Type.DELETE && expTarget != null);
			RefUpdate ru = rp.getRepository().updateRef(getRefName(), detach);
			if (expTarget != null) {
				if (!ru.getRef().isSymbolic() || !ru.getRef().getTarget()
						.getName().equals(expTarget)) {
					setResult(Result.LOCK_FAILURE);
					return;
				}
			}

			ru.setRefLogIdent(rp.getRefLogIdent());
			ru.setRefLogMessage(refLogMessage, refLogIncludeResult);
			switch (getType()) {
			case DELETE:
				if (!ObjectId.zeroId().equals(getOldId())) {
					// We can only do a CAS style delete if the client
					// didn't bork its delete request by sending the
					// wrong zero id rather than the advertised one.
					//
					ru.setExpectedOldObjectId(getOldId());
				}
				ru.setForceUpdate(true);
				setResult(ru.delete(rp.getRevWalk()));
				break;

			case CREATE:
			case UPDATE:
			case UPDATE_NONFASTFORWARD:
				ru.setForceUpdate(rp.isAllowNonFastForwards());
				ru.setExpectedOldObjectId(getOldId());
				ru.setRefLogMessage("push", true); //$NON-NLS-1$
				if (getNewSymref() != null) {
					setResult(ru.link(getNewSymref()));
				} else {
					ru.setNewObjectId(getNewId());
					setResult(ru.update(rp.getRevWalk()));
				}
				break;
			}
		} catch (IOException err) {
			reject(err);
		}
	}

	void setRef(Ref r) {
		ref = r;
	}

	void setType(Type t) {
		type = t;
	}

	void setTypeFastForwardUpdate() {
		type = Type.UPDATE;
		typeIsCorrect = true;
	}

	/**
	 * Set the result of this command.
	 *
	 * @param r
	 *            the new result code for this command.
	 */
	public void setResult(RefUpdate.Result r) {
		switch (r) {
		case NOT_ATTEMPTED:
			setResult(Result.NOT_ATTEMPTED);
			break;

		case LOCK_FAILURE:
		case IO_FAILURE:
			setResult(Result.LOCK_FAILURE);
			break;

		case NO_CHANGE:
		case NEW:
		case FORCED:
		case FAST_FORWARD:
			setResult(Result.OK);
			break;

		case REJECTED:
			setResult(Result.REJECTED_NONFASTFORWARD);
			break;

		case REJECTED_CURRENT_BRANCH:
			setResult(Result.REJECTED_CURRENT_BRANCH);
			break;

		case REJECTED_MISSING_OBJECT:
			setResult(Result.REJECTED_MISSING_OBJECT);
			break;

		case REJECTED_OTHER_REASON:
			setResult(Result.REJECTED_OTHER_REASON);
			break;

		default:
			setResult(Result.REJECTED_OTHER_REASON, r.name());
			break;
		}
	}

	void reject(IOException err) {
		setResult(Result.REJECTED_OTHER_REASON, MessageFormat.format(
				JGitText.get().lockError, err.getMessage()));
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return getType().name() + ": " + getOldId().name() + " "
				+ getNewId().name() + " " + getRefName();
	}
}
