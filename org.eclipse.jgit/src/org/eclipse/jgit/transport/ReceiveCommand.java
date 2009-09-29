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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A command being processed by {@link ReceivePack}.
 * <p>
 * This command instance roughly translates to the server side representation of
 * the {@link RemoteRefUpdate} created by the client.
 */
public class ReceiveCommand {
	/** Type of operation requested. */
	public static enum Type {
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
	public static enum Result {
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

	private final ObjectId oldId;

	private final ObjectId newId;

	private final String name;

	private Type type;

	private Ref ref;

	private Result status;

	private String message;

	/**
	 * Create a new command for {@link ReceivePack}.
	 *
	 * @param oldId
	 *            the old object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref deletion.
	 * @param name
	 *            name of the ref being affected.
	 */
	public ReceiveCommand(final ObjectId oldId, final ObjectId newId,
			final String name) {
		this.oldId = oldId;
		this.newId = newId;
		this.name = name;

		type = Type.UPDATE;
		if (ObjectId.zeroId().equals(oldId))
			type = Type.CREATE;
		if (ObjectId.zeroId().equals(newId))
			type = Type.DELETE;
		status = Result.NOT_ATTEMPTED;
	}

	/** @return the old value the client thinks the ref has. */
	public ObjectId getOldId() {
		return oldId;
	}

	/** @return the requested new value for this ref. */
	public ObjectId getNewId() {
		return newId;
	}

	/** @return the name of the ref being updated. */
	public String getRefName() {
		return name;
	}

	/** @return the type of this command; see {@link Type}. */
	public Type getType() {
		return type;
	}

	/** @return the ref, if this was advertised by the connection. */
	public Ref getRef() {
		return ref;
	}

	/** @return the current status code of this command. */
	public Result getResult() {
		return status;
	}

	/** @return the message associated with a failure status. */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the status of this command.
	 *
	 * @param s
	 *            the new status code for this command.
	 */
	public void setResult(final Result s) {
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
	public void setResult(final Result s, final String m) {
		status = s;
		message = m;
	}

	void setRef(final Ref r) {
		ref = r;
	}

	void setType(final Type t) {
		type = t;
	}

	@Override
	public String toString() {
		return getType().name() + ": " + getOldId().name() + " "
				+ getNewId().name() + " " + getRefName();
	}
}
