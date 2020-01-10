/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

public class CommitHistory {

	private final List<RevCommit> commits;
	private final Map<AnyObjectId, String> pathsByCommit;
	private final String trackedPath;

	public CommitHistory(final List<RevCommit> commits, final Map<AnyObjectId, String> pathsByCommit,
			final String trackedPath) {
		this.commits = commits;
		this.pathsByCommit = pathsByCommit;
		this.trackedPath = trackedPath;
	}

	public List<RevCommit> getCommits() {
		return commits;
	}

	/**
	 * @return The initial file path that was followed, or else the root path (/) if
	 *         none was given.
	 */
	public String getTrackedFilePath() {
		return (trackedPath == null) ? "/" : trackedPath;
	}

	public String trackedFileNameChangeFor(final AnyObjectId commitId) {
		return Optional.ofNullable(pathsByCommit.get(commitId)).map(path -> "/" + path)
				.orElseGet(() -> getTrackedFilePath());
	}
}
