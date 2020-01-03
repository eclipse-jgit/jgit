/*
 * Copyright (C) 2013, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.IllegalTodoFileModification;
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
			throw new IllegalArgumentException(MessageFormat.format(
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
	 *            a {@link org.eclipse.jgit.lib.RebaseTodoLine.Action} object.
	 * @param commit
	 *            a {@link org.eclipse.jgit.lib.AbbreviatedObjectId} object.
	 * @param shortMessage
	 *            a {@link java.lang.String} object.
	 */
	public RebaseTodoLine(Action action, AbbreviatedObjectId commit,
			String shortMessage) {
		this.action = action;
		this.commit = commit;
		this.shortMessage = shortMessage;
		this.comment = null;
	}

	/**
	 * Get rebase action type
	 *
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
	 *            a {@link org.eclipse.jgit.lib.RebaseTodoLine.Action} object.
	 * @throws org.eclipse.jgit.errors.IllegalTodoFileModification
	 *             on attempt to set a non-comment action on a line which was a
	 *             comment line before.
	 */
	public void setAction(Action newAction) throws IllegalTodoFileModification {
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
				throw new IllegalTodoFileModification(MessageFormat.format(
						JGitText.get().cannotChangeActionOnComment, action,
						newAction));
		}
		this.action = newAction;
	}

	/**
	 * <p>
	 * Set a comment for this line that is used if this line's
	 * {@link org.eclipse.jgit.lib.RebaseTodoLine#action} is a {@link org.eclipse.jgit.lib.RebaseTodoLine.Action#COMMENT}
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

	private static IllegalArgumentException createInvalidCommentException(
			String newComment) {
		return new IllegalArgumentException(
				MessageFormat.format(
				JGitText.get().argumentIsNotAValidCommentString, newComment));
	}

	/**
	 * Get abbreviated commit SHA-1 of commit that action will be performed on
	 *
	 * @return abbreviated commit SHA-1 of commit that action will be performed
	 *         on
	 */
	public AbbreviatedObjectId getCommit() {
		return commit;
	}

	/**
	 * Get the first line of the commit message of the commit the action will be
	 * performed on.
	 *
	 * @return the first line of the commit message of the commit the action
	 *         will be performed on.
	 */
	public String getShortMessage() {
		return shortMessage;
	}

	/**
	 * Set short message
	 *
	 * @param shortMessage
	 *            a short message.
	 */
	public void setShortMessage(String shortMessage) {
		this.shortMessage = shortMessage;
	}

	/**
	 * Get a comment
	 *
	 * @return a comment. If the line is a comment line then the comment is
	 *         returned. Lines starting with # or blank lines or lines
	 *         containing only spaces and tabs are considered as comment lines.
	 *         The complete line is returned (e.g. including the '#')
	 */
	public String getComment() {
		return comment;
	}

	/** {@inheritDoc} */
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
