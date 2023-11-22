/*
 * Copyright (c) 2023 Dmitrii Naumenko <dmitrii.naumenko@jetbrains.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is aailable at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.revwalk.RevCommit;

/**
 * The interface is used to construct a cherry-picked commit message based on
 * the original commit
 *
 * @see CherryPickCommitMessageProvider#ORIGINAL
 * @see CherryPickCommitMessageProvider#ORIGINAL_WITH_REFERENCE
 * @since 6.9
 */
public interface CherryPickCommitMessageProvider {
	/**
	 * @param srcCommit
	 *            original cherry-picked commit
	 * @return target cherry-picked commit message
	 */
	String getCherryPickedCommitMessage(RevCommit srcCommit);

	/**
	 * This provider returns the original commit message
	 */
	CherryPickCommitMessageProvider ORIGINAL = RevCommit::getFullMessage;

	/**
	 * This provider returns the original commit message with original commit
	 * hash in SHA-1 form.<br>
	 * Example:
	 * 
	 * <pre>
	 * <code>my original commit message
	 *
	 * (cherry picked from commit 75355897dc28e9975afed028c1a6d8c6b97b2a3c)</code>
	 * </pre>
	 * 
	 * This is similar to <code>-x</code> flag in git-scm (see <a href=
	 * "https://git-scm.com/docs/git-cherry-pick#_options">https://git-scm.com/docs/git-cherry-pick#_options</a>)
	 */
	CherryPickCommitMessageProvider ORIGINAL_WITH_REFERENCE = srcCommit -> {
		String fullMessage = srcCommit.getFullMessage();
		String revisionString = srcCommit.getName();
		return String.format("%s\n\n(cherry picked from commit %s)", //$NON-NLS-1$
				fullMessage, revisionString);
	};
}
