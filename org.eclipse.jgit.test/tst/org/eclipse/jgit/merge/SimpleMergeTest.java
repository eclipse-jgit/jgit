/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class SimpleMergeTest extends SampleDataRepositoryTestCase {

	@Test
	public void testOurs() throws IOException {
		Merger ourMerger = MergeStrategy.OURS.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals(db.resolve("a^{tree}"), ourMerger.getResultTreeId());
	}

	@Test
	public void testOurs_noRepo() throws IOException {
		try (ObjectInserter ins = db.newObjectInserter()) {
			Merger ourMerger = MergeStrategy.OURS.newMerger(ins, db.getConfig());
			boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
			assertTrue(merge);
			assertEquals(db.resolve("a^{tree}"), ourMerger.getResultTreeId());
		}
	}

	@Test
	public void testTheirs() throws IOException {
		Merger ourMerger = MergeStrategy.THEIRS.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals(db.resolve("c^{tree}"), ourMerger.getResultTreeId());
	}

	@Test
	public void testTheirs_noRepo() throws IOException {
		try (ObjectInserter ins = db.newObjectInserter()) {
			Merger ourMerger = MergeStrategy.THEIRS.newMerger(db);
			boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
			assertTrue(merge);
			assertEquals(db.resolve("c^{tree}"), ourMerger.getResultTreeId());
		}
	}

	@Test
	public void testTrivialTwoWay() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c") });
		assertTrue(merge);
		assertEquals("02ba32d3649e510002c21651936b7077aa75ffa9",ourMerger.getResultTreeId().name());
	}

	@Test
	public void testTrivialTwoWay_disjointhistories() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a"), db.resolve("c~4") });
		assertTrue(merge);
		assertEquals("86265c33b19b2be71bdd7b8cb95823f2743d03a8",ourMerger.getResultTreeId().name());
	}

	@Test
	public void testTrivialTwoWay_ok() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a^0^0^0"), db.resolve("a^0^0^1") });
		assertTrue(merge);
		assertEquals(db.resolve("a^0^0^{tree}"), ourMerger.getResultTreeId());
	}

	@Test
	public void testTrivialTwoWay_noRepo() throws IOException {
		try (ObjectInserter ins = db.newObjectInserter()) {
			Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(ins, db.getConfig());
			boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("a^0^0^0"), db.resolve("a^0^0^1") });
			assertTrue(merge);
			assertEquals(db.resolve("a^0^0^{tree}"), ourMerger.getResultTreeId());
		}
	}

	@Test
	public void testTrivialTwoWay_conflict() throws IOException {
		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { db.resolve("f"), db.resolve("g") });
		assertFalse(merge);
	}

	@Test
	public void testTrivialTwoWay_validSubtreeSort() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("libelf-po/a", FileMode.REGULAR_FILE));
		bTreeBuilder.add(createEntry("libelf/c", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("Makefile", FileMode.REGULAR_FILE));
		oTreeBuilder.add(createEntry("libelf-po/a", FileMode.REGULAR_FILE));
		oTreeBuilder.add(createEntry("libelf/c", FileMode.REGULAR_FILE));

		tTreeBuilder.add(createEntry("libelf-po/a", FileMode.REGULAR_FILE));
		tTreeBuilder
				.add(createEntry("libelf/c", FileMode.REGULAR_FILE, "blah"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertTrue(merge);

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setRecursive(true);
			tw.reset(ourMerger.getResultTreeId());

			assertTrue(tw.next());
			assertEquals("Makefile", tw.getPathString());
			assertCorrectId(treeO, tw);

			assertTrue(tw.next());
			assertEquals("libelf-po/a", tw.getPathString());
			assertCorrectId(treeO, tw);

			assertTrue(tw.next());
			assertEquals("libelf/c", tw.getPathString());
			assertCorrectId(treeT, tw);

			assertFalse(tw.next());
		}
	}

	@Test
	public void testTrivialTwoWay_concurrentSubtreeChange() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		bTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE, "o !"));
		oTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE));

		tTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		tTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE, "t !"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertTrue(merge);

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setRecursive(true);
			tw.reset(ourMerger.getResultTreeId());

			assertTrue(tw.next());
			assertEquals("d/o", tw.getPathString());
			assertCorrectId(treeO, tw);

			assertTrue(tw.next());
			assertEquals("d/t", tw.getPathString());
			assertCorrectId(treeT, tw);

			assertFalse(tw.next());
		}
	}

	@Test
	public void testTrivialTwoWay_conflictSubtreeChange() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		bTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		oTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE, "o !"));

		tTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE, "t !"));
		tTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE, "t !"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testTrivialTwoWay_leftDFconflict1() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		bTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE));

		tTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		tTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE, "t !"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testTrivialTwoWay_rightDFconflict1() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		bTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));
		oTreeBuilder.add(createEntry("d/t", FileMode.REGULAR_FILE, "o !"));

		tTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testTrivialTwoWay_leftDFconflict2() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE, "o !"));

		tTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	@Test
	public void testTrivialTwoWay_rightDFconflict2() throws Exception {
		DirCache treeB = db.readDirCache();
		DirCache treeO = db.readDirCache();
		DirCache treeT = db.readDirCache();

		DirCacheBuilder bTreeBuilder = treeB.builder();
		DirCacheBuilder oTreeBuilder = treeO.builder();
		DirCacheBuilder tTreeBuilder = treeT.builder();

		bTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE));

		oTreeBuilder.add(createEntry("d/o", FileMode.REGULAR_FILE));

		tTreeBuilder.add(createEntry("d", FileMode.REGULAR_FILE, "t !"));

		bTreeBuilder.finish();
		oTreeBuilder.finish();
		tTreeBuilder.finish();

		ObjectInserter ow = db.newObjectInserter();
		ObjectId b = commit(ow, treeB, new ObjectId[] {});
		ObjectId o = commit(ow, treeO, new ObjectId[] { b });
		ObjectId t = commit(ow, treeT, new ObjectId[] { b });

		Merger ourMerger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(db);
		boolean merge = ourMerger.merge(new ObjectId[] { o, t });
		assertFalse(merge);
	}

	private static void assertCorrectId(DirCache treeT, TreeWalk tw) {
		assertEquals(treeT.getEntry(tw.getPathString()).getObjectId(), tw
				.getObjectId(0));
	}

	private static ObjectId commit(ObjectInserter odi, DirCache treeB,
			ObjectId[] parentIds) throws Exception {
		CommitBuilder c = new CommitBuilder();
		c.setTreeId(treeB.writeTree(odi));
		c.setAuthor(new PersonIdent("A U Thor", "a.u.thor", 1L, 0));
		c.setCommitter(c.getAuthor());
		c.setParentIds(parentIds);
		c.setMessage("Tree " + c.getTreeId().name());
		ObjectId id = odi.insert(c);
		odi.flush();
		return id;
	}
}
