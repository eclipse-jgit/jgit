/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.PathInfo;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class ListPathContent {

	private final Git git;
	private final String branchName;
	private final String path;

	public ListPathContent(final Git git, final String branchName, final String path) {
		this.git = git;
		this.branchName = branchName;
		this.path = path;
	}

	public List<PathInfo> execute() throws IOException {

		final String gitPath = PathUtil.normalize(path);
		final List<PathInfo> result = new ArrayList<>();
		final ObjectId tree = git.getTreeFromRef(branchName);
		if (tree == null) {
			return result;
		}
		try (final TreeWalk tw = new TreeWalk(git.getRepository())) {
			boolean found = false;
			if (gitPath.isEmpty()) {
				found = true;
			} else {
				tw.setFilter(PathFilter.create(gitPath));
			}
			tw.reset(tree);
			while (tw.next()) {
				if (!found && tw.isSubtree()) {
					tw.enterSubtree();
				}
				if (tw.getPathString().equals(gitPath)) {
					found = true;
					continue;
				}
				if (found) {
					result.add(new PathInfo(tw.getObjectId(0), tw.getPathString(), tw.getFileMode(0)));
				}
			}
			return result;
		}
	}
}
