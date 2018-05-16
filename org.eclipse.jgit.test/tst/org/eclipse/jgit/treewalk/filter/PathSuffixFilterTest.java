/*
 * Copyright (C) 2009, 2013 Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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

public class PathSuffixFilterTest extends RepositoryTestCase {

	@Test
	public void testNonRecursiveFiltering() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt");

		List<String> paths = getMatchingPaths(".txt", treeId);
		List<String> expected = Arrays.asList("a.txt");

		assertEquals(expected, paths);
	}

	@Test
	public void testRecursiveFiltering() throws IOException {
		ObjectId treeId = createTree("a.sth", "a.txt", "sub/b.sth", "sub/b.txt");

		List<String> paths = getMatchingPaths(".txt", treeId, true);
		List<String> expected = Arrays.asList("a.txt", "sub/b.txt");

		assertEquals(expected, paths);
	}

	@Test
	public void testEdgeCases() throws IOException {
		ObjectId treeId = createTree("abc", "abcd", "bcd", "c");
		assertEquals(new ArrayList<String>(), getMatchingPaths("xbcd", treeId));
		assertEquals(new ArrayList<String>(), getMatchingPaths("abcx", treeId));
		assertEquals(Arrays.asList("abcd"), getMatchingPaths("abcd", treeId));
		assertEquals(Arrays.asList("abcd", "bcd"), getMatchingPaths("bcd", treeId));
		assertEquals(Arrays.asList("abc", "c"), getMatchingPaths("c", treeId));
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
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setFilter(PathSuffixFilter.create(suffixFilter));
			tw.setRecursive(recursiveWalk);
			tw.addTree(treeId);

			List<String> paths = new ArrayList<>();
			while (tw.next())
				paths.add(tw.getPathString());
			return paths;
		}
	}

}
