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

import java.util.List;

import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * The interface is used to construct a cherry-picked commit message based on
 * the original commit
 *
 * @see #ORIGINAL
 * @see #ORIGINAL_WITH_REFERENCE
 * @since 6.9
 */
public interface CherryPickCommitMessageProvider {

	/**
	 * This provider returns the original commit message
	 */
	static final CherryPickCommitMessageProvider ORIGINAL = RevCommit::getFullMessage;

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
	static final CherryPickCommitMessageProvider ORIGINAL_WITH_REFERENCE = srcCommit -> {
		String fullMessage = srcCommit.getFullMessage();

		// Don't add extra new line after footer (aka trailer)
		// https://stackoverflow.com/questions/70007405/git-log-exclude-cherry-pick-messages-for-trailers
		// https://lore.kernel.org/git/7vmx136cdc.fsf@alter.siamese.dyndns.org
		String separator = messageEndsWithFooter(srcCommit) ? "\n" : "\n\n"; //$NON-NLS-1$//$NON-NLS-2$
		String revisionString = srcCommit.getName();
		return String.format("%s%s(cherry picked from commit %s)", //$NON-NLS-1$
				fullMessage, separator, revisionString);
	};

	/**
	 * @param srcCommit
	 *            original cherry-picked commit
	 * @return target cherry-picked commit message
	 */
	String getCherryPickedCommitMessage(RevCommit srcCommit);

	private static boolean messageEndsWithFooter(RevCommit srcCommit) {
		byte[] rawBuffer = srcCommit.getRawBuffer();
		List<FooterLine> footers = srcCommit.getFooterLines();
		int maxFooterEnd = footers.stream().mapToInt(FooterLine::getEndOffset)
				.max().orElse(-1);
		return rawBuffer.length == maxFooterEnd;
	}
}
