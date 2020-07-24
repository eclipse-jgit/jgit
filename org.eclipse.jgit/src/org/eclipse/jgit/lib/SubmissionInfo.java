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

/**
 * Description of a submission, the update of multiple repositories in a simultaneous operation.
 *
 * @since 5.9
 */
public class SubmissionInfo {

	private final String submissionId;

	private final String description;

	public SubmissionInfo(String submissionId, String description) {
		this.submissionId = submissionId;
		this.description = description;
	}

	public static SubmissionInfo anonymous() {
		return new SubmissionInfo(UUID.randomUUID().toString(), "anonymous-topic");
	}

	public String getSubmissionId() {
		return submissionId;
	}

	public String getDescription() {
		return description;
	}
}
