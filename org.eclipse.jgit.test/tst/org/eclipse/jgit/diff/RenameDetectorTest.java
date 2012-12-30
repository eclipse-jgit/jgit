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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class RenameDetectorTest extends RepositoryTestCase {
	private static final String PATH_A = "src/A";
	private static final String PATH_B = "src/B";
	private static final String PATH_H = "src/H";
	private static final String PATH_Q = "src/Q";

	private RenameDetector rd;

	private TestRepository<Repository> testDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository<Repository>(db);
		rd = new RenameDetector(db);
	}

	@Test
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

	@Test
	public void testExactRename_DifferentObjects() throws Exception {
		ObjectId foo = blob("foo");
		ObjectId bar = blob("bar");

		DiffEntry a = DiffEntry.add(PATH_A, foo);
		DiffEntry h = DiffEntry.add(PATH_H, foo);
		DiffEntry q = DiffEntry.delete(PATH_Q, bar);

		rd.add(a);
		rd.add(h);
		rd.add(q);

		List<DiffEntry> entries = rd.compute();
		assertEquals(3, entries.size());
		assertSame(a, entries.get(0));
		assertSame(h, entries.get(1));
		assertSame(q, entries.get(2));
	}

	@Test
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

	@Test
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

	@Test
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

	@Test
	public void testExactRename_PathBreaksTie() throws Exception {
		ObjectId foo = blob("foo");

		DiffEntry a = DiffEntry.add("src/com/foo/a.java", foo);
		DiffEntry b = DiffEntry.delete("src/com/foo/b.java", foo);

		DiffEntry c = DiffEntry.add("c.txt", foo);
		DiffEntry d = DiffEntry.delete("d.txt", foo);
		DiffEntry e = DiffEntry.add("the_e_file.txt", foo);

		// Add out of order to avoid first-match succeeding
		rd.add(a);
		rd.add(d);
		rd.add(e);
		rd.add(b);
		rd.add(c);

		List<DiffEntry> entries = rd.compute();
		assertEquals(3, entries.size());
		assertRename(d, c, 100, entries.get(0));
		assertRename(b, a, 100, entries.get(1));
		assertCopy(d, e, 100, entries.get(2));
	}

	@Test
	public void testExactRename_OneDeleteManyAdds() throws Exception {
		ObjectId foo = blob("foo");

		DiffEntry a = DiffEntry.add("src/com/foo/a.java", foo);
		DiffEntry b = DiffEntry.add("src/com/foo/b.java", foo);
		DiffEntry c = DiffEntry.add("c.txt", foo);

		DiffEntry d = DiffEntry.delete("d.txt", foo);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		List<DiffEntry> entries = rd.compute();
		assertEquals(3, entries.size());
		assertRename(d, c, 100, entries.get(0));
		assertCopy(d, a, 100, entries.get(1));
		assertCopy(d, b, 100, entries.get(2));
	}

	@Test
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

	@Test
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

	@Test
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

	@Test
	public void testInexactRename_NewlinesOnly() throws Exception {
		ObjectId aId = blob("\n\n\n");
		ObjectId bId = blob("\n\n\n\n");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 74, entries.get(0));
	}

	@Test
	public void testInexactRename_SameContentMultipleTimes() throws Exception {
		ObjectId aId = blob("a\na\na\na\n");
		ObjectId bId = blob("a\na\na\n");

		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_Q, bId);

		rd.add(a);
		rd.add(b);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertRename(b, a, 74, entries.get(0));
	}

	@Test
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

	@Test
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

	@Test
	public void testNoRenames_EmptyFile1() throws Exception {
		ObjectId aId = blob("");
		DiffEntry a = DiffEntry.add(PATH_A, aId);

		rd.add(a);

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());
		assertSame(a, entries.get(0));
	}

	@Test
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

	@Test
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

	@Test
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

	@Test
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

	@Test
	public void testBreakModify_BreakAll() throws Exception {
		ObjectId aId = blob("foo");
		ObjectId bId = blob("bar");

		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldId = AbbreviatedObjectId.fromObjectId(aId);
		m.newId = AbbreviatedObjectId.fromObjectId(bId);

		DiffEntry a = DiffEntry.add(PATH_B, aId);

		rd.add(a);
		rd.add(m);

		rd.setBreakScore(101);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertAdd(PATH_A, bId, FileMode.REGULAR_FILE, entries.get(0));
		assertRename(DiffEntry.breakModify(m).get(0), a, 100, entries.get(1));
	}

	@Test
	public void testBreakModify_BreakNone() throws Exception {
		ObjectId aId = blob("foo");
		ObjectId bId = blob("bar");

		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldId = AbbreviatedObjectId.fromObjectId(aId);
		m.newId = AbbreviatedObjectId.fromObjectId(bId);

		DiffEntry a = DiffEntry.add(PATH_B, aId);

		rd.add(a);
		rd.add(m);

		rd.setBreakScore(-1);

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(m, entries.get(0));
		assertSame(a, entries.get(1));
	}

	@Test
	public void testBreakModify_BreakBelowScore() throws Exception {
		ObjectId aId = blob("foo");
		ObjectId bId = blob("bar");

		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldId = AbbreviatedObjectId.fromObjectId(aId);
		m.newId = AbbreviatedObjectId.fromObjectId(bId);

		DiffEntry a = DiffEntry.add(PATH_B, aId);

		rd.add(a);
		rd.add(m);

		rd.setBreakScore(20); // Should break the modify

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertAdd(PATH_A, bId, FileMode.REGULAR_FILE, entries.get(0));
		assertRename(DiffEntry.breakModify(m).get(0), a, 100, entries.get(1));
	}

	@Test
	public void testBreakModify_DontBreakAboveScore() throws Exception {
		ObjectId aId = blob("blah\nblah\nfoo");
		ObjectId bId = blob("blah\nblah\nbar");

		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldId = AbbreviatedObjectId.fromObjectId(aId);
		m.newId = AbbreviatedObjectId.fromObjectId(bId);

		DiffEntry a = DiffEntry.add(PATH_B, aId);

		rd.add(a);
		rd.add(m);

		rd.setBreakScore(20); // Should not break the modify

		List<DiffEntry> entries = rd.compute();
		assertEquals(2, entries.size());
		assertSame(m, entries.get(0));
		assertSame(a, entries.get(1));
	}

	@Test
	public void testBreakModify_RejoinIfUnpaired() throws Exception {
		ObjectId aId = blob("foo");
		ObjectId bId = blob("bar");

		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldId = AbbreviatedObjectId.fromObjectId(aId);
		m.newId = AbbreviatedObjectId.fromObjectId(bId);

		rd.add(m);

		rd.setBreakScore(101); // Ensure m is broken apart

		List<DiffEntry> entries = rd.compute();
		assertEquals(1, entries.size());

		DiffEntry modify = entries.get(0);
		assertEquals(m.oldPath, modify.oldPath);
		assertEquals(m.oldId, modify.oldId);
		assertEquals(m.oldMode, modify.oldMode);
		assertEquals(m.newPath, modify.newPath);
		assertEquals(m.newId, modify.newId);
		assertEquals(m.newMode, modify.newMode);
		assertEquals(m.changeType, modify.changeType);
		assertEquals(0, modify.score);
	}

	@Test
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

	@Test
	public void testRenameLimit() throws Exception {
		ObjectId aId = blob("foo\nbar\nbaz\nblarg\n");
		ObjectId bId = blob("foo\nbar\nbaz\nblah\n");
		DiffEntry a = DiffEntry.add(PATH_A, aId);
		DiffEntry b = DiffEntry.delete(PATH_B, bId);

		ObjectId cId = blob("a\nb\nc\nd\n");
		ObjectId dId = blob("a\nb\nc\n");
		DiffEntry c = DiffEntry.add(PATH_H, cId);
		DiffEntry d = DiffEntry.delete(PATH_Q, dId);

		rd.add(a);
		rd.add(b);
		rd.add(c);
		rd.add(d);

		rd.setRenameLimit(1);

		assertTrue(rd.isOverRenameLimit());

		List<DiffEntry> entries = rd.compute();
		assertEquals(4, entries.size());
		assertSame(a, entries.get(0));
		assertSame(b, entries.get(1));
		assertSame(c, entries.get(2));
		assertSame(d, entries.get(3));
	}

	private ObjectId blob(String content) throws Exception {
		return testDb.blob(content).copy();
	}

	private static void assertRename(DiffEntry o, DiffEntry n, int score,
			DiffEntry rename) {
		assertEquals(ChangeType.RENAME, rename.getChangeType());

		assertEquals(o.getOldPath(), rename.getOldPath());
		assertEquals(n.getNewPath(), rename.getNewPath());

		assertEquals(o.getOldMode(), rename.getOldMode());
		assertEquals(n.getNewMode(), rename.getNewMode());

		assertEquals(o.getOldId(), rename.getOldId());
		assertEquals(n.getNewId(), rename.getNewId());

		assertEquals(score, rename.getScore());
	}

	private static void assertCopy(DiffEntry o, DiffEntry n, int score,
			DiffEntry copy) {
		assertEquals(ChangeType.COPY, copy.getChangeType());

		assertEquals(o.getOldPath(), copy.getOldPath());
		assertEquals(n.getNewPath(), copy.getNewPath());

		assertEquals(o.getOldMode(), copy.getOldMode());
		assertEquals(n.getNewMode(), copy.getNewMode());

		assertEquals(o.getOldId(), copy.getOldId());
		assertEquals(n.getNewId(), copy.getNewId());

		assertEquals(score, copy.getScore());
	}

	private static void assertAdd(String newName, ObjectId newId,
			FileMode newMode, DiffEntry add) {
		assertEquals(DiffEntry.DEV_NULL, add.oldPath);
		assertEquals(DiffEntry.A_ZERO, add.oldId);
		assertEquals(FileMode.MISSING, add.oldMode);
		assertEquals(ChangeType.ADD, add.changeType);
		assertEquals(newName, add.newPath);
		assertEquals(AbbreviatedObjectId.fromObjectId(newId), add.newId);
		assertEquals(newMode, add.newMode);
	}
}
