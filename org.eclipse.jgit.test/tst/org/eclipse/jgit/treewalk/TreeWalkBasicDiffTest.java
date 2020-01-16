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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class TreeWalkBasicDiffTest extends RepositoryTestCase {
	@Test
	public void testMissingSubtree_DetectFileAdded_FileModified()
			throws Exception {
		final ObjectId oldTree, newTree, bFileId, cFileId1, cFileId2;
		try (ObjectInserter inserter = db.newObjectInserter()) {
			final ObjectId aFileId = inserter.insert(OBJ_BLOB, encode("a"));
			bFileId = inserter.insert(OBJ_BLOB, encode("b"));
			cFileId1 = inserter.insert(OBJ_BLOB, encode("c-1"));
			cFileId2 = inserter.insert(OBJ_BLOB, encode("c-2"));

			// Create sub-a/empty, sub-c/empty = hello.
			{
				TreeFormatter root = new TreeFormatter();
				{
					TreeFormatter subA = new TreeFormatter();
					subA.append("empty", FileMode.REGULAR_FILE, aFileId);
					root.append("sub-a", FileMode.TREE, inserter.insert(subA));
				}
				{
					TreeFormatter subC = new TreeFormatter();
					subC.append("empty", FileMode.REGULAR_FILE, cFileId1);
					root.append("sub-c", FileMode.TREE, inserter.insert(subC));
				}
				oldTree = inserter.insert(root);
			}

			// Create sub-a/empty, sub-b/empty, sub-c/empty.
			{
				TreeFormatter root = new TreeFormatter();
				{
					TreeFormatter subA = new TreeFormatter();
					subA.append("empty", FileMode.REGULAR_FILE, aFileId);
					root.append("sub-a", FileMode.TREE, inserter.insert(subA));
				}
				{
					TreeFormatter subB = new TreeFormatter();
					subB.append("empty", FileMode.REGULAR_FILE, bFileId);
					root.append("sub-b", FileMode.TREE, inserter.insert(subB));
				}
				{
					TreeFormatter subC = new TreeFormatter();
					subC.append("empty", FileMode.REGULAR_FILE, cFileId2);
					root.append("sub-c", FileMode.TREE, inserter.insert(subC));
				}
				newTree = inserter.insert(root);
			}
			inserter.flush();
		}

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.reset(oldTree, newTree);
			tw.setRecursive(true);
			tw.setFilter(TreeFilter.ANY_DIFF);

			assertTrue(tw.next());
			assertEquals("sub-b/empty", tw.getPathString());
			assertEquals(FileMode.MISSING, tw.getFileMode(0));
			assertEquals(FileMode.REGULAR_FILE, tw.getFileMode(1));
			assertEquals(ObjectId.zeroId(), tw.getObjectId(0));
			assertEquals(bFileId, tw.getObjectId(1));

			assertTrue(tw.next());
			assertEquals("sub-c/empty", tw.getPathString());
			assertEquals(FileMode.REGULAR_FILE, tw.getFileMode(0));
			assertEquals(FileMode.REGULAR_FILE, tw.getFileMode(1));
			assertEquals(cFileId1, tw.getObjectId(0));
			assertEquals(cFileId2, tw.getObjectId(1));

			assertFalse(tw.next());
		}
	}
}
