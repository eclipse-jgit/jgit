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

import java.util.Optional;
import java.util.function.BiConsumer;

import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.CommitContent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

abstract class BaseCreateCommitTree<T extends CommitContent> {

	final T commitContent;
	final Git git;
	final ObjectId headId;
	final ObjectInserter odi;

	BaseCreateCommitTree(final Git git, final ObjectId headId, final ObjectInserter inserter, final T commitContent) {
		this.git = git;
		this.headId = headId;
		this.odi = inserter;
		this.commitContent = commitContent;
	}

	Optional<ObjectId> buildTree(final DirCacheEditor editor) {
		try {
			return Optional.ofNullable(editor.getDirCache().writeTree(odi));
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	void iterateOverTreeWalk(final Git git, final ObjectId headId,
			final BiConsumer<String, CanonicalTreeParser> consumer) {
		if (headId != null) {
			try (final TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
				final int hIdx = treeWalk.addTree(new RevWalk(git.getRepository()).parseTree(headId));
				treeWalk.setRecursive(true);

				while (treeWalk.next()) {
					final String walkPath = treeWalk.getPathString();
					final CanonicalTreeParser hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);

					consumer.accept(walkPath, hTree);
				}
			} catch (final Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	void addToTemporaryInCoreIndex(final DirCacheEditor editor, final DirCacheEntry dcEntry, final ObjectId objectId,
			final FileMode fileMode) {
		editor.add(new DirCacheEditor.PathEdit(dcEntry) {
			@Override
			public void apply(final DirCacheEntry ent) {
				ent.setObjectId(objectId);
				ent.setFileMode(fileMode);
			}
		});
	}
}
