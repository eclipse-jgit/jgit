/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.lib.GitIndex.Entry;

public class IndexTreeWalkerTest extends RepositoryTestCase {
	private ArrayList<String> treeOnlyEntriesVisited = new ArrayList<String>();
	private ArrayList<String> bothVisited = new ArrayList<String>();
	private ArrayList<String> indexOnlyEntriesVisited = new ArrayList<String>();

	private class TestIndexTreeVisitor extends AbstractIndexTreeVisitor {
		public void visitEntry(TreeEntry treeEntry, Entry indexEntry, File file) {
			if (treeEntry == null)
				indexOnlyEntriesVisited.add(indexEntry.getName());
			else if (indexEntry == null)
				treeOnlyEntriesVisited.add(treeEntry.getFullName());
			else bothVisited.add(indexEntry.getName());
		}
	}

	/*
	 * Need to think about what I really need to be able to do....
	 *
	 * 1) Visit all entries in index and tree
	 * 2) Get all directories that exist in the index, but not in the tree
	 *    -- I'm pretty sure that I don't need to do the other way around
	 *       because I already
	 */

	public void testTreeOnlyOneLevel() throws IOException {
		GitIndex index = new GitIndex(db);
		Tree tree = new Tree(db);
		tree.addFile("foo");
		tree.addFile("bar");

		new IndexTreeWalker(index, tree, trash, new TestIndexTreeVisitor()).walk();

		assertTrue(treeOnlyEntriesVisited.get(0).equals("bar"));
		assertTrue(treeOnlyEntriesVisited.get(1).equals("foo"));
	}

	public void testIndexOnlyOneLevel() throws IOException {
		GitIndex index = new GitIndex(db);
		Tree tree = new Tree(db);

		index.add(trash, writeTrashFile("foo", "foo"));
		index.add(trash, writeTrashFile("bar", "bar"));
		new IndexTreeWalker(index, tree, trash, new TestIndexTreeVisitor()).walk();

		assertTrue(indexOnlyEntriesVisited.get(0).equals("bar"));
		assertTrue(indexOnlyEntriesVisited.get(1).equals("foo"));
	}

	public void testBoth() throws IOException {
		GitIndex index = new GitIndex(db);
		Tree tree = new Tree(db);

		index.add(trash, writeTrashFile("a", "a"));
		tree.addFile("b/b");
		index.add(trash, writeTrashFile("c", "c"));
		tree.addFile("c");

		new IndexTreeWalker(index, tree, trash, new TestIndexTreeVisitor()).walk();
		assertTrue(indexOnlyEntriesVisited.contains("a"));
		assertTrue(treeOnlyEntriesVisited.contains("b/b"));
		assertTrue(bothVisited.contains("c"));

	}

	public void testIndexOnlySubDirs() throws IOException {
		GitIndex index = new GitIndex(db);
		Tree tree = new Tree(db);

		index.add(trash, writeTrashFile("foo/bar/baz", "foobar"));
		index.add(trash, writeTrashFile("asdf", "asdf"));
		new IndexTreeWalker(index, tree, trash, new TestIndexTreeVisitor()).walk();

		assertEquals("asdf", indexOnlyEntriesVisited.get(0));
		assertEquals("foo/bar/baz", indexOnlyEntriesVisited.get(1));
	}

	public void testLeavingTree() throws IOException {
		GitIndex index = new GitIndex(db);
		index.add(trash, writeTrashFile("foo/bar", "foo/bar"));
		index.add(trash, writeTrashFile("foobar", "foobar"));

		new IndexTreeWalker(index, db.mapTree(index.writeTree()), trash, new AbstractIndexTreeVisitor() {
			@Override
			public void visitEntry(TreeEntry entry, Entry indexEntry, File f) {
				if (entry == null || indexEntry == null)
					fail();
			}

			@Override
			public void finishVisitTree(Tree tree, int i, String curDir)
					throws IOException {
				if (tree.memberCount() == 0)
					fail();
				if (i == 0)
					fail();
			}

		}).walk();
	}
}
