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

package org.eclipse.jgit.errors;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.merge.RecursiveMerger;

/**
 * Exception thrown if a merge fails because no merge base could be determined.
 *
 * @since 3.0
 */
public class NoMergeBaseException extends IOException {
	private static final long serialVersionUID = 1L;

	private MergeBaseFailureReason reason;

	/**
	 * An enum listing the different reason why no merge base could be
	 * determined.
	 */
	public static enum MergeBaseFailureReason {
		/**
		 * Multiple merge bases have been found (e.g. the commits to be merged
		 * have multiple common predecessors) but the merge strategy doesn't
		 * support this (e.g. ResolveMerge)
		 */
		MULTIPLE_MERGE_BASES_NOT_SUPPORTED,

		/**
		 * The number of merge bases exceeds {@link RecursiveMerger#MAX_BASES}
		 */
		TOO_MANY_MERGE_BASES,

		/**
		 * In order to find a single merge base it may required to merge
		 * together multiple common predecessors. If during these merges
		 * conflicts occur the merge fails with this reason
		 */
		CONFLICTS_DURING_MERGE_BASE_CALCULATION
	}


	/**
	 * Construct a NoMergeBase exception
	 *
	 * @param reason
	 *            the reason why no merge base could be found
	 * @param message
	 *            a text describing the problem
	 */
	public NoMergeBaseException(MergeBaseFailureReason reason, String message) {
		super(MessageFormat.format(JGitText.get().noMergeBase,
				reason.toString(), message));
		this.reason = reason;
	}

	/**
	 * Construct a NoMergeBase exception
	 *
	 * @param reason
	 *            the reason why no merge base could be found
	 * @param message
	 *            a text describing the problem
	 * @param why
	 *            an exception causing this error
	 */
	public NoMergeBaseException(MergeBaseFailureReason reason, String message,
			Throwable why) {
		super(MessageFormat.format(JGitText.get().noMergeBase,
				reason.toString(), message));
		this.reason = reason;
		initCause(why);
	}

	/**
	 * Get the reason why no merge base could be found
	 *
	 * @return the reason why no merge base could be found
	 */
	public MergeBaseFailureReason getReason() {
		return reason;
	}
}
