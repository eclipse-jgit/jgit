/*
 * Copyright (C) 2013, Robin Rosenberg <robin.rosenberg@dewire.com>
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

public class InterIndexDiffFilterTest extends LocalDiskRepositoryTestCase {

	private Repository db;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
	}

	@Test
	public void testEmpty() throws IOException {
		DirCache dc1 = DirCache.newInCore();
		DirCache dc2 = DirCache.newInCore();
		TreeWalk tw = new TreeWalk(db);
		tw.addTree(new DirCacheIterator(dc1));
		tw.addTree(new DirCacheIterator(dc2));
		assertFalse(tw.next());
	}

	static final class AddEdit extends PathEdit {

		private final ObjectId data;

		private final long length;

		private boolean assumeValid;

		private FileMode type;

		public AddEdit(String entryPath, FileMode type, ObjectId data,
				long length,
				boolean assumeValid) {
			super(entryPath);
			this.type = type;
			this.data = data;
			this.length = length;
			this.assumeValid = assumeValid;
		}

		@Override
		public void apply(DirCacheEntry ent) {
			ent.setFileMode(type);
			ent.setLength(length);
			ent.setObjectId(data);
			ent.setAssumeValid(assumeValid);
		}
	}

	private ObjectId id(String data) {
		byte[] bytes = data.getBytes();
		return db.newObjectInserter().idFor(Constants.OBJ_BLOB, bytes);
	}

	@Test
	public void testOneOnly() throws IOException {
		DirCache dc1 = DirCache.newInCore();
		DirCache dc2 = DirCache.newInCore();
		DirCacheEditor editor = dc1.editor();
		editor.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, false));
		editor.finish();

		TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.addTree(new DirCacheIterator(dc1));
		tw.addTree(new DirCacheIterator(dc2));
		tw.setFilter(InterIndexDiffFilter.INSTANCE);
		assertTrue(tw.next());
		assertEquals("a/a", tw.getPathString());
		assertFalse(tw.next());
	}

	@Test
	public void testTwoSame() throws IOException {
		DirCache dc1 = DirCache.newInCore();
		DirCache dc2 = DirCache.newInCore();
		DirCacheEditor ed1 = dc1.editor();
		ed1.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, false));
		ed1.finish();
		DirCacheEditor ed2 = dc2.editor();
		ed2.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, false));
		ed2.finish();

		TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.addTree(new DirCacheIterator(dc1));
		tw.addTree(new DirCacheIterator(dc2));
		tw.setFilter(InterIndexDiffFilter.INSTANCE);

		assertFalse(tw.next());
	}

	@Test
	public void testTwoSameDifferByAssumeValid() throws IOException {
		DirCache dc1 = DirCache.newInCore();
		DirCache dc2 = DirCache.newInCore();
		DirCacheEditor ed1 = dc1.editor();
		ed1.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, false));
		ed1.finish();
		DirCacheEditor ed2 = dc2.editor();
		ed2.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, true));
		ed2.finish();

		TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.addTree(new DirCacheIterator(dc1));
		tw.addTree(new DirCacheIterator(dc2));
		tw.setFilter(InterIndexDiffFilter.INSTANCE);

		assertTrue(tw.next());
		assertEquals("a/a", tw.getPathString());
		assertFalse(tw.next());
	}

	@Test
	public void testTwoSameSameAssumeValidDifferentContent()
			throws IOException {
		DirCache dc1 = DirCache.newInCore();
		DirCache dc2 = DirCache.newInCore();
		DirCacheEditor ed1 = dc1.editor();
		ed1.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("a"), 1, true));
		ed1.finish();
		DirCacheEditor ed2 = dc2.editor();
		ed2.add(new AddEdit("a/a", FileMode.REGULAR_FILE, id("b"), 1, true));
		ed2.finish();

		TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.addTree(new DirCacheIterator(dc1));
		tw.addTree(new DirCacheIterator(dc2));
		tw.setFilter(InterIndexDiffFilter.INSTANCE);

		assertFalse(tw.next());
	}
}
