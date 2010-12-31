/*
 * Copyright (C) 2009, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class PathSuffixFilterTest extends RepositoryTestCase {

	@Test
	public void testNonRecursiveFiltering() throws IOException {
		final ObjectInserter odi = db.newObjectInserter();
		final ObjectId aSth = odi.insert(OBJ_BLOB, "a.sth".getBytes());
		final ObjectId aTxt = odi.insert(OBJ_BLOB, "a.txt".getBytes());
		final DirCache dc = db.readDirCache();
		final DirCacheBuilder builder = dc.builder();
		final DirCacheEntry aSthEntry = new DirCacheEntry("a.sth");
		aSthEntry.setFileMode(FileMode.REGULAR_FILE);
		aSthEntry.setObjectId(aSth);
		final DirCacheEntry aTxtEntry = new DirCacheEntry("a.txt");
		aTxtEntry.setFileMode(FileMode.REGULAR_FILE);
		aTxtEntry.setObjectId(aTxt);
		builder.add(aSthEntry);
		builder.add(aTxtEntry);
		builder.finish();
		final ObjectId treeId = dc.writeTree(odi);
		odi.flush();


		final TreeWalk tw = new TreeWalk(db);
		tw.setFilter(PathSuffixFilter.create(".txt"));
		tw.addTree(treeId);

		List<String> paths = new LinkedList<String>();
		while (tw.next()) {
			paths.add(tw.getPathString());
		}

		List<String> expected =  new LinkedList<String>();
		expected.add("a.txt");

		assertEquals(expected, paths);
	}

	@Test
	public void testRecursiveFiltering() throws IOException {
		final ObjectInserter odi = db.newObjectInserter();
		final ObjectId aSth = odi.insert(OBJ_BLOB, "a.sth".getBytes());
		final ObjectId aTxt = odi.insert(OBJ_BLOB, "a.txt".getBytes());
		final ObjectId bSth = odi.insert(OBJ_BLOB, "b.sth".getBytes());
		final ObjectId bTxt = odi.insert(OBJ_BLOB, "b.txt".getBytes());
		final DirCache dc = db.readDirCache();
		final DirCacheBuilder builder = dc.builder();
		final DirCacheEntry aSthEntry = new DirCacheEntry("a.sth");
		aSthEntry.setFileMode(FileMode.REGULAR_FILE);
		aSthEntry.setObjectId(aSth);
		final DirCacheEntry aTxtEntry = new DirCacheEntry("a.txt");
		aTxtEntry.setFileMode(FileMode.REGULAR_FILE);
		aTxtEntry.setObjectId(aTxt);
		builder.add(aSthEntry);
		builder.add(aTxtEntry);
		final DirCacheEntry bSthEntry = new DirCacheEntry("sub/b.sth");
		bSthEntry.setFileMode(FileMode.REGULAR_FILE);
		bSthEntry.setObjectId(bSth);
		final DirCacheEntry bTxtEntry = new DirCacheEntry("sub/b.txt");
		bTxtEntry.setFileMode(FileMode.REGULAR_FILE);
		bTxtEntry.setObjectId(bTxt);
		builder.add(bSthEntry);
		builder.add(bTxtEntry);
		builder.finish();
		final ObjectId treeId = dc.writeTree(odi);
		odi.flush();


		final TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.setFilter(PathSuffixFilter.create(".txt"));
		tw.addTree(treeId);

		List<String> paths = new LinkedList<String>();
		while (tw.next()) {
			paths.add(tw.getPathString());
		}

		List<String> expected =  new LinkedList<String>();
		expected.add("a.txt");
		expected.add("sub/b.txt");

		assertEquals(expected, paths);
	}

}
