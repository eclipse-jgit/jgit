/*
 * Copyright (C) 2009, 2021 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class PathPrefixFilterTest extends RepositoryTestCase {

	@Test
	public void testNonRecursiveFiltering() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt");

		List<String> paths = getMatchingPaths("a.sth", treeId);
		List<String> expected = Arrays.asList("a.sth");

		assertEquals(expected, paths);
	}

	@Test
	public void testSubdirectoryFiltering() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt", "foo/b.sth", "foo/b.txt",
				"foo/bar/c.txt",  "t.sth", "t.txt");

		List<String> paths = getMatchingPaths("foo", treeId, true);
		List<String> expected = Arrays.asList("foo/b.sth", "foo/b.txt", "foo/bar/c.txt");

		assertEquals(expected, paths);
	}

	@Test
	public void testNestedSubdirectoryFiltering() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt", "foo/b.sth", "foo/b.txt",
				"foo/bar/c.txt",  "t.sth", "t.txt");

		List<String> paths = getMatchingPaths("foo/bar", treeId, true);
		List<String> expected = Arrays.asList("foo/bar/c.txt");

		assertEquals(expected, paths);
	}

	@Test
	public void testNegated() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt");

		// The `a` prefix is not expected to match any of the entries. Only exact matches and
		// entries in a subdirectory to the prefix will be matched.
		List<String> paths = getMatchingPaths("a", treeId);
		List<String> expected = Arrays.asList();

		assertEquals(expected, paths);
	}

	private ObjectId createTree(String... paths) throws IOException {
		final ObjectInserter odi = db.newObjectInserter();
		final DirCache dc = db.readDirCache();
		final DirCacheBuilder builder = dc.builder();

		for (String path : paths) {
			DirCacheEntry entry = createEntry(path, FileMode.REGULAR_FILE);
			builder.add(entry);
		}

		builder.finish();
		final ObjectId treeId = dc.writeTree(odi);
		odi.flush();

		return treeId;
	}

	private List<String> getMatchingPaths(String suffixFilter,
			final ObjectId treeId) throws IOException {
		return getMatchingPaths(suffixFilter, treeId, false);
	}

	private List<String> getMatchingPaths(String suffixFilter,
			final ObjectId treeId, boolean recursiveWalk) throws IOException {
		return getMatchingPaths(suffixFilter, treeId, recursiveWalk, false);
	}

	private List<String> getMatchingPaths(String suffixFilter,
			final ObjectId treeId, boolean recursiveWalk, boolean negated)
			throws IOException {
		try (TreeWalk tw = new TreeWalk(db)) {
			TreeFilter filter = PathPrefixFilter.create(suffixFilter);

			if (negated) {
				filter = filter.negate();
			}

			tw.setFilter(filter);
			tw.setRecursive(recursiveWalk);
			tw.addTree(treeId);

			List<String> paths = new ArrayList<>();

			while (tw.next()) {
				paths.add( tw.getPathString() );
			}

			return paths;
		}
	}

}
