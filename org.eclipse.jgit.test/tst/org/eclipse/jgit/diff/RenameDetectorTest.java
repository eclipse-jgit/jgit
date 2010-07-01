/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryTestCase;

public class RenameDetectorTest extends RepositoryTestCase {

	RenameDetector rd;

	TestRepository testDb;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository(db);
		rd = new RenameDetector();
	}

	public void testGetEntriesAddDelete() throws Exception {
		ObjectId foo = testDb.blob("foo").copy();

		DiffEntry a = new DiffEntry();
		a.newId = AbbreviatedObjectId.fromObjectId(foo);
		a.newMode = FileMode.REGULAR_FILE;
		a.newName = "some/file.c";
		a.changeType = ChangeType.ADD;

		DiffEntry b = new DiffEntry();
		b.oldId = AbbreviatedObjectId.fromObjectId(foo);
		b.oldMode = FileMode.REGULAR_FILE;
		b.oldName = "some/other_file.c";
		b.changeType = ChangeType.DELETE;

		rd.addDiffEntry(a);
		rd.addDiffEntry(b);

		List<DiffEntry> entries = rd.getEntries();
		assertEquals(1, entries.size());

		DiffEntry rename = entries.get(0);
		assertNotNull(rename);
		assertTrue(foo.equals(rename.newId.toObjectId()));
		assertTrue(foo.equals(rename.oldId.toObjectId()));
		assertEquals(FileMode.REGULAR_FILE, rename.newMode);
		assertEquals(FileMode.REGULAR_FILE, rename.oldMode);
		assertEquals(ChangeType.RENAME, rename.changeType);
		assertEquals("some/file.c", rename.newName);
		assertEquals("some/other_file.c", rename.oldName);
	}

	public void testGetEntriesAddDeleteModify() throws Exception {
		ObjectId foo = testDb.blob("foo").copy();
		ObjectId bar = testDb.blob("bar").copy();

		DiffEntry a = new DiffEntry();
		a.newId = AbbreviatedObjectId.fromObjectId(foo);
		a.newMode = FileMode.REGULAR_FILE;
		a.newName = "some/file.c";
		a.changeType = ChangeType.ADD;

		DiffEntry b = new DiffEntry();
		b.oldId = AbbreviatedObjectId.fromObjectId(foo);
		b.oldMode = FileMode.REGULAR_FILE;
		b.oldName = "some/other_file.c";
		b.changeType = ChangeType.DELETE;

		DiffEntry c = new DiffEntry();
		c.newId = c.oldId = AbbreviatedObjectId.fromObjectId(bar);
		c.newMode = c.oldMode = FileMode.REGULAR_FILE;
		c.newName = c.oldName = "some/header.h";
		c.changeType = ChangeType.MODIFY;

		rd.addDiffEntry(a);
		rd.addDiffEntry(b);
		rd.addDiffEntry(c);

		List<DiffEntry> entries = rd.getEntries();
		assertEquals(2, entries.size());

		// The renamed change should be first because the output should be
		// sorted by newName
		DiffEntry rename = entries.get(0);
		assertNotNull(rename);
		assertTrue(foo.equals(rename.newId.toObjectId()));
		assertTrue(foo.equals(rename.oldId.toObjectId()));
		assertEquals(FileMode.REGULAR_FILE, rename.newMode);
		assertEquals(FileMode.REGULAR_FILE, rename.oldMode);
		assertEquals(ChangeType.RENAME, rename.changeType);
		assertEquals("some/file.c", rename.newName);
		assertEquals("some/other_file.c", rename.oldName);

		DiffEntry modify = entries.get(1);
		assertEquals(c, modify);
	}

	public void testGetEntriesMultipleRenames() throws Exception {
		ObjectId foo = testDb.blob("foo").copy();
		ObjectId bar = testDb.blob("bar").copy();

		DiffEntry a = new DiffEntry();
		a.newId = AbbreviatedObjectId.fromObjectId(foo);
		a.newMode = FileMode.REGULAR_FILE;
		a.newName = "some/file.c";
		a.changeType = ChangeType.ADD;

		DiffEntry b = new DiffEntry();
		b.oldId = AbbreviatedObjectId.fromObjectId(foo);
		b.oldMode = FileMode.REGULAR_FILE;
		b.oldName = "some/other_file.c";
		b.changeType = ChangeType.DELETE;

		DiffEntry c = new DiffEntry();
		c.newId = AbbreviatedObjectId.fromObjectId(bar);
		c.newMode = FileMode.REGULAR_FILE;
		c.newName = "README";
		c.changeType = ChangeType.ADD;

		DiffEntry d = new DiffEntry();
		d.oldId = AbbreviatedObjectId.fromObjectId(bar);
		d.oldMode = FileMode.REGULAR_FILE;
		d.oldName = "REEDME";
		d.changeType = ChangeType.DELETE;

		rd.addDiffEntry(a);
		rd.addDiffEntry(b);
		rd.addDiffEntry(c);
		rd.addDiffEntry(d);

		List<DiffEntry> entries = rd.getEntries();
		assertEquals(2, entries.size());

		// The REEDME -> README renamed change should be first because the
		// output should be sorted by newName
		DiffEntry readme = entries.get(0);
		assertNotNull(readme);
		assertTrue(bar.equals(readme.newId.toObjectId()));
		assertTrue(bar.equals(readme.oldId.toObjectId()));
		assertEquals(FileMode.REGULAR_FILE, readme.newMode);
		assertEquals(FileMode.REGULAR_FILE, readme.oldMode);
		assertEquals(ChangeType.RENAME, readme.changeType);
		assertEquals("README", readme.newName);
		assertEquals("REEDME", readme.oldName);

		DiffEntry somefile = entries.get(1);
		assertNotNull(somefile);
		assertTrue(foo.equals(somefile.newId.toObjectId()));
		assertTrue(foo.equals(somefile.oldId.toObjectId()));
		assertEquals(FileMode.REGULAR_FILE, somefile.newMode);
		assertEquals(FileMode.REGULAR_FILE, somefile.oldMode);
		assertEquals(ChangeType.RENAME, somefile.changeType);
		assertEquals("some/file.c", somefile.newName);
		assertEquals("some/other_file.c", somefile.oldName);
	}

}
