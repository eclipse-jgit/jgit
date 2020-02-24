/*
 * Copyright (C) 2010, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.util.regex.Pattern;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Utilities for creating and working with Change-Id's, like the one used by
 * Gerrit Code Review.
 * <p>
 * A Change-Id is a SHA-1 computed from the content of a commit, in a similar
 * fashion to how the commit id is computed. Unlike the commit id a Change-Id is
 * retained in the commit and subsequent revised commits in the footer of the
 * commit text.
 */
public class ChangeIdUtil {

	static final String CHANGE_ID = "Change-Id:"; //$NON-NLS-1$

	// package-private so the unit test can test this part only
	@SuppressWarnings("nls")
	static String clean(String msg) {
		return msg.//
				replaceAll("(?i)(?m)^Signed-off-by:.*$\n?", "").// //$NON-NLS-1$
				replaceAll("(?m)^#.*$\n?", "").// //$NON-NLS-1$
				replaceAll("(?m)\n\n\n+", "\\\n").// //$NON-NLS-1$
				replaceAll("\\n*$", "").// //$NON-NLS-1$
				replaceAll("(?s)\ndiff --git.*", "").// //$NON-NLS-1$
				trim();
	}

	/**
	 * Compute a Change-Id.
	 *
	 * @param treeId
	 *            The id of the tree that would be committed
	 * @param firstParentId
	 *            parent id of previous commit or null
	 * @param author
	 *            the {@link org.eclipse.jgit.lib.PersonIdent} for the presumed
	 *            author and time
	 * @param committer
	 *            the {@link org.eclipse.jgit.lib.PersonIdent} for the presumed
	 *            committer and time
	 * @param message
	 *            The commit message
	 * @return the change id SHA1 string (without the 'I') or null if the
	 *         message is not complete enough
	 */
	public static ObjectId computeChangeId(final ObjectId treeId,
			final ObjectId firstParentId, final PersonIdent author,
			final PersonIdent committer, final String message) {
		String cleanMessage = clean(message);
		if (cleanMessage.length() == 0)
			return null;
		StringBuilder b = new StringBuilder();
		b.append("tree "); //$NON-NLS-1$
		b.append(ObjectId.toString(treeId));
		b.append("\n"); //$NON-NLS-1$
		if (firstParentId != null) {
			b.append("parent "); //$NON-NLS-1$
			b.append(ObjectId.toString(firstParentId));
			b.append("\n"); //$NON-NLS-1$
		}
		b.append("author "); //$NON-NLS-1$
		b.append(author.toExternalString());
		b.append("\n"); //$NON-NLS-1$
		b.append("committer "); //$NON-NLS-1$
		b.append(committer.toExternalString());
		b.append("\n\n"); //$NON-NLS-1$
		b.append(cleanMessage);
		try (ObjectInserter f = new ObjectInserter.Formatter()) {
			return f.idFor(Constants.OBJ_COMMIT, Constants.encode(b.toString()));
		}
	}

	private static final Pattern issuePattern = Pattern
			.compile("^(Bug|Issue)[a-zA-Z0-9-]*:.*$"); //$NON-NLS-1$

	private static final Pattern footerPattern = Pattern
			.compile("(^[a-zA-Z0-9-]+:(?!//).*$)"); //$NON-NLS-1$

	private static final Pattern changeIdPattern = Pattern
			.compile("(^" + CHANGE_ID + " *I[a-f0-9]{40}$)"); //$NON-NLS-1$ //$NON-NLS-2$

	private static final Pattern includeInFooterPattern = Pattern
			.compile("^[ \\[].*$"); //$NON-NLS-1$

	private static final Pattern trailingWhitespace = Pattern.compile("\\s+$"); //$NON-NLS-1$

	/**
	 * Find the right place to insert a Change-Id and return it.
	 * <p>
	 * The Change-Id is inserted before the first footer line but after a Bug
	 * line.
	 *
	 * @param message
	 *            a message.
	 * @param changeId
	 *            a Change-Id.
	 * @return a commit message with an inserted Change-Id line
	 */
	public static String insertId(String message, ObjectId changeId) {
		return insertId(message, changeId, false);
	}

	/**
	 * Find the right place to insert a Change-Id and return it.
	 * <p>
	 * If no Change-Id is found the Change-Id is inserted before the first
	 * footer line but after a Bug line.
	 *
	 * If Change-Id is found and replaceExisting is set to false, the message is
	 * unchanged.
	 *
	 * If Change-Id is found and replaceExisting is set to true, the Change-Id
	 * is replaced with {@code changeId}.
	 *
	 * @param message
	 *            a message.
	 * @param changeId
	 *            a Change-Id.
	 * @param replaceExisting
	 *            a boolean.
	 * @return a commit message with an inserted Change-Id line
	 */
	public static String insertId(String message, ObjectId changeId,
			boolean replaceExisting) {
		int indexOfChangeId = indexOfChangeId(message, "\n"); //$NON-NLS-1$
		if (indexOfChangeId > 0) {
			if (!replaceExisting) {
				return message;
			}
			StringBuilder ret = new StringBuilder(
					message.substring(0, indexOfChangeId));
			ret.append(CHANGE_ID);
			ret.append(" I"); //$NON-NLS-1$
			ret.append(ObjectId.toString(changeId));
			int indexOfNextLineBreak = message.indexOf('\n',
					indexOfChangeId);
			if (indexOfNextLineBreak > 0)
				ret.append(message.substring(indexOfNextLineBreak));
			return ret.toString();
		}

		String[] lines = message.split("\n"); //$NON-NLS-1$
		int footerFirstLine = indexOfFirstFooterLine(lines);
		int insertAfter = footerFirstLine;
		for (int i = footerFirstLine; i < lines.length; ++i) {
			if (issuePattern.matcher(lines[i]).matches()) {
				insertAfter = i + 1;
				continue;
			}
			break;
		}
		StringBuilder ret = new StringBuilder();
		int i = 0;
		for (; i < insertAfter; ++i) {
			ret.append(lines[i]);
			ret.append("\n"); //$NON-NLS-1$
		}
		if (insertAfter == lines.length && insertAfter == footerFirstLine)
			ret.append("\n"); //$NON-NLS-1$
		ret.append(CHANGE_ID);
		ret.append(" I"); //$NON-NLS-1$
		ret.append(ObjectId.toString(changeId));
		ret.append("\n"); //$NON-NLS-1$
		for (; i < lines.length; ++i) {
			ret.append(lines[i]);
			ret.append("\n"); //$NON-NLS-1$
		}
		return ret.toString();
	}

	/**
	 * Return the index in the String {@code message} where the Change-Id entry
	 * in the footer begins. If there are more than one entries matching the
	 * pattern, return the index of the last one in the last section. Because of
	 * Bug: 400818 we release the constraint here that a footer must contain
	 * only lines matching {@code footerPattern}.
	 *
	 * @param message
	 *            a message.
	 * @param delimiter
	 *            the line delimiter, like "\n" or "\r\n", needed to find the
	 *            footer
	 * @return the index of the ChangeId footer in the message, or -1 if no
	 *         ChangeId footer available
	 */
	public static int indexOfChangeId(String message, String delimiter) {
		String[] lines = message.split(delimiter);
		if (lines.length == 0)
			return -1;
		int indexOfChangeIdLine = 0;
		boolean inFooter = false;
		for (int i = lines.length - 1; i >= 0; --i) {
			if (!inFooter && isEmptyLine(lines[i]))
				continue;
			inFooter = true;
			if (changeIdPattern.matcher(trimRight(lines[i])).matches()) {
				indexOfChangeIdLine = i;
				break;
			} else if (isEmptyLine(lines[i]) || i == 0)
				return -1;
		}
		int indexOfChangeIdLineinString = 0;
		for (int i = 0; i < indexOfChangeIdLine; ++i)
			indexOfChangeIdLineinString += lines[i].length()
					+ delimiter.length();
		return indexOfChangeIdLineinString
				+ lines[indexOfChangeIdLine].indexOf(CHANGE_ID);
	}

	private static boolean isEmptyLine(String line) {
		return line.trim().length() == 0;
	}

	private static String trimRight(String s) {
		return trailingWhitespace.matcher(s).replaceAll(""); //$NON-NLS-1$
	}

	/**
	 * Find the index of the first line of the footer paragraph in an array of
	 * the lines, or lines.length if no footer is available
	 *
	 * @param lines
	 *            the commit message split into lines and the line delimiters
	 *            stripped off
	 * @return the index of the first line of the footer paragraph, or
	 *         lines.length if no footer is available
	 */
	public static int indexOfFirstFooterLine(String[] lines) {
		int footerFirstLine = lines.length;
		for (int i = lines.length - 1; i > 1; --i) {
			if (footerPattern.matcher(lines[i]).matches()) {
				footerFirstLine = i;
				continue;
			}
			if (footerFirstLine != lines.length && lines[i].length() == 0)
				break;
			if (footerFirstLine != lines.length
					&& includeInFooterPattern.matcher(lines[i]).matches()) {
				footerFirstLine = i + 1;
				continue;
			}
			footerFirstLine = lines.length;
			break;
		}
		return footerFirstLine;
	}
}
