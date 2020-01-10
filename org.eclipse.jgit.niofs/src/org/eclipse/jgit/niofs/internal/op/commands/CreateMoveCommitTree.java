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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.MoveCommitContent;

public class CreateMoveCommitTree extends BaseCreateCommitTree<MoveCommitContent> {

	public CreateMoveCommitTree(final Git git, final ObjectId headId, final ObjectInserter inserter,
			final MoveCommitContent commitContent) {
		super(git, headId, inserter, commitContent);
	}

	public Optional<ObjectId> execute() {
		final Map<String, String> content = commitContent.getContent();
		final DirCacheEditor editor = DirCache.newInCore().editor();
		final List<String> pathsAdded = new ArrayList<>();

		try {
			iterateOverTreeWalk(git, headId, (walkPath, hTree) -> {
				final String toPath = content.get(walkPath);
				final DirCacheEntry dcEntry = new DirCacheEntry((toPath == null) ? walkPath : toPath);
				if (!pathsAdded.contains(dcEntry.getPathString())) {
					addToTemporaryInCoreIndex(editor, dcEntry, hTree.getEntryObjectId(), hTree.getEntryFileMode());
					pathsAdded.add(dcEntry.getPathString());
				}
			});
			editor.finish();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}

		return buildTree(editor);
	}
}
