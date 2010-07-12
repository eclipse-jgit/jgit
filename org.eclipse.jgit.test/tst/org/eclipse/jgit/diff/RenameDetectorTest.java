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
	private static final String PATH_A = "src/A";
	private static final String PATH_B = "src/B";
	private static final String PATH_H = "src/H";
	private static final String PATH_Q = "src/Q";

	private RenameDetector rd;

	private TestRepository testDb;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository(db);
		rd = new RenameDetector(db);
	}

	public void testExactRename_OneRename() throws Exception {
		ObjectId foo = blob("foo");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_Q, foo);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 100, entries.get(0));
	}

	public void testExactRename_OneRenameOneModify() throws Exception {
		ObjectId foo = blob("foo");
		ObjectId bar = blob("bar");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_Q, foo);

		DiffEntry c = DiffEntry.modify(PATH_H);
		c.newId = c.oldId = AbbreviatedObjectId.fromObjectId(bar);

		rd.add(a);
		rd.add(b);
		rd.add(c);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertRename(b, a, 100, entries.get(0));
		assertSame(c, entries.get(1));
	}

	public void testExactRename_ManyRenames() throws Exception {
		ObjectId foo = blob("foo");
		ObjectId bar = blob("bar");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_Q, foo);

		DiffEntry c = DiffEntry.add(PATH_H, bar);
		DiffEntry d = DiffEntry.delete(PATH_B, bar);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertRename(b, a, 100, entries.get(0));
		assertRename(d, c, 100, entries.get(1));
	}

	public void testExactRename_MultipleIdenticalDeletes() throws Exception {
		ObjectId foo = blob("foo");

		DiffEntry a = DiffEntry.delete(PATH_A, foo);
		DiffEntry b = DiffEntry.delete(PATH_B, foo);

		DiffEntry c = DiffEntry.delete(PATH_H, foo);
		DiffEntry d = DiffEntry.add(PATH_Q, foo);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		// Pairs the add with the first delete added
		List<DiffEntry> entries = rd.compute();
		assertEquals(3, entries.size());
		assertEquals(b, entries.get(0));
		assertEquals(c, entries.get(1));
		assertRename(a, d, 100, entries.get(2));
	}

	public void testInexactRename_OnePair() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 66, entries.get(0));
	}

	public void testInexactRename_OneRenameTwoUnrelatedFiles() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");
		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		ObjectId cId = blob("some\nsort\nof\ntext\n");
		ObjectId dId = blob("completely\nunrelated\ntext\n");
		DiffEntry c = DiffEntry.add(PATH_B, cId);
		DiffEntry d = DiffEntry.delete(PATH_H, dId);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		List<DiffEntry> entries = rd.compute();
		assertEquals(3, entries.size());
		assertRename(b, a, 66, entries.get(0));
		assertSame(c, entries.get(1));
		assertSame(d, entries.get(2));
	}

	public void testInexactRename_LastByteDifferent() throws Exception {
		ObjectId aId = blob("foo\nbar\na");
		ObjectId bId = blob("foo\nbar\nb");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 88, entries.get(0));
	}

	public void testInexactRenames_OnePair2() throws Exception {
		ObjectId aId = blob("ab\nab\nab\nac\nad\nae\n");
		ObjectId bId = blob("ac\nab\nab\nab\naa\na0\na1\n");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);
		rd.setRenameScore(50);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 57, entries.get(0));
	}

	public void testNoRenames_SingleByteFiles() throws Exception {
		ObjectId aId = blob("a");
		ObjectId bId = blob("b");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
	}

	public void testNoRenames_EmptyFile1() throws Exception {
		ObjectId aId = blob("");
		DiffEntry a = DiffEntry.add(PATH_A, aId);

		rd.add(a);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertSame(a, entries.get(0));
	}

	public void testNoRenames_EmptyFile2() throws Exception {
		ObjectId aId = blob("");
		ObjectId bId = blob("blah");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
	}

	public void testNoRenames_SymlinkAndFile() throws Exception {
		ObjectId aId = blob("src/dest");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, aId);
		b.oldMode = FileMode.SYMLINK;

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
	}

	public void testNoRenames_GitlinkAndFile() throws Exception {
		ObjectId aId = blob("src/dest");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, aId);
		b.oldMode = FileMode.GITLINK;

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
	}

	public void testNoRenames_SymlinkAndFileSamePath() throws Exception {
		ObjectId aId = blob("src/dest");

		DiffEntry a = DiffEntry.delete(PATH_A, aId);
		DiffEntry b = DiffEntry.add(PATH_A, aId);
		a.oldMode = FileMode.SYMLINK;

		rd.add(a);
		rd.add(b);

		// Deletes should be first
		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
	}

	public void testSetRenameScore_IllegalArgs() throws Exception {
		try {
			rd.setRenameScore(-1);
			fail();
		} catch (IllegalArgumentException e) {
			// pass
		}

		try {
			rd.setRenameScore(101);
			fail();
		} catch (IllegalArgumentException e) {
			// pass
		}
	}

	public void testRenameLimit() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");
		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		ObjectId cId = blob("a\nb\nc\nd\n");
		ObjectId dId = blob("a\nb\nc\n");
		DiffEntry c = DiffEntry.add(PATH_B, cId);
		DiffEntry d = DiffEntry.delete(PATH_H, dId);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		rd.setRenameLimit(1);

		assertTrue(rd.isOverRenameLimit());
	}

	private ObjectId blob(String content) throws Exception {
		return testDb.blob(content).copy();
	}

	private static void assertRename(DiffEntry o, DiffEntry n, int score,
			DiffEntry rename) {
		assertEquals(ChangeType.RENAME, rename.getChangeType());

		assertEquals(o.getOldName(), rename.getOldName());
		assertEquals(n.getNewName(), rename.getNewName());

		assertEquals(o.getOldMode(), rename.getOldMode());
		assertEquals(n.getNewMode(), rename.getNewMode());

		assertEquals(o.getOldId(), rename.getOldId());
		assertEquals(n.getNewId(), rename.getNewId());

		assertEquals(score, rename.getScore());
	}
}
