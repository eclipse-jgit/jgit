/*
 * Copyright (C) 2010, 2020 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;
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

	private TestRepository<Repository> testDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository<>(db);
		df = new DiffFormatter(DisabledOutputStream.INSTANCE);
		df.setRepository(db);
		df.setAbbreviationLength(8);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (df != null) {
			df.close();
		}
		super.tearDown();
	}

	@Test
	public void testDefaultRenameDetectorSettings() throws Exception {
		RenameDetector rd = df.getRenameDetector();
		assertNull(rd);
		df.setDetectRenames(true);
		rd = df.getRenameDetector();
		assertNotNull(rd);
		assertEquals(400, rd.getRenameLimit());
		assertEquals(60, rd.getRenameScore());
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
				GITLINK, REGULAR_FILE);

		DiffEntry ad = DiffEntry.delete(PATH_A, aId);
		ad.oldMode = FileMode.GITLINK;
		DiffEntry abcd = DiffEntry.add(PATH_A, bId);

		DiffEntry mod = DiffEntry.pair(ChangeType.MODIFY, ad, abcd, 0);

		FileHeader fh = df.toFileHeader(mod);

		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(1, fh.getHunks().size());

		HunkHeader hh = fh.getHunks().get(0);
		assertEquals(1, hh.toEditList().size());
	}

	@Test
	public void testCreateFileHeader_AddGitLink() throws Exception {
		ObjectId adId = blob("a\nd\n");
		DiffEntry ent = DiffEntry.add("FOO", adId);
		ent.newMode = FileMode.GITLINK;
		FileHeader fh = df.toFileHeader(ent);

		String diffHeader = "diff --git a/FOO b/FOO\n" //
				+ "new file mode " + GITLINK + "\n"
				+ "index "
				+ ObjectId.zeroId().abbreviate(8).name()
				+ ".."
				+ adId.abbreviate(8).name() + "\n" //
				+ "--- /dev/null\n"//
				+ "+++ b/FOO\n";
		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(1, fh.getHunks().size());
		HunkHeader hh = fh.getHunks().get(0);

		EditList el = hh.toEditList();
		assertEquals(1, el.size());

		Edit e = el.get(0);
		assertEquals(0, e.getBeginA());
		assertEquals(0, e.getEndA());
		assertEquals(0, e.getBeginB());
		assertEquals(1, e.getEndB());
		assertEquals(Edit.Type.INSERT, e.getType());
	}

	@Test
	public void testCreateFileHeader_DeleteGitLink() throws Exception {
		ObjectId adId = blob("a\nd\n");
		DiffEntry ent = DiffEntry.delete("FOO", adId);
		ent.oldMode = FileMode.GITLINK;
		FileHeader fh = df.toFileHeader(ent);

		String diffHeader = "diff --git a/FOO b/FOO\n" //
				+ "deleted file mode " + GITLINK + "\n"
				+ "index "
				+ adId.abbreviate(8).name()
				+ ".."
				+ ObjectId.zeroId().abbreviate(8).name() + "\n" //
				+ "--- a/FOO\n"//
				+ "+++ /dev/null\n";
		assertEquals(diffHeader, RawParseUtils.decode(fh.getBuffer()));

		assertEquals(1, fh.getHunks().size());
		HunkHeader hh = fh.getHunks().get(0);

		EditList el = hh.toEditList();
		assertEquals(1, el.size());

		Edit e = el.get(0);
		assertEquals(0, e.getBeginA());
		assertEquals(1, e.getEndA());
		assertEquals(0, e.getBeginB());
		assertEquals(0, e.getEndB());
		assertEquals(Edit.Type.DELETE, e.getType());
	}

	@Test
	public void testCreateFileHeaderWithoutIndexLine() throws Exception {
		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldMode = FileMode.REGULAR_FILE;
		m.newMode = FileMode.EXECUTABLE_FILE;

		FileHeader fh = df.toFileHeader(m);
		String expected = DIFF + "a/src/a b/src/a\n" + //
				"old mode 100644\n" + //
				"new mode 100755\n";
		assertEquals(expected, fh.getScriptText());
	}

	@Test
	public void testCreateFileHeaderForRenameWithoutContentChange() throws Exception {
		DiffEntry a = DiffEntry.delete(PATH_A, ObjectId.zeroId());
		DiffEntry b = DiffEntry.add(PATH_B, ObjectId.zeroId());
		DiffEntry m = DiffEntry.pair(ChangeType.RENAME, a, b, 100);
		m.oldId = null;
		m.newId = null;

		FileHeader fh = df.toFileHeader(m);
		String expected = DIFF + "a/src/a b/src/b\n" + //
				"similarity index 100%\n" + //
				"rename from src/a\n" + //
				"rename to src/b\n";
		assertEquals(expected, fh.getScriptText());
	}

	@Test
	public void testCreateFileHeaderForRenameModeChange()
			throws Exception {
		DiffEntry a = DiffEntry.delete(PATH_A, ObjectId.zeroId());
		DiffEntry b = DiffEntry.add(PATH_B, ObjectId.zeroId());
		b.oldMode = FileMode.REGULAR_FILE;
		b.newMode = FileMode.EXECUTABLE_FILE;
		DiffEntry m = DiffEntry.pair(ChangeType.RENAME, a, b, 100);
		m.oldId = null;
		m.newId = null;

		FileHeader fh = df.toFileHeader(m);
		//@formatter:off
		String expected = DIFF + "a/src/a b/src/b\n" +
				"old mode 100644\n" +
				"new mode 100755\n" +
				"similarity index 100%\n" +
				"rename from src/a\n" +
				"rename to src/b\n";
		//@formatter:on
		assertEquals(expected, fh.getScriptText());
	}

	@Test
	public void testDiff() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		FileUtils.mkdir(folder);
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(new BufferedOutputStream(os))) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");
			dfmt.setRepository(db);
			dfmt.setPathFilter(PathFilter.create("folder"));
			DirCacheIterator oldTree = new DirCacheIterator(db.readDirCache());
			FileTreeIterator newTree = new FileTreeIterator(db);

			dfmt.format(oldTree, newTree);
			dfmt.flush();

			String actual = os.toString("UTF-8");
			String expected =
					"diff --git a/folder/folder.txt b/folder/folder.txt\n"
					+ "index 0119635..95c4c65 100644\n"
					+ "--- a/folder/folder.txt\n" + "+++ b/folder/folder.txt\n"
					+ "@@ -1 +1 @@\n" + "-folder\n"
					+ "\\ No newline at end of file\n" + "+folder change\n"
					+ "\\ No newline at end of file\n";

			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffRootNullToTree() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		FileUtils.mkdir(folder);
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(new BufferedOutputStream(os))) {
			git.add().addFilepattern(".").call();
			RevCommit commit = git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");

			dfmt.setRepository(db);
			dfmt.setPathFilter(PathFilter.create("folder"));
			dfmt.format(null, commit.getTree().getId());
			dfmt.flush();

			String actual = os.toString("UTF-8");
			String expected = "diff --git a/folder/folder.txt b/folder/folder.txt\n"
					+ "new file mode 100644\n"
					+ "index 0000000..0119635\n"
					+ "--- /dev/null\n"
					+ "+++ b/folder/folder.txt\n"
					+ "@@ -0,0 +1 @@\n"
					+ "+folder\n"
					+ "\\ No newline at end of file\n";

			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffRootTreeToNull() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		FileUtils.mkdir(folder);
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(new BufferedOutputStream(os));) {
			git.add().addFilepattern(".").call();
			RevCommit commit = git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");

			dfmt.setRepository(db);
			dfmt.setPathFilter(PathFilter.create("folder"));
			dfmt.format(commit.getTree().getId(), null);
			dfmt.flush();

			String actual = os.toString("UTF-8");
			String expected = "diff --git a/folder/folder.txt b/folder/folder.txt\n"
					+ "deleted file mode 100644\n"
					+ "index 0119635..0000000\n"
					+ "--- a/folder/folder.txt\n"
					+ "+++ /dev/null\n"
					+ "@@ -1 +0,0 @@\n"
					+ "-folder\n"
					+ "\\ No newline at end of file\n";

			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffNullToNull() throws Exception {
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(new BufferedOutputStream(os))) {
			dfmt.setRepository(db);
			dfmt.format((AnyObjectId) null, null);
			dfmt.flush();

			String actual = os.toString("UTF-8");
			String expected = "";

			assertEquals(expected, actual);
		}
	}

	@Test
	public void testTrackedFileInIgnoredFolderUnchanged()
			throws Exception {
		commitFile("empty/empty/foo", "", "master");
		commitFile(".gitignore", "empty/*", "master");
		try (Git git = new Git(db)) {
			Status status = git.status().call();
			assertTrue(status.isClean());
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(os)) {
			dfmt.setRepository(db);
			dfmt.format(new DirCacheIterator(db.readDirCache()),
					new FileTreeIterator(db));
			dfmt.flush();

			String actual = os.toString("UTF-8");

			assertEquals("", actual);
		}
	}

	@Test
	public void testTrackedFileInIgnoredFolderChanged()
			throws Exception {
		String expectedDiff = "diff --git a/empty/empty/foo b/empty/empty/foo\n"
				+ "index e69de29..5ea2ed4 100644\n" //
				+ "--- a/empty/empty/foo\n" //
				+ "+++ b/empty/empty/foo\n" //
				+ "@@ -0,0 +1 @@\n" //
				+ "+changed\n";

		commitFile("empty/empty/foo", "", "master");
		commitFile(".gitignore", "empty/*", "master");
		try (Git git = new Git(db)) {
			Status status = git.status().call();
			assertTrue(status.isClean());
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(os)) {
			writeTrashFile("empty/empty/foo", "changed\n");
			dfmt.setRepository(db);
			dfmt.format(new DirCacheIterator(db.readDirCache()),
					new FileTreeIterator(db));
			dfmt.flush();

			String actual = os.toString("UTF-8");

			assertEquals(expectedDiff, actual);
		}
	}

	@Test
	public void testDiffAutoCrlfSmallFile() throws Exception {
		String content = "01234\r\n01234\r\n01234\r\n";
		String expectedDiff = "diff --git a/test.txt b/test.txt\n"
				+ "index fe25983..a44a032 100644\n" //
				+ "--- a/test.txt\n" //
				+ "+++ b/test.txt\n" //
				+ "@@ -1,3 +1,4 @@\n" //
				+ " 01234\n" //
				+ "+ABCD\n" //
				+ " 01234\n" //
				+ " 01234\n";
		doAutoCrLfTest(content, expectedDiff);
	}

	@Test
	public void testDiffAutoCrlfMediumFile() throws Exception {
		String content = mediumCrLfString();
		String expectedDiff = "diff --git a/test.txt b/test.txt\n"
				+ "index 215c502..c10f08c 100644\n" //
				+ "--- a/test.txt\n" //
				+ "+++ b/test.txt\n" //
				+ "@@ -1,4 +1,5 @@\n" //
				+ " 01234567\n" //
				+ "+ABCD\n" //
				+ " 01234567\n" //
				+ " 01234567\n" //
				+ " 01234567\n";
		doAutoCrLfTest(content, expectedDiff);
	}

	@Test
	public void testDiffAutoCrlfLargeFile() throws Exception {
		String content = largeCrLfString();
		String expectedDiff = "diff --git a/test.txt b/test.txt\n"
				+ "index 7014942..c0487a7 100644\n" //
				+ "--- a/test.txt\n" //
				+ "+++ b/test.txt\n" //
				+ "@@ -1,4 +1,5 @@\n"
				+ " 012345678901234567890123456789012345678901234567\n"
				+ "+ABCD\n"
				+ " 012345678901234567890123456789012345678901234567\n"
				+ " 012345678901234567890123456789012345678901234567\n"
				+ " 012345678901234567890123456789012345678901234567\n";
		doAutoCrLfTest(content, expectedDiff);
	}

	private void doAutoCrLfTest(String content, String expectedDiff)
			throws Exception {
		FileBasedConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, "true");
		config.save();
		commitFile("test.txt", content, "master");
		// Insert a line into content
		int i = content.indexOf('\n');
		content = content.substring(0, i + 1) + "ABCD\r\n"
				+ content.substring(i + 1);
		writeTrashFile("test.txt", content);
		// Create the patch
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter dfmt = new DiffFormatter(
						new BufferedOutputStream(os))) {
			dfmt.setRepository(db);
			dfmt.format(new DirCacheIterator(db.readDirCache()),
					new FileTreeIterator(db));
			dfmt.flush();

			String actual = os.toString("UTF-8");

			assertEquals(expectedDiff, actual);
		}
	}

	private static String largeCrLfString() {
		String line = "012345678901234567890123456789012345678901234567\r\n";
		StringBuilder builder = new StringBuilder(
				2 * RawText.FIRST_FEW_BYTES);
		while (builder.length() < 2 * RawText.FIRST_FEW_BYTES) {
			builder.append(line);
		}
		return builder.toString();
	}

	private static String mediumCrLfString() {
		// Create a CR-LF string longer than RawText.FIRST_FEW_BYTES whose
		// canonical representation is shorter than RawText.FIRST_FEW_BYTES.
		String line = "01234567\r\n"; // 10 characters
		StringBuilder builder = new StringBuilder(
				RawText.FIRST_FEW_BYTES + line.length());
		while (builder.length() <= RawText.FIRST_FEW_BYTES) {
			builder.append(line);
		}
		return builder.toString();
	}

	private static String makeDiffHeader(String pathA, String pathB,
			ObjectId aId,
			ObjectId bId) {
		String a = aId.abbreviate(8).name();
		String b = bId.abbreviate(8).name();
		return DIFF + "a/" + pathA + " " + "b/" + pathB + "\n" + //
				"index " + a + ".." + b + " " + REGULAR_FILE + "\n" + //
				"--- a/" + pathA + "\n" + //
				"+++ b/" + pathB + "\n";
	}

	private static String makeDiffHeaderModeChange(String pathA, String pathB,
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
