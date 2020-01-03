/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.time.TimeUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

public class DiffCommandTest extends RepositoryTestCase {
	@Test
	public void testDiffModified() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		File folder = new File(db.getWorkTree(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");

			OutputStream out = new ByteArrayOutputStream();
			List<DiffEntry> entries = git.diff().setOutputStream(out).call();
			assertEquals(1, entries.size());
			assertEquals(ChangeType.MODIFY, entries.get(0)
					.getChangeType());
			assertEquals("folder/folder.txt", entries.get(0)
					.getOldPath());
			assertEquals("folder/folder.txt", entries.get(0)
					.getNewPath());

			String actual = out.toString();
			String expected = "diff --git a/folder/folder.txt b/folder/folder.txt\n"
					+ "index 0119635..95c4c65 100644\n"
					+ "--- a/folder/folder.txt\n"
					+ "+++ b/folder/folder.txt\n"
					+ "@@ -1 +1 @@\n"
					+ "-folder\n"
					+ "\\ No newline at end of file\n"
					+ "+folder change\n"
					+ "\\ No newline at end of file\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffCached() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		File folder = new File(db.getWorkTree(), "folder");
		folder.mkdir();
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder");
			git.add().addFilepattern(".").call();

			OutputStream out = new ByteArrayOutputStream();
			List<DiffEntry> entries = git.diff().setOutputStream(out)
					.setCached(true).call();
			assertEquals(1, entries.size());
			assertEquals(ChangeType.ADD, entries.get(0)
					.getChangeType());
			assertEquals("/dev/null", entries.get(0)
					.getOldPath());
			assertEquals("folder/folder.txt", entries.get(0)
					.getNewPath());

			String actual = out.toString();
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
	public void testDiffTwoCommits() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		File folder = new File(db.getWorkTree(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("second commit").call();
			write(new File(folder, "folder.txt"), "second folder change");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("third commit").call();

			// bad filter
			DiffCommand diff = git.diff().setShowNameAndStatusOnly(true)
					.setPathFilter(PathFilter.create("test.txt"))
					.setOldTree(getTreeIterator("HEAD^^"))
					.setNewTree(getTreeIterator("HEAD^"));
			List<DiffEntry> entries = diff.call();
			assertEquals(0, entries.size());

			// no filter, two commits
			OutputStream out = new ByteArrayOutputStream();
			diff = git.diff().setOutputStream(out)
					.setOldTree(getTreeIterator("HEAD^^"))
					.setNewTree(getTreeIterator("HEAD^"));
			entries = diff.call();
			assertEquals(1, entries.size());
			assertEquals(ChangeType.MODIFY, entries.get(0).getChangeType());
			assertEquals("folder/folder.txt", entries.get(0).getOldPath());
			assertEquals("folder/folder.txt", entries.get(0).getNewPath());

			String actual = out.toString();
			String expected = "diff --git a/folder/folder.txt b/folder/folder.txt\n"
					+ "index 0119635..95c4c65 100644\n"
					+ "--- a/folder/folder.txt\n"
					+ "+++ b/folder/folder.txt\n"
					+ "@@ -1 +1 @@\n"
					+ "-folder\n"
					+ "\\ No newline at end of file\n"
					+ "+folder change\n"
					+ "\\ No newline at end of file\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffWithPrefixes() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(db.getWorkTree(), "test.txt"), "test change");

			OutputStream out = new ByteArrayOutputStream();
			git.diff().setOutputStream(out).setSourcePrefix("old/")
					.setDestinationPrefix("new/").call();

			String actual = out.toString();
			String expected = "diff --git old/test.txt new/test.txt\n"
					+ "index 30d74d2..4dba797 100644\n" + "--- old/test.txt\n"
					+ "+++ new/test.txt\n" + "@@ -1 +1 @@\n" + "-test\n"
					+ "\\ No newline at end of file\n" + "+test change\n"
					+ "\\ No newline at end of file\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDiffWithNegativeLineCount() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"),
				"0\n1\n2\n3\n4\n5\n6\n7\n8\n9");
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(db.getWorkTree(), "test.txt"),
					"0\n1\n2\n3\n4a\n5\n6\n7\n8\n9");

			OutputStream out = new ByteArrayOutputStream();
			git.diff().setOutputStream(out).setContextLines(1).call();

			String actual = out.toString();
			String expected = "diff --git a/test.txt b/test.txt\n"
					+ "index f55b5c9..c5ec8fd 100644\n" + "--- a/test.txt\n"
					+ "+++ b/test.txt\n" + "@@ -4,3 +4,3 @@\n" + " 3\n" + "-4\n"
					+ "+4a\n" + " 5\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testNoOutputStreamSet() throws Exception {
		File file = writeTrashFile("test.txt", "a");
		TimeUtil.setLastModifiedWithOffset(file.toPath(), -5000L);
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			write(file, "b");

			List<DiffEntry> diffs = git.diff().call();
			assertNotNull(diffs);
			assertEquals(1, diffs.size());
			DiffEntry diff = diffs.get(0);
			assertEquals(ChangeType.MODIFY, diff.getChangeType());
			assertEquals("test.txt", diff.getOldPath());
			assertEquals("test.txt", diff.getNewPath());
		}
	}

	private AbstractTreeIterator getTreeIterator(String name)
			throws IOException {
		final ObjectId id = db.resolve(name);
		if (id == null)
			throw new IllegalArgumentException(name);
		final CanonicalTreeParser p = new CanonicalTreeParser();
		try (ObjectReader or = db.newObjectReader();
				RevWalk rw = new RevWalk(db)) {
			p.reset(or, rw.parseTree(id));
			return p;
		}
	}
}
