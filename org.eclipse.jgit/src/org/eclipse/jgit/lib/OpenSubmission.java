/*
 * Copyright (c) 2020, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Handler of a submission, a collection of updates over one of more repositories.
 *
 * Caller must invoke {@link #completed()} when all operations (successful or not) are done.
 *
 * Usually a builder or factory (e.g. {@link SingleRepoSubmissionFactory}) will assign an id, collect all
 * required changes, initialize the internals and return this handler to the caller.
 *
 * The {@link SubmissionInfo} identifies this submission. Use {@link BatchRefUpdate#setSubmission(SubmissionInfo)} to link a BatchRefUpdate with the submission.
 * How both interact is left to implementors.
 *
 * @since 5.9
 */
public interface OpenSubmission {

	/**
	 * Description of the submission in progress.
	 *
	 * @return description of the submission. Null in case of no active submission.
	 */
	@Nullable
	SubmissionInfo getSubmissionInfo();

	/**
	 * Mark this submission as done.
	 */
	void completed();

	OpenSubmission NOOP = new OpenSubmission() {

		@Override
		public SubmissionInfo getSubmissionInfo() {
			return null;
		}

		@Override
		public void completed() {}
	};
}