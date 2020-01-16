/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.lib.RefUpdate.Result;

/**
 * A RefUpdate combination for renaming a reference.
 * <p>
 * If the source reference is currently pointed to by {@code HEAD}, then the
 * HEAD symbolic reference is updated to point to the new destination.
 */
public abstract class RefRename {
	/** Update operation to read and delete the source reference. */
	protected final RefUpdate source;

	/** Update operation to create/overwrite the destination reference. */
	protected final RefUpdate destination;

	private Result result = Result.NOT_ATTEMPTED;

	/**
	 * Initialize a new rename operation.
	 *
	 * @param src
	 *            operation to read and delete the source.
	 * @param dst
	 *            operation to create (or overwrite) the destination.
	 */
	protected RefRename(RefUpdate src, RefUpdate dst) {
		source = src;
		destination = dst;

		String cmd = ""; //$NON-NLS-1$
		if (source.getName().startsWith(Constants.R_HEADS)
				&& destination.getName().startsWith(Constants.R_HEADS))
			cmd = "Branch: "; //$NON-NLS-1$
		setRefLogMessage(cmd + "renamed " //$NON-NLS-1$
				+ Repository.shortenRefName(source.getName()) + " to " //$NON-NLS-1$
				+ Repository.shortenRefName(destination.getName()));
	}

	/**
	 * Get identity of the user making the change in the reflog.
	 *
	 * @return identity of the user making the change in the reflog.
	 */
	public PersonIdent getRefLogIdent() {
		return destination.getRefLogIdent();
	}

	/**
	 * Set the identity of the user appearing in the reflog.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the rename occurs
	 * and the log record is written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 */
	public void setRefLogIdent(PersonIdent pi) {
		destination.setRefLogIdent(pi);
	}

	/**
	 * Get the message to include in the reflog.
	 *
	 * @return message the caller wants to include in the reflog; null if the
	 *         rename should not be logged.
	 */
	public String getRefLogMessage() {
		return destination.getRefLogMessage();
	}

	/**
	 * Set the message to include in the reflog.
	 *
	 * @param msg
	 *            the message to describe this change.
	 */
	public void setRefLogMessage(String msg) {
		if (msg == null)
			disableRefLog();
		else
			destination.setRefLogMessage(msg, false);
	}

	/**
	 * Don't record this rename in the ref's associated reflog.
	 */
	public void disableRefLog() {
		destination.setRefLogMessage("", false); //$NON-NLS-1$
	}

	/**
	 * Get result of rename operation
	 *
	 * @return result of rename operation
	 */
	public Result getResult() {
		return result;
	}

	/**
	 * Rename
	 *
	 * @return the result of the new ref update
	 * @throws java.io.IOException
	 */
	public Result rename() throws IOException {
		try {
			result = doRename();
			return result;
		} catch (IOException err) {
			result = Result.IO_FAILURE;
			throw err;
		}
	}

	/**
	 * Do the actual rename
	 *
	 * @return the result of the rename operation.
	 * @throws java.io.IOException
	 */
	protected abstract Result doRename() throws IOException;

	/**
	 * Whether the {@code Constants#HEAD} reference needs to be linked to the
	 * new destination name.
	 *
	 * @return true if the {@code Constants#HEAD} reference needs to be linked
	 *         to the new destination name.
	 * @throws java.io.IOException
	 *             the current value of {@code HEAD} cannot be read.
	 */
	protected boolean needToUpdateHEAD() throws IOException {
		Ref head = source.getRefDatabase().exactRef(Constants.HEAD);
		if (head != null && head.isSymbolic()) {
			head = head.getTarget();
			return head.getName().equals(source.getName());
		}
		return false;
	}
}
