/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class NameConflictTreeWalkTest extends RepositoryTestCase {
	private static final FileMode TREE = FileMode.TREE;

	private static final FileMode SYMLINK = FileMode.SYMLINK;

	private static final FileMode MISSING = FileMode.MISSING;

	private static final FileMode REGULAR_FILE = FileMode.REGULAR_FILE;

	private static final FileMode EXECUTABLE_FILE = FileMode.EXECUTABLE_FILE;

	@Test
	public void testNoDF_NoGap() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();

			b0.add(createEntry("a", REGULAR_FILE));
			b0.add(createEntry("a.b", EXECUTABLE_FILE));
			b1.add(createEntry("a/b", REGULAR_FILE));
			b0.add(createEntry("a0b", SYMLINK));

			b0.finish();
			b1.finish();
			assertEquals(3, tree0.getEntryCount());
			assertEquals(1, tree1.getEntryCount());
		}

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("a", REGULAR_FILE, MISSING, tw);
			assertModes("a.b", EXECUTABLE_FILE, MISSING, tw);
			assertModes("a", MISSING, TREE, tw);
			tw.enterSubtree();
			assertModes("a/b", MISSING, REGULAR_FILE, tw);
			assertModes("a0b", SYMLINK, MISSING, tw);
		}
	}

	@Test
	public void testDF_NoGap() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();

			b0.add(createEntry("a", REGULAR_FILE));
			b0.add(createEntry("a.b", EXECUTABLE_FILE));
			b1.add(createEntry("a/b", REGULAR_FILE));
			b0.add(createEntry("a0b", SYMLINK));

			b0.finish();
			b1.finish();
			assertEquals(3, tree0.getEntryCount());
			assertEquals(1, tree1.getEntryCount());
		}

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("a", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			assertTrue(tw.isSubtree());
			tw.enterSubtree();
			assertModes("a/b", MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("a.b", EXECUTABLE_FILE, MISSING, tw);
			assertFalse(tw.isDirectoryFileConflict());
			assertModes("a0b", SYMLINK, MISSING, tw);
			assertFalse(tw.isDirectoryFileConflict());
		}
	}

	@Test
	public void testDF_GapByOne() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();

			b0.add(createEntry("a", REGULAR_FILE));
			b0.add(createEntry("a.b", EXECUTABLE_FILE));
			b1.add(createEntry("a.b", EXECUTABLE_FILE));
			b1.add(createEntry("a/b", REGULAR_FILE));
			b0.add(createEntry("a0b", SYMLINK));

			b0.finish();
			b1.finish();
			assertEquals(3, tree0.getEntryCount());
			assertEquals(2, tree1.getEntryCount());
		}

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("a", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("a/b", MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("a.b", EXECUTABLE_FILE, EXECUTABLE_FILE, tw);
			assertFalse(tw.isDirectoryFileConflict());
			assertModes("a0b", SYMLINK, MISSING, tw);
			assertFalse(tw.isDirectoryFileConflict());
		}
	}

	/**
	 * The test reproduces https://bugs.eclipse.org/bugs/show_bug.cgi?id=535919.
	 */
	@Test
	public void tesdDF_LastItemsInTreeHasDFConflictAndSpecialNames()
			throws Exception {

		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();

		final DirCacheBuilder b0 = tree0.builder();
		final DirCacheBuilder b1 = tree1.builder();
		// The tree0 has the following order in git:
		//     subtree, subtree-0
		b0.add(createEntry("subtree", REGULAR_FILE));
		b0.add(createEntry("subtree-0", REGULAR_FILE));
		// The tree1 has the following order in git:
		//     subtree-0, subtree/file
		b1.add(createEntry("subtree/file", REGULAR_FILE));
		b1.add(createEntry("subtree-0", REGULAR_FILE));

		b0.finish();
		b1.finish();

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("subtree", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("subtree/file", MISSING, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			// isDirectoryFileConflict is true, because the conflict is detected
			// on parent.
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("subtree-0", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
			assertFalse(tw.next());
		}
	}

	/**
	 * The test reproduces https://bugs.eclipse.org/bugs/show_bug.cgi?id=535919.
	 */
	@Test
	public void tesdDF_LastItemsInTreeHasDFConflictAndSpecialNames2()
			throws Exception {

		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();

		final DirCacheBuilder b0 = tree0.builder();
		final DirCacheBuilder b1 = tree1.builder();
		// The tree0 has the following order in git:
		//     subtree-0, subtree/file
		b0.add(createEntry("subtree/file", REGULAR_FILE));
		b0.add(createEntry("subtree-0", REGULAR_FILE));
		// The tree1 has the following order in git:
		//     subtree, subtree-0
		b1.add(createEntry("subtree", REGULAR_FILE));
		b1.add(createEntry("subtree-0", REGULAR_FILE));

		b0.finish();
		b1.finish();

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("subtree", TREE, REGULAR_FILE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("subtree/file", REGULAR_FILE, MISSING, tw);
			assertFalse(tw.isSubtree());
			// isDirectoryFileConflict is true, because the conflict is detected
			// on parent.
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("subtree-0", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
			assertFalse(tw.next());
		}
	}

	@Test
	public void tesdDF_NonLastItemsInTreeHasDFConflictAndSpecialNames()
			throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();

		final DirCacheBuilder b0 = tree0.builder();
		final DirCacheBuilder b1 = tree1.builder();
		b0.add(createEntry("subtree", REGULAR_FILE));
		b0.add(createEntry("subtree-0", REGULAR_FILE));
		b0.add(createEntry("x", REGULAR_FILE));

		b1.add(createEntry("subtree/file", REGULAR_FILE));
		b1.add(createEntry("subtree-0", REGULAR_FILE));
		b1.add(createEntry("x", REGULAR_FILE));

		b0.finish();
		b1.finish();

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("subtree", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("subtree/file", MISSING, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			// isDirectoryFileConflict is true, because the conflict is detected
			// on parent.
			// see JavaDoc for isDirectoryFileConflict for details
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("subtree-0", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
			assertModes("x", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
			assertFalse(tw.next());
		}
	}

	@Test
	public void tesdDF_NoSpecialNames() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();

		final DirCacheBuilder b0 = tree0.builder();
		final DirCacheBuilder b1 = tree1.builder();
		// In this test both trees (tree0 and tree1) have exactly the same order
		// of entries:
		//     subtree, xubtree-0
		b0.add(createEntry("subtree", REGULAR_FILE));
		b0.add(createEntry("xubtree-0", REGULAR_FILE));

		b1.add(createEntry("subtree/file", REGULAR_FILE));
		b1.add(createEntry("xubtree-0", REGULAR_FILE));

		b0.finish();
		b1.finish();

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("subtree", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("subtree/file", MISSING, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			// isDirectoryFileConflict is true, because the conflict is detected
			// on parent.
			// see JavaDoc for isDirectoryFileConflict for details
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("xubtree-0", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
			assertFalse(tw.next());
		}
	}

	@Test
	public void testDF_specialFileNames() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		final DirCache tree2 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();
			final DirCacheBuilder b2 = tree2.builder();

			b0.add(createEntry("gradle.properties", REGULAR_FILE));
			b0.add(createEntry("gradle/nested_file.txt", REGULAR_FILE));

			b1.add(createEntry("gradle.properties", REGULAR_FILE));

			b2.add(createEntry("gradle", REGULAR_FILE));
			b2.add(createEntry("gradle.properties", REGULAR_FILE));

			b0.finish();
			b1.finish();
			b2.finish();
			assertEquals(2, tree0.getEntryCount());
			assertEquals(1, tree1.getEntryCount());
			assertEquals(2, tree2.getEntryCount());
		}

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));
			tw.addTree(new DirCacheIterator(tree2));

			assertModes("gradle", TREE, MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("gradle/nested_file.txt", REGULAR_FILE, MISSING,
					MISSING, tw);
			assertFalse(tw.isSubtree());
			// isDirectoryFileConflict is true, because the conflict is detected
			// on parent.
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("gradle.properties", REGULAR_FILE, REGULAR_FILE,
					REGULAR_FILE, tw);
			assertFalse(tw.isSubtree());
			assertFalse(tw.isDirectoryFileConflict());
		}
	}

	@Test
	public void testDF_SkipsSeenSubtree() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();

			b0.add(createEntry("a", REGULAR_FILE));
			b1.add(createEntry("a.b", EXECUTABLE_FILE));
			b1.add(createEntry("a/b", REGULAR_FILE));
			b0.add(createEntry("a0b", SYMLINK));
			b1.add(createEntry("a0b", SYMLINK));

			b0.finish();
			b1.finish();
			assertEquals(2, tree0.getEntryCount());
			assertEquals(3, tree1.getEntryCount());
		}

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("a", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("a/b", MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("a.b", MISSING, EXECUTABLE_FILE, tw);
			assertFalse(tw.isDirectoryFileConflict());
			assertModes("a0b", SYMLINK, SYMLINK, tw);
			assertFalse(tw.isDirectoryFileConflict());
		}
	}

	@Test
	public void testDF_DetectConflict() throws Exception {
		final DirCache tree0 = db.readDirCache();
		final DirCache tree1 = db.readDirCache();
		{
			final DirCacheBuilder b0 = tree0.builder();
			final DirCacheBuilder b1 = tree1.builder();

			b0.add(createEntry("0", REGULAR_FILE));
			b0.add(createEntry("a", REGULAR_FILE));
			b1.add(createEntry("0", REGULAR_FILE));
			b1.add(createEntry("a.b", REGULAR_FILE));
			b1.add(createEntry("a/b", REGULAR_FILE));
			b1.add(createEntry("a/c/e", REGULAR_FILE));

			b0.finish();
			b1.finish();
			assertEquals(2, tree0.getEntryCount());
			assertEquals(4, tree1.getEntryCount());
		}

		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(new DirCacheIterator(tree0));
			tw.addTree(new DirCacheIterator(tree1));

			assertModes("0", REGULAR_FILE, REGULAR_FILE, tw);
			assertFalse(tw.isDirectoryFileConflict());
			assertModes("a", REGULAR_FILE, TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("a/b", MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			assertModes("a/c", MISSING, TREE, tw);
			assertTrue(tw.isDirectoryFileConflict());
			tw.enterSubtree();
			assertModes("a/c/e", MISSING, REGULAR_FILE, tw);
			assertTrue(tw.isDirectoryFileConflict());

			assertModes("a.b", MISSING, REGULAR_FILE, tw);
			assertFalse(tw.isDirectoryFileConflict());
		}
	}

	private static void assertModes(String path, FileMode mode0, FileMode mode1,
			TreeWalk tw) throws Exception {
		assertTrue("has " + path, tw.next());
		assertEquals(path, tw.getPathString());
		assertEquals(mode0, tw.getFileMode(0));
		assertEquals(mode1, tw.getFileMode(1));
	}

	private static void assertModes(String path, FileMode mode0, FileMode mode1,
			FileMode mode2, TreeWalk tw) throws Exception {
		assertTrue("has " + path, tw.next());
		assertEquals(path, tw.getPathString());
		if (tw.getFileMode(0) != FileMode.MISSING) {
			assertEquals(path, TreeWalk.pathOf(tw.trees[0]));
		}
		if (tw.getFileMode(1) != FileMode.MISSING) {
			assertEquals(path, TreeWalk.pathOf(tw.trees[1]));
		}
		if (tw.getFileMode(2) != FileMode.MISSING) {
			assertEquals(path, TreeWalk.pathOf(tw.trees[2]));
		}
		assertEquals(mode0, tw.getFileMode(0));
		assertEquals(mode1, tw.getFileMode(1));
		assertEquals(mode2, tw.getFileMode(2));
	}
}
