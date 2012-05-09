/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RepositoryTestCase;
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
		Git git = new Git(db);
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
		assertEquals(expected.toString(), actual);
	}

	@Test
	public void testDiffCached() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		File folder = new File(db.getWorkTree(), "folder");
		folder.mkdir();
		Git git = new Git(db);
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
		assertEquals(expected.toString(), actual);
	}

	@Test
	public void testDiffTwoCommits() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		File folder = new File(db.getWorkTree(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		Git git = new Git(db);
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
		assertEquals(expected.toString(), actual);
	}

	@Test
	public void testDiffWithPrefixes() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"), "test");
		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();
		write(new File(db.getWorkTree(), "test.txt"), "test change");

		OutputStream out = new ByteArrayOutputStream();
		git.diff().setOutputStream(out).setSourcePrefix("old/")
				.setDestinationPrefix("new/")
				.call();

		String actual = out.toString();
		String expected = "diff --git old/test.txt new/test.txt\n"
				+ "index 30d74d2..4dba797 100644\n" + "--- old/test.txt\n"
				+ "+++ new/test.txt\n" + "@@ -1 +1 @@\n" + "-test\n"
				+ "\\ No newline at end of file\n" + "+test change\n"
				+ "\\ No newline at end of file\n";
		assertEquals(expected.toString(), actual);
	}

	@Test
	public void testDiffWithNegativeLineCount() throws Exception {
		write(new File(db.getWorkTree(), "test.txt"),
				"0\n1\n2\n3\n4\n5\n6\n7\n8\n9");
		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();
		write(new File(db.getWorkTree(), "test.txt"),
				"0\n1\n2\n3\n4a\n5\n6\n7\n8\n9");

		OutputStream out = new ByteArrayOutputStream();
		git.diff().setOutputStream(out).setContextLines(1)
				.call();

		String actual = out.toString();
		String expected = "diff --git a/test.txt b/test.txt\n"
				+ "index f55b5c9..c5ec8fd 100644\n" + "--- a/test.txt\n"
				+ "+++ b/test.txt\n" + "@@ -4,3 +4,3 @@\n" + " 3\n" + "-4\n"
				+ "+4a\n" + " 5\n";
		assertEquals(expected.toString(), actual);
	}

	@Test
	public void testNoOutputStreamSet() throws Exception {
		File file = writeTrashFile("test.txt", "a");
		assertTrue(file.setLastModified(file.lastModified() - 5000));
		Git git = new Git(db);
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

	private AbstractTreeIterator getTreeIterator(String name)
			throws IOException {
		final ObjectId id = db.resolve(name);
		if (id == null)
			throw new IllegalArgumentException(name);
		final CanonicalTreeParser p = new CanonicalTreeParser();
		final ObjectReader or = db.newObjectReader();
		try {
			p.reset(or, new RevWalk(db).parseTree(id));
			return p;
		} finally {
			or.release();
		}
	}
}
