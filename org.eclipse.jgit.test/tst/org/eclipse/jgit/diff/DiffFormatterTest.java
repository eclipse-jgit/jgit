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

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DiffFormatterTest extends RepositoryTestCase {
	private static final String DIFF = "diff --git ";

	private static final String REGULAR_FILE = "100644";

	private static final String GITLINK = "160000";

	private static final String PATH_A = "src/a";

	private static final String PATH_B = "src/b";

	private DiffFormatter df;

	private TestRepository testDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository(db);
		df = new DiffFormatter(DisabledOutputStream.INSTANCE);
		df.setRepository(db);
		df.setAbbreviationLength(8);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (df != null)
			df.release();
		super.tearDown();
	}

	@Test
	public void testCreateFileHeader_Add() throws Exception {
		ObjectId adId = blob("a\nd\n");
		DiffEntry ent = DiffEntry.add("FOO", adId);
		FileHeader fh = df.toFileHeader(ent);

		String diffHeader = "diff --git a/FOO b/FOO\n" //
				+ "new file mode " + REGULAR_FILE + "\n"
				+ "index "
				+ ObjectId.zeroId().abbreviate(8).name()
				+ ".."
				+ adId.abbreviate(8).name() + "\n" //
				+ "--- /dev/null\n"//
				+ "+++ b/FOO\n";
		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(0, fh.getStartOffset());
		assertEquals(fh.getBuffer().length, fh.getEndOffset());
		assertEquals(FileHeader.PatchType.UNIFIED, fh.getPatchType());

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(1, hh.toEditList().size());

		EditList el = hh.toEditList();
		assertEquals(1, el.size());

		Edit e = el.get(0);
		assertEquals(0, e.getBeginA());
		assertEquals(0, e.getEndA());
		assertEquals(0, e.getBeginB());
		assertEquals(2, e.getEndB());
		assertEquals(Edit.Type.INSERT, e.getType());
	}

	@Test
	public void testCreateFileHeader_Delete() throws Exception {
		ObjectId adId = blob("a\nd\n");
		DiffEntry ent = DiffEntry.delete("FOO", adId);
		FileHeader fh = df.toFileHeader(ent);

		String diffHeader = "diff --git a/FOO b/FOO\n" //
				+ "deleted file mode " + REGULAR_FILE + "\n"
				+ "index "
				+ adId.abbreviate(8).name()
				+ ".."
				+ ObjectId.zeroId().abbreviate(8).name() + "\n" //
				+ "--- a/FOO\n"//
				+ "+++ /dev/null\n";
		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(0, fh.getStartOffset());
		assertEquals(fh.getBuffer().length, fh.getEndOffset());
		assertEquals(FileHeader.PatchType.UNIFIED, fh.getPatchType());

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(1, hh.toEditList().size());

		EditList el = hh.toEditList();
		assertEquals(1, el.size());

		Edit e = el.get(0);
		assertEquals(0, e.getBeginA());
		assertEquals(2, e.getEndA());
		assertEquals(0, e.getBeginB());
		assertEquals(0, e.getEndB());
		assertEquals(Edit.Type.DELETE, e.getType());
	}

	@Test
	public void testCreateFileHeader_Modify() throws Exception {
		ObjectId adId = blob("a\nd\n");
		ObjectId abcdId = blob("a\nb\nc\nd\n");

		String diffHeader = makeDiffHeader(PATH_A, PATH_A, adId, abcdId);

		DiffEntry ad = DiffEntry.delete(PATH_A, adId);
		DiffEntry abcd = DiffEntry.add(PATH_A, abcdId);

		DiffEntry mod = DiffEntry.pair(ChangeType.MODIFY, ad, abcd, 0);

		FileHeader fh = df.toFileHeader(mod);

		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));
		assertEquals(0, fh.getStartOffset());
		assertEquals(fh.getBuffer().length, fh.getEndOffset());
		assertEquals(FileHeader.PatchType.UNIFIED, fh.getPatchType());

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(1, hh.toEditList().size());

		EditList el = hh.toEditList();
		assertEquals(1, el.size());

		Edit e = el.get(0);
		assertEquals(1, e.getBeginA());
		assertEquals(1, e.getEndA());
		assertEquals(1, e.getBeginB());
		assertEquals(3, e.getEndB());
		assertEquals(Edit.Type.INSERT, e.getType());
	}

	@Test
	public void testCreateFileHeader_Binary() throws Exception {
		ObjectId adId = blob("a\nd\n");
		ObjectId binId = blob("a\nb\nc\n\0\0\0\0d\n");

		String diffHeader = makeDiffHeader(PATH_A, PATH_B, adId, binId)
				+ "Binary files differ\n";

		DiffEntry ad = DiffEntry.delete(PATH_A, adId);
		DiffEntry abcd = DiffEntry.add(PATH_B, binId);

		DiffEntry mod = DiffEntry.pair(ChangeType.MODIFY, ad, abcd, 0);

		FileHeader fh = df.toFileHeader(mod);

		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));
		assertEquals(FileHeader.PatchType.BINARY, fh.getPatchType());

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(0, hh.toEditList().size());
	}

	@Test
	public void testCreateFileHeader_GitLink() throws Exception {
		ObjectId aId = blob("a\n");
		ObjectId bId = blob("b\n");

		String diffHeader = makeDiffHeaderModeChange(PATH_A, PATH_A, aId, bId,
				GITLINK, REGULAR_FILE)
				+ "-Subproject commit " + aId.name() + "\n";

		DiffEntry ad = DiffEntry.delete(PATH_A, aId);
		ad.oldMode = FileMode.GITLINK;
		DiffEntry abcd = DiffEntry.add(PATH_A, bId);

		DiffEntry mod = DiffEntry.pair(ChangeType.MODIFY, ad, abcd, 0);

		FileHeader fh = df.toFileHeader(mod);

		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(0, hh.toEditList().size());
	}

	@Test
	public void testDiff() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();
		write(new File(folder, "folder.txt"), "folder change");

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		DiffFormatter df = new DiffFormatter(new SafeBufferedOutputStream(os));
		df.setRepository(db);
		df.setPathFilter(PathFilter.create("folder"));
		DirCacheIterator oldTree = new DirCacheIterator(db.readDirCache());
		FileTreeIterator newTree = new FileTreeIterator(db);
		df.format(oldTree, newTree);
		df.flush();

		String actual = os.toString();
		String expected =
 "diff --git a/folder/folder.txt b/folder/folder.txt\n"
				+ "index 0119635..95c4c65 100644\n"
				+ "--- a/folder/folder.txt\n" + "+++ b/folder/folder.txt\n"
				+ "@@ -1 +1 @@\n" + "-folder\n"
				+ "\\ No newline at end of file\n" + "+folder change\n"
				+ "\\ No newline at end of file\n";

		assertEquals(expected.toString(), actual);
	}

	private String makeDiffHeader(String pathA, String pathB, ObjectId aId,
			ObjectId bId) {
		String a = aId.abbreviate(8).name();
		String b = bId.abbreviate(8).name();
		return DIFF + "a/" + pathA + " " + "b/" + pathB + "\n" + //
				"index " + a + ".." + b + " " + REGULAR_FILE + "\n" + //
				"--- a/" + pathA + "\n" + //
				"+++ b/" + pathB + "\n";
	}

	private String makeDiffHeaderModeChange(String pathA, String pathB,
			ObjectId aId, ObjectId bId, String modeA, String modeB) {
		String a = aId.abbreviate(8).name();
		String b = bId.abbreviate(8).name();
		return DIFF + "a/" + pathA + " " + "b/" + pathB + "\n" + //
				"old mode " + modeA + "\n" + //
				"new mode " + modeB + "\n" + //
				"index " + a + ".." + b + "\n" + //
				"--- a/" + pathA + "\n" + //
				"+++ b/" + pathB + "\n";
	}

	private ObjectId blob(String content) throws Exception {
		return testDb.blob(content).copy();
	}
}
