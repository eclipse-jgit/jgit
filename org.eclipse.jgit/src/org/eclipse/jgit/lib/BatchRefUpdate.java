/*
 * Copyright (C) 2008-2012, Google Inc.
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

import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Batch of reference updates to be applied to a repository.
 * <p>
 * The batch update is primarily useful in the transport code, where a client or
 * server is making changes to more than one reference at a time.
 */
public class BatchRefUpdate {
	private final RefDatabase refdb;

	/** Commands to apply during this batch. */
	private final List<ReceiveCommand> commands;

	/** Does the caller permit a forced update on a reference? */
	private boolean allowNonFastForwards;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Message the caller wants included in the reflog. */
	private String refLogMessage;

	/** Should the result value be appended to {@link #refLogMessage}. */
	private boolean refLogIncludeResult;

	/**
	 * Initialize a new batch update.
	 *
	 * @param refdb
	 *            the reference database of the repository to be updated.
	 */
	protected BatchRefUpdate(RefDatabase refdb) {
		this.refdb = refdb;
		this.commands = new ArrayList<ReceiveCommand>();
	}

	/**
	 * @return true if the batch update will permit a non-fast-forward update to
	 *         an existing reference.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * Set if this update wants to permit a forced update.
	 *
	 * @param allow
	 *            true if this update batch should ignore merge tests.
	 * @return {@code this}.
	 */
	public BatchRefUpdate setAllowNonFastForwards(boolean allow) {
		allowNonFastForwards = allow;
		return this;
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
	 * @return {@code this}.
	 */
	public BatchRefUpdate setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
		return this;
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
	public boolean isRefLogIncludingResult() {
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
	 * @return {@code this}.
	 */
	public BatchRefUpdate setRefLogMessage(String msg, boolean appendStatus) {
		if (msg == null && !appendStatus)
			disableRefLog();
		else if (msg == null && appendStatus) {
			refLogMessage = "";
			refLogIncludeResult = true;
		} else {
			refLogMessage = msg;
			refLogIncludeResult = appendStatus;
		}
		return this;
	}

	/**
	 * Don't record this update in the ref's associated reflog.
	 *
	 * @return {@code this}.
	 */
	public BatchRefUpdate disableRefLog() {
		refLogMessage = null;
		refLogIncludeResult = false;
		return this;
	}

	/** @return true if log has been disabled by {@link #disableRefLog()}. */
	public boolean isRefLogDisabled() {
		return refLogMessage == null;
	}

	/** @return commands this update will process. */
	public List<ReceiveCommand> getCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Add a single command to this batch update.
	 *
	 * @param cmd
	 *            the command to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(ReceiveCommand cmd) {
		commands.add(cmd);
		return this;
	}

	/**
	 * Add commands to this batch update.
	 *
	 * @param cmd
	 *            the commands to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(ReceiveCommand... cmd) {
		return addCommand(Arrays.asList(cmd));
	}

	/**
	 * Add commands to this batch update.
	 *
	 * @param cmd
	 *            the commands to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(Collection<ReceiveCommand> cmd) {
		commands.addAll(cmd);
		return this;
	}

	/**
	 * Execute this batch update.
	 * <p>
	 * The default implementation of this method performs a sequential reference
	 * update over each reference.
	 *
	 * @param walk
	 *            a RevWalk to parse tags in case the storage system wants to
	 *            store them pre-peeled, a common performance optimization.
	 * @param update
	 *            progress monitor to receive update status on.
	 * @throws IOException
	 *             the database is unable to accept the update. Individual
	 *             command status must be tested to determine if there is a
	 *             partial failure, or a total failure.
	 */
	public void execute(RevWalk walk, ProgressMonitor update)
			throws IOException {
		update.beginTask(JGitText.get().updatingReferences, commands.size());
		for (ReceiveCommand cmd : commands) {
			try {
				update.update(1);

				if (cmd.getResult() == NOT_ATTEMPTED) {
					cmd.updateType(walk);
					RefUpdate ru = newUpdate(cmd);
					switch (cmd.getType()) {
					case DELETE:
						cmd.setResult(ru.delete(walk));
						continue;

					case CREATE:
					case UPDATE:
					case UPDATE_NONFASTFORWARD:
						cmd.setResult(ru.update(walk));
						continue;
					}
				}
			} catch (IOException err) {
				cmd.setResult(REJECTED_OTHER_REASON, MessageFormat.format(
						JGitText.get().lockError, err.getMessage()));
			}
		}
		update.endTask();
	}

	/**
	 * Create a new RefUpdate copying the batch settings.
	 *
	 * @param cmd
	 *            specific command the update should be created to copy.
	 * @return a single reference update command.
	 * @throws IOException
	 *             the reference database cannot make a new update object for
	 *             the given reference.
	 */
	protected RefUpdate newUpdate(ReceiveCommand cmd) throws IOException {
		RefUpdate ru = refdb.newUpdate(cmd.getRefName(), false);
		if (isRefLogDisabled())
			ru.disableRefLog();
		else {
			ru.setRefLogIdent(refLogIdent);
			ru.setRefLogMessage(refLogMessage, refLogIncludeResult);
		}
		switch (cmd.getType()) {
		case DELETE:
			if (!ObjectId.zeroId().equals(cmd.getOldId()))
				ru.setExpectedOldObjectId(cmd.getOldId());
			ru.setForceUpdate(true);
			return ru;

		case CREATE:
		case UPDATE:
		case UPDATE_NONFASTFORWARD:
		default:
			ru.setForceUpdate(isAllowNonFastForwards());
			ru.setExpectedOldObjectId(cmd.getOldId());
			ru.setNewObjectId(cmd.getNewId());
			return ru;
		}
	}
}
