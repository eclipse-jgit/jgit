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
 * @since 3.2
 */
public class RebaseTodoLine {
	/**
	 * Describes rebase actions
	 */
	@SuppressWarnings("nls")
	public static enum Action {
		/** Use commit */
		PICK("pick", "p"),

		/** Use commit, but edit the commit message */
		REWORD("reword", "r"),

		/** Use commit, but stop for amending */
		EDIT("edit", "e"),

		/** Use commit, but meld into previous commit */
		SQUASH("squash", "s"),

		/** like "squash", but discard this commit's log message */
		FIXUP("fixup", "f"),

		/**
		 * A comment in the file. Also blank lines (or lines containing only
		 * whitespaces) are reported as comments
		 */
		COMMENT("comment", "#");

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

	final AbbreviatedObjectId commit;

	String shortMessage;

	String comment;

	/**
	 * Create a new comment line
	 *
	 * @param newComment
	 *            the new comment
	 */
	public RebaseTodoLine(String newComment) {
		this.action = Action.COMMENT;
		setComment(newComment);
		this.commit = null;
		this.shortMessage = null;
	}

	/**
	 * Create a new non-comment line
	 *
	 * @param action
	 * @param commit
	 * @param shortMessage
	 */
	public RebaseTodoLine(Action action, AbbreviatedObjectId commit,
			String shortMessage) {
		this.action = action;
		this.commit = commit;
		this.shortMessage = shortMessage;
		this.comment = null;
	}

	/**
	 * @return rebase action type
	 */
	public Action getAction() {
		return action;
	}

	/**
	 * Set the action. It's not allowed to set a non-comment action on a line
	 * which was a comment line before. But you are allowed to set the comment
	 * action on a non-comment line and afterwards change the action back to
	 * non-comment.
	 *
	 * @param newAction
	 */
	public void setAction(Action newAction) {
		if (!Action.COMMENT.equals(action) && Action.COMMENT.equals(newAction)) {
			// transforming from non-comment to comment
			if (comment == null)
				// no comment was explicitly set. Take the old line as comment
				// text
				comment = "# " + action.token + " " //$NON-NLS-1$ //$NON-NLS-2$
						+ ((commit == null) ? "null" : commit.name()) + " " //$NON-NLS-1$ //$NON-NLS-2$
						+ ((shortMessage == null) ? "null" : shortMessage); //$NON-NLS-1$
		} else if (Action.COMMENT.equals(action) && !Action.COMMENT.equals(newAction)) {
			// transforming from comment to non-comment
			if (commit == null)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().cannotChangeActionOnComment, action,
						newAction));
		}
		this.action = newAction;
	}

	/**
	 * <p>
	 * Set a comment for this line that is used if this line's
	 * {@link RebaseTodoLine#action} is a {@link Action#COMMENT}
	 * </p>
	 * It's allowed to unset the comment by calling
	 * <code>setComment(null)</code> <br>
	 * A valid comment either starts with a hash (i.e. <code>'#'</code>), is an
	 * empty string, or consists of only spaces and tabs.<br>
	 * If the argument <code>newComment</code> doesn't match these requirements
	 * an Exception is thrown.
	 *
	 * @param newComment
	 *            the comment
	 */
	public void setComment(String newComment) {
		if (newComment == null) {
			this.comment = null;
			return;
		}

		if (newComment.contains("\n") || newComment.contains("\r")) //$NON-NLS-1$ //$NON-NLS-2$
			throw createInvalidCommentException(newComment);

		if (newComment.trim().length() == 0 || newComment.startsWith("#")) { //$NON-NLS-1$
			this.comment = newComment;
			return;
		}

		throw createInvalidCommentException(newComment);
	}

	private static JGitInternalException createInvalidCommentException(
			String newComment) {
		IllegalArgumentException iae = new IllegalArgumentException(
				MessageFormat.format(
				JGitText.get().argumentIsNotAValidCommentString, newComment));
		return new JGitInternalException(iae.getMessage(), iae);
	}

	/**
	 * @return abbreviated commit SHA-1 of commit that action will be performed
	 *         on
	 */
	public AbbreviatedObjectId getCommit() {
		return commit;
	}

	/**
	 * @return the first line of the commit message of the commit the action
	 *         will be performed on.
	 */
	public String getShortMessage() {
		return shortMessage;
	}

	/**
	 * @param shortMessage
	 */
	public void setShortMessage(String shortMessage) {
		this.shortMessage = shortMessage;
	}

	/**
	 * @return a comment. If the line is a comment line then the comment is
	 *         returned. Lines starting with # or blank lines or lines
	 *         containing only spaces and tabs are considered as comment lines.
	 *         The complete line is returned (e.g. including the '#')
	 */
	public String getComment() {
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
				+ ((shortMessage == null) ? "null" : shortMessage)
				+ ", "
				+ ((comment == null) ? "" : comment) + "]";
	}
}
