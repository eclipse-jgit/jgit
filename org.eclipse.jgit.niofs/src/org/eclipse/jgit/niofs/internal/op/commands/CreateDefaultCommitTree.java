/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.DefaultCommitContent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import static org.eclipse.jgit.lib.FileMode.REGULAR_FILE;

public class CreateDefaultCommitTree extends BaseCreateCommitTree<DefaultCommitContent> {

	public CreateDefaultCommitTree(final Git git, final ObjectId headId, final ObjectInserter inserter,
			final DefaultCommitContent commitContent) {
		super(git, headId, inserter, commitContent);
	}

	public Optional<ObjectId> execute() {
		final Map<String, File> content = commitContent.getContent();
		final Map<String, Map.Entry<File, ObjectId>> paths = new HashMap<>(content.size());
		final Set<String> path2delete = new HashSet<>();

		final DirCacheEditor editor = DirCache.newInCore().editor();

		try {
			for (final Map.Entry<String, File> pathAndContent : content.entrySet()) {
				final String gPath = PathUtil.normalize(pathAndContent.getKey());
				if (pathAndContent.getValue() == null) {
					path2delete.addAll(searchPathsToDelete(git, headId, gPath));
				} else {
					paths.putAll(storePathsIntoHashMap(odi, pathAndContent, gPath));
				}
			}

			iterateOverTreeWalk(git, headId, (walkPath, hTree) -> {
				if (paths.containsKey(walkPath) && paths.get(walkPath).getValue().equals(hTree.getEntryObjectId())) {
					paths.remove(walkPath);
				}

				if (paths.get(walkPath) == null && !path2delete.contains(walkPath)) {
					addToTemporaryInCoreIndex(editor, new DirCacheEntry(walkPath), hTree.getEntryObjectId(),
							hTree.getEntryFileMode());
				}
			});

			paths.forEach((key, value) -> {
				if (value.getKey() != null) {
					editor.add(new DirCacheEditor.PathEdit(new DirCacheEntry(key)) {
						@Override
						public void apply(final DirCacheEntry ent) {
							ent.setLength(value.getKey().length());
							ent.setLastModified(Instant.ofEpochMilli(value.getKey().lastModified()));
							ent.setFileMode(REGULAR_FILE);
							ent.setObjectId(value.getValue());
						}
					});
				}
			});

			editor.finish();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		if (path2delete.isEmpty() && paths.isEmpty()) {
			editor.getDirCache().clear();
			return Optional.empty();
		}

		return buildTree(editor);
	}

	private static Map<String, Map.Entry<File, ObjectId>> storePathsIntoHashMap(final ObjectInserter inserter,
			final Map.Entry<String, File> pathAndContent, final String gPath) {
		try (final InputStream inputStream = new FileInputStream(pathAndContent.getValue())) {
			final Map<String, Map.Entry<File, ObjectId>> paths = new HashMap<>();
			final ObjectId objectId = inserter.insert(Constants.OBJ_BLOB, pathAndContent.getValue().length(),
					inputStream);
			paths.put(gPath, new AbstractMap.SimpleEntry<>(pathAndContent.getValue(), objectId));
			return paths;
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static Set<String> searchPathsToDelete(final Git git, final ObjectId headId, final String gPath)
			throws java.io.IOException {
		try (final TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
			final Set<String> path2delete = new HashSet<>();
			treeWalk.addTree(new RevWalk(git.getRepository()).parseTree(headId));
			treeWalk.setRecursive(true);
			treeWalk.setFilter(PathFilter.create(gPath));

			while (treeWalk.next()) {
				path2delete.add(treeWalk.getPathString());
			}
			return path2delete;
		}
	}
}
