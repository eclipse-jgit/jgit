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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.jupiter.api.Test;

public class NameConflictTreeWalkTest extends RepositoryTestCase {
	private static final FileMode TREE = FileMode.TREE;

	private static final FileMode SYMLINK = FileMode.SYMLINK;

	private static final FileMode MISSING = FileMode.MISSING;

	private static final FileMode REGULAR_FILE = FileMode.REGULAR_FILE;

	private static final FileMode EXECUTABLE_FILE = FileMode.EXECUTABLE_FILE;

	@Test
	void testNoDF_NoGap() throws Exception {
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
	void testDF_NoGap() throws Exception {
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
	void testDF_GapByOne() throws Exception {
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

	@Test
	void testDF_specialFileNames() throws Exception {
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
	void testDF_SkipsSeenSubtree() throws Exception {
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
	void testDF_DetectConflict() throws Exception {
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
		assertTrue(tw.next(), "has " + path);
		assertEquals(path, tw.getPathString());
		assertEquals(mode0, tw.getFileMode(0));
		assertEquals(mode1, tw.getFileMode(1));
	}

	private static void assertModes(String path, FileMode mode0, FileMode mode1,
			FileMode mode2, TreeWalk tw) throws Exception {
		assertTrue(tw.next(), "has " + path);
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
