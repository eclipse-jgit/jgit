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

import java.util.UUID;

import org.eclipse.jgit.annotations.Nullable;

/**
 * Description of a submission, the update of multiple repositories in a
 * simultaneous operation.
 *
 * @since 5.9
 */
public class SubmissionInfo {

	private final String submissionId;

	private final String description;

	/**
	 * @param submissionId
	 *            Identifier to retrieve the submission
	 * @param description
	 *            Free form text that can be used e.g. for logging
	 */
	public SubmissionInfo(String submissionId, @Nullable String description) {
		this.submissionId = submissionId;
		this.description = description;
	}

	/**
	 * Create a submission with a random id.
	 *
	 * @return a SubmissionInfo with a random UUID.
	 */
	public static SubmissionInfo anonymous() {
		return new SubmissionInfo(UUID.randomUUID().toString(),
				"anonymous-topic"); //$NON-NLS-1$
	}

	/**
	 * Unique identifier for a submission
	 *
	 * @return submission id
	 */
	public String getSubmissionId() {
		return submissionId;
	}

	/**
	 * Free form text attached to this submission.
	 *
	 * @return description
	 */
	@Nullable
	public String getDescription() {
		return description;
	}
}
