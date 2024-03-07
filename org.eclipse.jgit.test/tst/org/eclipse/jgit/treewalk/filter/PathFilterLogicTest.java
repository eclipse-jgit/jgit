/*
 * Copyright (C) 2017 Magnus Vigerlöf (magnus.vigerlof@gmail.com) and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class PathFilterLogicTest extends RepositoryTestCase {

	private ObjectId treeId;

	@Before
	public void setup() throws IOException {
		String[] paths = new String[] {
				"a.txt",
				"sub1.txt",
				"sub1/suba/a.txt",
				"sub1/subb/b.txt",
				"sub2/suba/a.txt"
		};
		treeId = createTree(paths);
	}

	@Test
	public void testSinglePath() throws IOException {
		List<String> expected = Arrays.asList("sub1/suba/a.txt",
				"sub1/subb/b.txt");

		TreeFilter tf = PathFilter.create("sub1");
		List<String> paths = getMatchingPaths(treeId, tf);

		assertEquals(expected, paths);
	}

	@Test
	public void testSingleSubPath() throws IOException {
		List<String> expected = Collections.singletonList("sub1/suba/a.txt");

		TreeFilter tf = PathFilter.create("sub1/suba");
		List<String> paths = getMatchingPaths(treeId, tf);

		assertEquals(expected, paths);
	}

	@Test
	public void testSinglePathNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt",
				"sub2/suba/a.txt");

		TreeFilter tf = PathFilter.create("sub1").negate();
		List<String> paths = getMatchingPaths(treeId, tf);

		assertEquals(expected, paths);
	}

	@Test
	public void testSingleSubPathNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt",
				"sub1/subb/b.txt", "sub2/suba/a.txt");

		TreeFilter tf = PathFilter.create("sub1/suba").negate();
		List<String> paths = getMatchingPaths(treeId, tf);

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiTwoPath() throws IOException {
		List<String> expected = Arrays.asList("sub1/suba/a.txt",
				"sub1/subb/b.txt", "sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub2")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiThreePath() throws IOException {
		List<String> expected = Arrays.asList("sub1.txt", "sub1/suba/a.txt",
				"sub1/subb/b.txt", "sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub2"), PathFilter.create("sub1.txt")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiTwoSubPath() throws IOException {
		List<String> expected = Arrays.asList("sub1/subb/b.txt",
				"sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1/subb"),
				PathFilter.create("sub2/suba")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiTwoMixSubPath() throws IOException {
		List<String> expected = Arrays.asList("sub1/subb/b.txt",
				"sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1/subb"),
				PathFilter.create("sub2")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiTwoMixSubPathNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt",
				"sub1/suba/a.txt", "sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1").negate(),
				PathFilter.create("sub1/suba")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testOrMultiThreeMixSubPathNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt",
				"sub1/suba/a.txt", "sub2/suba/a.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1").negate(),
				PathFilter.create("sub1/suba"), PathFilter.create("no/path")};
		List<String> paths = getMatchingPaths(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testPatternParentFileMatch() throws IOException {
		List<String> expected = Collections.emptyList();

		TreeFilter tf = PathFilter.create("a.txt/test/path");
		List<String> paths = getMatchingPaths(treeId, tf);

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiPath() throws IOException {
		List<String> expected = Collections.emptyList();

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub2")};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiPathNegate() throws IOException {
		List<String> expected = Arrays.asList("sub1/suba/a.txt",
				"sub1/subb/b.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub2").negate()};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiSubPathDualNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt",
				"sub1/subb/b.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1/suba").negate(),
				PathFilter.create("sub2").negate()};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiSubPath() throws IOException {
		List<String> expected = Collections.emptyList();

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub2/suba")};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiSubPathNegate() throws IOException {
		List<String> expected = Collections.singletonList("sub1/subb/b.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1"),
				PathFilter.create("sub1/suba").negate()};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testAndMultiThreeSubPathNegate() throws IOException {
		List<String> expected = Collections.singletonList("sub1/subb/b.txt");

		TreeFilter[] tf = new TreeFilter[]{PathFilter.create("sub1"),
				PathFilter.create("sub1/suba").negate(),
				PathFilter.create("no/path").negate()};
		List<String> paths = getMatchingPaths(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testTopAndMultiPathDualNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1").negate(),
				PathFilter.create("sub2").negate()};
		List<String> paths = getMatchingPathsFlat(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testTopAndMultiSubPathDualNegate() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt", "sub1");

		// Filter on 'sub1/suba' is kind of silly for a non-recursive walk.
		// The result is interesting though as the 'sub1' path should be
		// returned, due to the fact that there may be hits once the pattern
		// is tested with one of the leaf paths.
		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1/suba").negate(),
				PathFilter.create("sub2").negate()};
		List<String> paths = getMatchingPathsFlat(treeId, AndTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testTopOrMultiSubPathGetPathsBestEffort() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a"));

		TreeFilter[] tf = new TreeFilter[] { PathFilter.create("a/b"),
				PathFilter.create("a") };

		Optional<Set<byte[]>> effectivePaths = OrTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopOrMultiSubPathGetPathsBestEffortToNarrowScope() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a", "b"));

		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("b");

		PathFilter p3 = PathFilter.create("a/b");
		PathFilter p4 = PathFilter.create("b/c");

		PathFilter p5 = PathFilter.create("a/b/c");
		PathFilter p6 = PathFilter.create("b/c/d");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)),
				PathFilterGroup.create(List.of(p5, p6)) };

		Optional<Set<byte[]>> effectivePaths = OrTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopOrMultiSubPathGetPathsBestEffortOmitNarrowScope() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a"));

		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("a/b");

		PathFilter p3 = PathFilter.create("a");
		PathFilter p4 = PathFilter.create("a/b/c");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)) };

		Optional<Set<byte[]>> effectivePaths = OrTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopOrMultiSubPathGetPathsBestEffortWithPathOutOfScope() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a", "f"));

		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("a/b");

		PathFilter p3 = PathFilter.create("a");
		PathFilter p4 = PathFilter.create("f");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)) };

		Optional<Set<byte[]>> effectivePaths = OrTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopOrMultiSubPathGetPathsBestEffortWithNonPathFilter() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a"));

		PathFilter p1 = PathFilter.create("a");

		TreeFilter[] tf = new TreeFilter[] { p1, TreeFilter.ANY_DIFF };

		Optional<Set<byte[]>> effectivePaths = OrTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopAndMultiSubPathGetPathsBestEffort() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a/b"));

		TreeFilter[] tf = new TreeFilter[] { PathFilter.create("a/b"),
				PathFilter.create("a") };

		Optional<Set<byte[]>> effectivePaths = AndTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopAndMultiSubPathGetPathsBestEffortToNarrowScope() {
		Set<byte[]> expected = PathsCalculator
				.encodePaths(Set.of("a/b/c", "b/c/d"));

		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("b");

		PathFilter p3 = PathFilter.create("a/b");
		PathFilter p4 = PathFilter.create("b/c");

		PathFilter p5 = PathFilter.create("a/b/c");
		PathFilter p6 = PathFilter.create("b/c/d");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)),
				PathFilterGroup.create(List.of(p5, p6)) };

		Optional<Set<byte[]>> effectivePaths = AndTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopAndMultiSubPathGetPathsBestEffortOmitNarrowScope() {
		Set<byte[]> expected = PathsCalculator.encodePaths(Set.of("a"));

		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("a/b");

		PathFilter p3 = PathFilter.create("a");
		PathFilter p4 = PathFilter.create("a/b/c");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)) };

		Optional<Set<byte[]>> effectivePaths = AndTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isPresent());

		Set<byte[]> actual = effectivePaths.get();

		assertEquals(expected.size(), actual.size());
		for (byte[] actualPath : actual) {
			boolean findMatch = false;
			for (byte[] expectedPath : expected) {
				if (Arrays.equals(actualPath, expectedPath)) {
					findMatch = true;
				}
			}
			assertTrue(findMatch);
		}
	}

	@Test
	public void testTopAndMultiSubPathGetPathsBestEffortWithPathOutOfScope() {
		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("a/b");

		PathFilter p3 = PathFilter.create("a");
		PathFilter p4 = PathFilter.create("f");

		TreeFilter[] tf = new TreeFilter[] {
				PathFilterGroup.create(List.of(p1, p2)),
				PathFilterGroup.create(List.of(p3, p4)) };

		Optional<Set<byte[]>> effectivePaths = AndTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isEmpty());
	}

	@Test
	public void testTopAndMultiSubPathGetPathsBestEffortWithNonPathFilter() {
		PathFilter p1 = PathFilter.create("a");

		TreeFilter[] tf = new TreeFilter[] { p1, TreeFilter.ANY_DIFF };

		Optional<Set<byte[]>> effectivePaths = AndTreeFilter.create(tf)
				.getPathsBestEffort();
		assertTrue(effectivePaths.isEmpty());
	}

	@Test
	public void testTopOrMultiPathDual() throws IOException {
		List<String> expected = Arrays.asList("sub1.txt", "sub2");

		TreeFilter[] tf = new TreeFilter[] {PathFilter.create("sub1.txt"),
				PathFilter.create("sub2")};
		List<String> paths = getMatchingPathsFlat(treeId, OrTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	@Test
	public void testTopNotPath() throws IOException {
		List<String> expected = Arrays.asList("a.txt", "sub1.txt", "sub2");

		TreeFilter tf = PathFilter.create("sub1");
		List<String> paths = getMatchingPathsFlat(treeId, NotTreeFilter.create(tf));

		assertEquals(expected, paths);
	}

	private List<String> getMatchingPaths(final ObjectId objId,
			TreeFilter tf) throws IOException {
		return getMatchingPaths(objId, tf, true);
	}

	private List<String> getMatchingPathsFlat(final ObjectId objId,
			TreeFilter tf) throws IOException {
		return getMatchingPaths(objId, tf, false);
	}

	private List<String> getMatchingPaths(final ObjectId objId,
			TreeFilter tf, boolean recursive) throws IOException {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setFilter(tf);
			tw.setRecursive(recursive);
			tw.addTree(objId);

			List<String> paths = new ArrayList<>();
			while (tw.next()) {
				paths.add(tw.getPathString());
			}
			return paths;
		}
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
		final ObjectId objId = dc.writeTree(odi);
		odi.flush();
		return objId;
	}
}

