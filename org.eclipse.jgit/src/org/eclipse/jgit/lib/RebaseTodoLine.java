/*
 * Copyright (C) 2013, Christian Halstrick <christian.halstrick@sap.com>
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

import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;

/**
 * Describes a single line in a file formatted like the git-rebase-todo file.
 *
 */
public class RebaseTodoLine {
	/**
	 * Describes rebase actions
	 */
	public static enum Action {
		/** Use commit */
		PICK("pick", "p"), //$NON-NLS-1$ //$NON-NLS-2$
		/** Use commit, but edit the commit message */
		REWORD("reword", "r"), //$NON-NLS-1$ //$NON-NLS-2$
		/** Use commit, but stop for amending */
		EDIT("edit", "e"), // later add SQUASH, FIXUP, etc. //$NON-NLS-1$ //$NON-NLS-2$
		/**
		 * A comment in the file. Also blank lines (or lines containing only
		 * whitespaces) are reported as comments
		 */
		COMMENT("comment", "#"); //$NON-NLS-1$ //$NON-NLS-2$

		private final String token;

		private final String shortToken;

		private Action(String token, String shortToken) {
			this.token = token;
			this.shortToken = shortToken;
		}

		/**
		 * @return full action token name
		 */
		public String toToken() {
			return this.token;
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return "Action[" + token + "]";
		}

		/**
		 * @param token
		 * @return the Action
		 */
		static public Action parse(String token) {
			for (Action action : Action.values()) {
				if (action.token.equals(token)
						|| action.shortToken.equals(token))
					return action;
			}
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().unknownOrUnsupportedCommand, token,
					Action.values()));
		}
	}

	Action action;

	AbbreviatedObjectId commit;

	byte[] shortMessage;

	byte[] comment;

	/**
	 * Creates a new comment line
	 *
	 * @param commentBuffer
	 *            a buffer containing the comment
	 * @param commentPos
	 *            the position in the buffer where the comment starts
	 * @param commentLen
	 *            the length of the comment
	 */
	public RebaseTodoLine(byte[] commentBuffer, int commentPos, int commentLen) {
		setComment(commentBuffer, commentPos, commentLen);
	}

	/**
	 * Creates a new non-comment line
	 *
	 * @param action
	 * @param commit
	 * @param shortMessageBuffer
	 * @param shortMessagePos
	 * @param shortMessageLen
	 */
	public RebaseTodoLine(Action action, AbbreviatedObjectId commit,
			byte[] shortMessageBuffer, int shortMessagePos, int shortMessageLen) {
		setNonComment(action, commit, shortMessageBuffer, shortMessagePos,
				shortMessageLen);
	}

	/**
	 * Transforms this line into a non-comment line
	 *
	 * @param action
	 * @param commit
	 * @param shortMessageBuffer
	 * @param shortMessagePos
	 * @param shortMessageLen
	 */
	public void setNonComment(Action action, AbbreviatedObjectId commit,
			byte[] shortMessageBuffer, int shortMessagePos, int shortMessageLen) {
		this.action = action;
		this.commit = commit;
		if (shortMessageBuffer == null)
			return;
		shortMessage = new byte[shortMessageLen];
		System.arraycopy(shortMessageBuffer, shortMessagePos, shortMessage, 0,
				shortMessageLen);
	}

	/**
	 * @return rebase action type
	 */
	public Action getAction() {
		return action;
	}

	/**
	 * Set's the new non-comment Action for a non-comment line
	 *
	 * @param newAction
	 */
	public void setAction(Action newAction) {
		if (Action.COMMENT.equals(newAction) || Action.COMMENT.equals(action))
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cannotChangeActionOnComment, action,
					newAction));
		this.action = newAction;
	}

	/**
	 * Transforms this line into a comment line
	 *
	 * @param commentBuffer
	 * @param commentPos
	 * @param commentLen
	 */
	public void setComment(byte[] commentBuffer, int commentPos,
			int commentLen) {
		this.action = Action.COMMENT;
		if (commentBuffer == null)
			return;
		comment = new byte[commentLen];
		System.arraycopy(commentBuffer, commentPos, comment, 0, commentLen);
	}

	/**
	 * @return abbreviated commit SHA-1 of commit that action will be performed
	 *         on
	 */
	public AbbreviatedObjectId getCommit() {
		return commit;
	}

	/**
	 * @return short message commit of commit that action will be performed
	 *         on
	 */
	public byte[] getShortMessage() {
		return shortMessage;
	}

	/**
	 * @return a comment. If the line is a comment line then the comment is
	 *         returned. Lines starting with # or blank lines or lines
	 *         containing only whitespaces are considered as comment lines. The
	 *         complete line is returned (e.g. including the '#')
	 */
	public byte[] getComment() {
		return comment;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "Step["
				+ action
				+ ", "
				+ ((commit == null) ? "null" : commit)
				+ ", "
				+ ((shortMessage == null) ? "null" : new String(shortMessage))
				+ ", "
				+ ((comment == null) ? "" : new String(comment)) + "]";
	}
}
