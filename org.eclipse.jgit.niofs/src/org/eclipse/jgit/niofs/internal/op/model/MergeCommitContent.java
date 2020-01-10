/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;

public class MergeCommitContent extends DefaultCommitContent {

	private final List<RevCommit> parents;

	public MergeCommitContent(final Map<String, File> content, final List<RevCommit> parents) {
		super(content);

		this.parents = parents;
	}

	public List<RevCommit> getParents() {
		return parents;
	}
}
