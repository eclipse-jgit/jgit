/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.treewalk;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

public class TreeWalkBasicDiffTest extends RepositoryTestCase {
	@Test
	public void testMissingSubtree_DetectFileAdded_FileModified()
			throws Exception {
		final ObjectInserter inserter = db.newObjectInserter();
		final ObjectId aFileId = inserter.insert(OBJ_BLOB, encode("a"));
		final ObjectId bFileId = inserter.insert(OBJ_BLOB, encode("b"));
		final ObjectId cFileId1 = inserter.insert(OBJ_BLOB, encode("c-1"));
		final ObjectId cFileId2 = inserter.insert(OBJ_BLOB, encode("c-2"));

		// Create sub-a/empty, sub-c/empty = hello.
		final ObjectId oldTree;
		{
			final Tree root = new Tree(db);
			{
				final Tree subA = root.addTree("sub-a");
				subA.addFile("empty").setId(aFileId);
				subA.setId(inserter.insert(OBJ_TREE, subA.format()));
			}
			{
				final Tree subC = root.addTree("sub-c");
				subC.addFile("empty").setId(cFileId1);
				subC.setId(inserter.insert(OBJ_TREE, subC.format()));
			}
			oldTree = inserter.insert(OBJ_TREE, root.format());
		}

		// Create sub-a/empty, sub-b/empty, sub-c/empty.
		final ObjectId newTree;
		{
			final Tree root = new Tree(db);
			{
				final Tree subA = root.addTree("sub-a");
				subA.addFile("empty").setId(aFileId);
				subA.setId(inserter.insert(OBJ_TREE, subA.format()));
			}
			{
				final Tree subB = root.addTree("sub-b");
				subB.addFile("empty").setId(bFileId);
				subB.setId(inserter.insert(OBJ_TREE, subB.format()));
			}
			{
				final Tree subC = root.addTree("sub-c");
				subC.addFile("empty").setId(cFileId2);
				subC.setId(inserter.insert(OBJ_TREE, subC.format()));
			}
			newTree = inserter.insert(OBJ_TREE, root.format());
		}
		inserter.flush();
		inserter.release();

		final TreeWalk tw = new TreeWalk(db);
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
