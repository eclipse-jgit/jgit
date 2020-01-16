/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_SYMLINK;
import static org.eclipse.jgit.lib.Ref.Storage.NETWORK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

/**
 * Command to create, update or delete an entry inside a
 * {@link org.eclipse.jgit.internal.storage.reftree.RefTree}.
 * <p>
 * Unlike {@link org.eclipse.jgit.transport.ReceiveCommand} (which can only
 * update a reference to an {@link org.eclipse.jgit.lib.ObjectId}), a RefTree
 * Command can also create, modify or delete symbolic references to a target
 * reference.
 * <p>
 * RefTree Commands may wrap a {@code ReceiveCommand} to allow callers to
 * process an existing ReceiveCommand against a RefTree.
 * <p>
 * Commands should be passed into
 * {@link org.eclipse.jgit.internal.storage.reftree.RefTree#apply(java.util.Collection)}
 * for processing.
 */
public class Command {
	/**
	 * Set unprocessed commands as failed due to transaction aborted.
	 * <p>
	 * If a command is still
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#NOT_ATTEMPTED} it
	 * will be set to
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#REJECTED_OTHER_REASON}.
	 * If {@code why} is non-null its contents will be used as the message for
	 * the first command status.
	 *
	 * @param commands
	 *            commands to mark as failed.
	 * @param why
	 *            optional message to set on the first aborted command.
	 */
	public static void abort(Iterable<Command> commands, @Nullable String why) {
		if (why == null || why.isEmpty()) {
			why = JGitText.get().transactionAborted;
		}
		for (Command c : commands) {
			if (c.getResult() == NOT_ATTEMPTED) {
				c.setResult(REJECTED_OTHER_REASON, why);
				why = JGitText.get().transactionAborted;
			}
		}
	}

	private final Ref oldRef;
	private final Ref newRef;
	private final ReceiveCommand cmd;
	private Result result;

	/**
	 * Create a command to create, update or delete a reference.
	 * <p>
	 * At least one of {@code oldRef} or {@code newRef} must be supplied.
	 *
	 * @param oldRef
	 *            expected value. Null if the ref should not exist.
	 * @param newRef
	 *            desired value, must be peeled if not null and not symbolic.
	 *            Null to delete the ref.
	 */
	public Command(@Nullable Ref oldRef, @Nullable Ref newRef) {
		this.oldRef = oldRef;
		this.newRef = newRef;
		this.cmd = null;
		this.result = NOT_ATTEMPTED;

		if (oldRef == null && newRef == null) {
			throw new IllegalArgumentException();
		}
		if (newRef != null && !newRef.isPeeled() && !newRef.isSymbolic()) {
			throw new IllegalArgumentException();
		}
		if (oldRef != null && newRef != null
				&& !oldRef.getName().equals(newRef.getName())) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Construct a RefTree command wrapped around a ReceiveCommand.
	 *
	 * @param rw
	 *            walk instance to peel the {@code newId}.
	 * @param cmd
	 *            command received from a push client.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             {@code oldId} or {@code newId} is missing.
	 * @throws java.io.IOException
	 *             {@code oldId} or {@code newId} cannot be peeled.
	 */
	public Command(RevWalk rw, ReceiveCommand cmd)
			throws MissingObjectException, IOException {
		this.oldRef = toRef(rw, cmd.getOldId(), cmd.getOldSymref(),
				cmd.getRefName(), false);
		this.newRef = toRef(rw, cmd.getNewId(), cmd.getNewSymref(),
				cmd.getRefName(), true);
		this.cmd = cmd;
	}

	static Ref toRef(RevWalk rw, ObjectId id, @Nullable String target,
			String name, boolean mustExist)
			throws MissingObjectException, IOException {
		if (target != null) {
			return new SymbolicRef(name,
					new ObjectIdRef.Unpeeled(NETWORK, target, id));
		} else if (ObjectId.zeroId().equals(id)) {
			return null;
		}

		try {
			RevObject o = rw.parseAny(id);
			if (o instanceof RevTag) {
				RevObject p = rw.peel(o);
				return new ObjectIdRef.PeeledTag(NETWORK, name, id, p.copy());
			}
			return new ObjectIdRef.PeeledNonTag(NETWORK, name, id);
		} catch (MissingObjectException e) {
			if (mustExist) {
				throw e;
			}
			return new ObjectIdRef.Unpeeled(NETWORK, name, id);
		}
	}

	/**
	 * Get name of the reference affected by this command.
	 *
	 * @return name of the reference affected by this command.
	 */
	public String getRefName() {
		if (cmd != null) {
			return cmd.getRefName();
		} else if (newRef != null) {
			return newRef.getName();
		}
		return oldRef.getName();
	}

	/**
	 * Set the result of this command.
	 *
	 * @param result
	 *            the command result.
	 */
	public void setResult(Result result) {
		setResult(result, null);
	}

	/**
	 * Set the result of this command.
	 *
	 * @param result
	 *            the command result.
	 * @param why
	 *            optional message explaining the result status.
	 */
	public void setResult(Result result, @Nullable String why) {
		if (cmd != null) {
			cmd.setResult(result, why);
		} else {
			this.result = result;
		}
	}

	/**
	 * Get result of executing this command.
	 *
	 * @return result of executing this command.
	 */
	public Result getResult() {
		return cmd != null ? cmd.getResult() : result;
	}

	/**
	 * Get optional message explaining command failure.
	 *
	 * @return optional message explaining command failure.
	 */
	@Nullable
	public String getMessage() {
		return cmd != null ? cmd.getMessage() : null;
	}

	/**
	 * Old peeled reference.
	 *
	 * @return the old reference; null if the command is creating the reference.
	 */
	@Nullable
	public Ref getOldRef() {
		return oldRef;
	}

	/**
	 * New peeled reference.
	 *
	 * @return the new reference; null if the command is deleting the reference.
	 */
	@Nullable
	public Ref getNewRef() {
		return newRef;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		append(s, oldRef, "CREATE"); //$NON-NLS-1$
		s.append(' ');
		append(s, newRef, "DELETE"); //$NON-NLS-1$
		s.append(' ').append(getRefName());
		s.append(' ').append(getResult());
		if (getMessage() != null) {
			s.append(' ').append(getMessage());
		}
		return s.toString();
	}

	private static void append(StringBuilder s, Ref r, String nullName) {
		if (r == null) {
			s.append(nullName);
		} else if (r.isSymbolic()) {
			s.append(r.getTarget().getName());
		} else {
			ObjectId id = r.getObjectId();
			if (id != null) {
				s.append(id.name());
			}
		}
	}

	/**
	 * Check the entry is consistent with either the old or the new ref.
	 *
	 * @param entry
	 *            current entry; null if the entry does not exist.
	 * @return true if entry matches {@link #getOldRef()} or
	 *         {@link #getNewRef()}; otherwise false.
	 */
	boolean checkRef(@Nullable DirCacheEntry entry) {
		if (entry != null && entry.getRawMode() == 0) {
			entry = null;
		}
		return check(entry, oldRef) || check(entry, newRef);
	}

	private static boolean check(@Nullable DirCacheEntry cur,
			@Nullable Ref exp) {
		if (cur == null) {
			// Does not exist, ok if oldRef does not exist.
			return exp == null;
		} else if (exp == null) {
			// Expected to not exist, but currently exists, fail.
			return false;
		}

		if (exp.isSymbolic()) {
			String dst = exp.getTarget().getName();
			return cur.getRawMode() == TYPE_SYMLINK
					&& cur.getObjectId().equals(symref(dst));
		}

		return cur.getRawMode() == TYPE_GITLINK
				&& cur.getObjectId().equals(exp.getObjectId());
	}

	static ObjectId symref(String s) {
		@SuppressWarnings("resource")
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		return fmt.idFor(OBJ_BLOB, encode(s));
	}
}
