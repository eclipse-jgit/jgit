/*
 * Copyright (C) 2013, Robin Rosenberg
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class PathFilterGroupTest {

	private TreeFilter filter;

	private Map<String, TreeFilter> singles;

	@Before
	public void setup() {
		// @formatter:off
		String[] paths = new String[] {
				"/a", // never match
				"/a/b", // never match
				"a",
				"b/c",
				"c/d/e",
				"c/d/f",
				"d/e/f/g",
				"d/e/f/g.x"
				};
		// @formatter:on
		filter = PathFilterGroup.createFromStrings(paths);
		singles = new HashMap<>();
		for (String path : paths) {
			singles.put(path, PathFilterGroup.createFromStrings(path));
		}
	}

	@Test
	public void testExact() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertMatches(Sets.of("a"), fakeWalk("a"));
		assertMatches(Sets.of("b/c"), fakeWalk("b/c"));
		assertMatches(Sets.of("c/d/e"), fakeWalk("c/d/e"));
		assertMatches(Sets.of("c/d/f"), fakeWalk("c/d/f"));
		assertMatches(Sets.of("d/e/f/g"), fakeWalk("d/e/f/g"));
		assertMatches(Sets.of("d/e/f/g.x"), fakeWalk("d/e/f/g.x"));
	}

	@Test
	public void testNoMatchButClose() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertNoMatches(fakeWalk("a+"));
		assertNoMatches(fakeWalk("b+/c"));
		assertNoMatches(fakeWalk("c+/d/e"));
		assertNoMatches(fakeWalk("c+/d/f"));
		assertNoMatches(fakeWalk("c/d.a"));
		assertNoMatches(fakeWalk("d+/e/f/g"));
	}

	@Test
	public void testJustCommonPrefixIsNotMatch() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertNoMatches(fakeWalk("b/a"));
		assertNoMatches(fakeWalk("b/d"));
		assertNoMatches(fakeWalk("c/d/a"));
		assertNoMatches(fakeWalk("d/e/e"));
		assertNoMatches(fakeWalk("d/e/f/g.y"));
	}

	@Test
	public void testKeyIsPrefixOfFilter() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertMatches(Sets.of("b/c"), fakeWalkAtSubtree("b"));
		assertMatches(Sets.of("c/d/e", "c/d/f"), fakeWalkAtSubtree("c/d"));
		assertMatches(Sets.of("c/d/e", "c/d/f"), fakeWalkAtSubtree("c"));
		assertMatches(Sets.of("d/e/f/g", "d/e/f/g.x"),
				fakeWalkAtSubtree("d/e/f"));
		assertMatches(Sets.of("d/e/f/g", "d/e/f/g.x"),
				fakeWalkAtSubtree("d/e"));
		assertMatches(Sets.of("d/e/f/g", "d/e/f/g.x"), fakeWalkAtSubtree("d"));

		assertNoMatches(fakeWalk("b"));
		assertNoMatches(fakeWalk("c/d"));
		assertNoMatches(fakeWalk("c"));
		assertNoMatches(fakeWalk("d/e/f"));
		assertNoMatches(fakeWalk("d/e"));
		assertNoMatches(fakeWalk("d"));

	}

	@Test
	public void testFilterIsPrefixOfKey() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertMatches(Sets.of("a"), fakeWalk("a/b"));
		assertMatches(Sets.of("b/c"), fakeWalk("b/c/d"));
		assertMatches(Sets.of("c/d/e"), fakeWalk("c/d/e/f"));
		assertMatches(Sets.of("c/d/f"), fakeWalk("c/d/f/g"));
		assertMatches(Sets.of("d/e/f/g"), fakeWalk("d/e/f/g/h"));
		assertMatches(Sets.of("d/e/f/g"), fakeWalk("d/e/f/g/y"));
		assertMatches(Sets.of("d/e/f/g.x"), fakeWalk("d/e/f/g.x/h"));
	}

	@Test
	public void testLongPaths() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		TreeFilter longPathFilter = PathFilterGroup
				.createFromStrings(
						"tst/org/eclipse/jgit/treewalk/filter/PathFilterGroupTest.java",
						"tst/org/eclipse/jgit/treewalk/filter/PathFilterGroupTest2.java");
		assertFalse(longPathFilter
				.include(fakeWalk("tst/org/eclipse/jgit/treewalk/FileTreeIteratorTest.java")));
		assertFalse(longPathFilter.include(fakeWalk("tst/a-other-in-same")));
		assertFalse(longPathFilter.include(fakeWalk("a-nothing-in-common")));
	}

	@Test
	public void testStopWalk() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		// Obvious
		filter.include(fakeWalk("d/e/f/f"));

		// Obvious
		try {
			filter.include(fakeWalk("de"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}

		// less obvious due to git sorting order
		filter.include(fakeWalk("d-"));

		// less obvious due to git sorting order
		try {
			filter.include(fakeWalk("d0"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}

		// less obvious #2 due to git sorting order
		filter.include(fakeWalk("d/e/f/g/h.txt"));

		// listed before g/y, so can't StopWalk here
		filter.include(fakeWalk("d/e/f/g.y"));
		singles.get("d/e/f/g").include(fakeWalk("d/e/f/g.y"));

		// non-ascii
		try {
			filter.include(fakeWalk("\u00C0"));
			fail("StopWalkException expected");
		} catch (StopWalkException e) {
			// good
		}
	}

	private void assertNoMatches(TreeWalk tw) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		assertMatches(Sets.<String> of(), tw);
	}

	private void assertMatches(Set<String> expect, TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		List<String> actual = new ArrayList<>();
		for (String path : singles.keySet()) {
			if (includes(singles.get(path), tw)) {
				actual.add(path);
			}
		}

		String[] e = expect.toArray(new String[0]);
		String[] a = actual.toArray(new String[0]);
		Arrays.sort(e);
		Arrays.sort(a);
		assertArrayEquals(e, a);

		if (expect.isEmpty()) {
			assertFalse(includes(filter, tw));
		} else {
			assertTrue(includes(filter, tw));
		}
	}

	private static boolean includes(TreeFilter f, TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try {
			return f.include(tw);
		} catch (StopWalkException e) {
			return false;
		}
	}

	TreeWalk fakeWalk(String path) throws IOException {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor dce = dc.editor();
		dce.add(new DirCacheEditor.PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
			}
		});
		dce.finish();

		TreeWalk ret = new TreeWalk((ObjectReader) null);
		ret.reset();
		ret.setRecursive(true);
		ret.addTree(new DirCacheIterator(dc));
		ret.next();
		return ret;
	}

	TreeWalk fakeWalkAtSubtree(String path) throws IOException {
		DirCache dc = DirCache.newInCore();
		DirCacheEditor dce = dc.editor();
		dce.add(new DirCacheEditor.PathEdit(path + "/README") {
			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
			}
		});
		dce.finish();

		TreeWalk ret = new TreeWalk((ObjectReader) null);
		ret.addTree(new DirCacheIterator(dc));
		ret.next();
		while (!path.equals(ret.getPathString())) {
			if (ret.isSubtree()) {
				ret.enterSubtree();
			}
			ret.next();
		}
		return ret;
	}
}
