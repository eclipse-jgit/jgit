/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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
	protected RefRename(final RefUpdate src, final RefUpdate dst) {
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
	public void setRefLogIdent(final PersonIdent pi) {
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
	public void setRefLogMessage(final String msg) {
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
		Ref head = source.getRefDatabase().getRef(Constants.HEAD);
		if (head != null && head.isSymbolic()) {
			head = head.getTarget();
			return head.getName().equals(source.getName());
		}
		return false;
	}
}
