/*
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import static org.eclipse.jgit.util.FileUtils.RECURSIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import org.eclipse.jgit.api.errors.FilterFailedException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class AddCommandTest extends RepositoryTestCase {

	@Test
	public void testAddNothing() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.add().call();
			fail("Expected IllegalArgumentException");
		} catch (NoFilepatternException e) {
			// expected
		}

	}

	@Test
	public void testAddNonExistingSingleFile() throws GitAPIException {
		try (Git git = new Git(db)) {
			DirCache dc = git.add().addFilepattern("a.txt").call();
			assertEquals(0, dc.getEntryCount());
		}
	}

	@Test
	public void testAddExistingSingleFile() throws IOException, GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testCleanFilter() throws IOException,
			GitAPIException {
		writeTrashFile(".gitattributes", "*.txt filter=tstFilter");
		writeTrashFile("src/a.tmp", "foo");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "foo\n");
		File script = writeTempFile("sed s/o/e/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + slashify(script.getPath()));
			config.save();

			git.add().addFilepattern("src/a.txt").addFilepattern("src/a.tmp")
					.call();

			assertEquals(
					"[src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:fee\n]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testCleanFilterEnvironment()
			throws IOException, GitAPIException {
		writeTrashFile(".gitattributes", "*.txt filter=tstFilter");
		writeTrashFile("src/a.txt", "foo");
		File script = writeTempFile("echo $GIT_DIR; echo 1 >xyz");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + slashify(script.getPath()));
			config.save();
			git.add().addFilepattern("src/a.txt").call();

			String gitDir = db.getDirectory().getAbsolutePath();
			assertEquals("[src/a.txt, mode:100644, content:" + gitDir
					+ "\n]", indexState(CONTENT));
			assertTrue(new File(db.getWorkTree(), "xyz").exists());
		}
	}

	@Test
	public void testMultipleCleanFilter() throws IOException, GitAPIException {
		writeTrashFile(".gitattributes",
				"*.txt filter=tstFilter\n*.tmp filter=tstFilter2");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.tmp", "foo\n");
		writeTrashFile("src/a.txt", "foo\n");
		File script = writeTempFile("sed s/o/e/g");
		File script2 = writeTempFile("sed s/f/x/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + slashify(script.getPath()));
			config.setString("filter", "tstFilter2", "clean",
					"sh " + slashify(script2.getPath()));
			config.save();

			git.add().addFilepattern("src/a.txt").addFilepattern("src/a.tmp")
					.call();

			assertEquals(
					"[src/a.tmp, mode:100644, content:xoo\n][src/a.txt, mode:100644, content:fee\n]",
					indexState(CONTENT));

			// TODO: multiple clean filters for one file???
		}
	}

	/**
	 * The path of an added file name contains ';' and afterwards malicious
	 * commands. Make sure when calling filter commands to properly escape the
	 * filenames
	 *
	 * @throws IOException
	 * @throws GitAPIException
	 */
	@Test
	public void testCommandInjection() throws IOException, GitAPIException {
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("; echo virus", "foo\n");
		File script = writeTempFile("sed s/o/e/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + slashify(script.getPath()) + " %f");
			writeTrashFile(".gitattributes", "* filter=tstFilter");

			git.add().addFilepattern("; echo virus").call();
			// Without proper escaping the content would be "feovirus". The sed
			// command and the "echo virus" would contribute to the content
			assertEquals("[; echo virus, mode:100644, content:fee\n]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testBadCleanFilter() throws IOException, GitAPIException {
		writeTrashFile("a.txt", "foo");
		File script = writeTempFile("sedfoo s/o/e/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + script.getPath());
			config.save();
			writeTrashFile(".gitattributes", "*.txt filter=tstFilter");

			try {
				git.add().addFilepattern("a.txt").call();
				fail("Didn't received the expected exception");
			} catch (FilterFailedException e) {
				assertEquals(127, e.getReturnCode());
			}
		}
	}

	@Test
	public void testBadCleanFilter2() throws IOException, GitAPIException {
		writeTrashFile("a.txt", "foo");
		File script = writeTempFile("sed s/o/e/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"shfoo " + script.getPath());
			config.save();
			writeTrashFile(".gitattributes", "*.txt filter=tstFilter");

			try {
				git.add().addFilepattern("a.txt").call();
				fail("Didn't received the expected exception");
			} catch (FilterFailedException e) {
				assertEquals(127, e.getReturnCode());
			}
		}
	}

	@Test
	public void testCleanFilterReturning12() throws IOException,
			GitAPIException {
		writeTrashFile("a.txt", "foo");
		File script = writeTempFile("exit 12");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "clean",
					"sh " + slashify(script.getPath()));
			config.save();
			writeTrashFile(".gitattributes", "*.txt filter=tstFilter");

			try {
				git.add().addFilepattern("a.txt").call();
				fail("Didn't received the expected exception");
			} catch (FilterFailedException e) {
				assertEquals(12, e.getReturnCode());
			}
		}
	}

	@Test
	public void testNotApplicableFilter() throws IOException, GitAPIException {
		writeTrashFile("a.txt", "foo");
		File script = writeTempFile("sed s/o/e/g");

		try (Git git = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "tstFilter", "something",
					"sh " + script.getPath());
			config.save();
			writeTrashFile(".gitattributes", "*.txt filter=tstFilter");

			git.add().addFilepattern("a.txt").call();

			assertEquals("[a.txt, mode:100644, content:foo]",
					indexState(CONTENT));
		}
	}

	private File writeTempFile(String body) throws IOException {
		File f = File.createTempFile("AddCommandTest_", "");
		JGitTestUtil.write(f, body);
		return f;
	}

	@Test
	public void testAddExistingSingleSmallFileWithNewLine() throws IOException,
			GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("row1\r\nrow2");
		writer.close();

		try (Git git = new Git(db)) {
			db.getConfig().setString("core", null, "autocrlf", "false");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\r\nrow2]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "true");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\nrow2]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "input");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\nrow2]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddExistingSingleMediumSizeFileWithNewLine()
			throws IOException, GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		StringBuilder data = new StringBuilder();
		for (int i = 0; i < 1000; ++i) {
			data.append("row1\r\nrow2");
		}
		String crData = data.toString();
		PrintWriter writer = new PrintWriter(file);
		writer.print(crData);
		writer.close();
		String lfData = data.toString().replaceAll("\r", "");
		try (Git git = new Git(db)) {
			db.getConfig().setString("core", null, "autocrlf", "false");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:" + data + "]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "true");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:" + lfData + "]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "input");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:" + lfData + "]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddExistingSingleBinaryFile() throws IOException,
			GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("row1\r\nrow2\u0000");
		writer.close();

		try (Git git = new Git(db)) {
			db.getConfig().setString("core", null, "autocrlf", "false");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\r\nrow2\u0000]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "true");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\r\nrow2\u0000]",
					indexState(CONTENT));
			db.getConfig().setString("core", null, "autocrlf", "input");
			git.add().addFilepattern("a.txt").call();
			assertEquals("[a.txt, mode:100644, content:row1\r\nrow2\u0000]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddExistingSingleFileInSubDir() throws IOException,
			GitAPIException {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("sub/a.txt").call();

			assertEquals(
					"[sub/a.txt, mode:100644, content:content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddExistingSingleFileTwice() throws IOException,
			GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			DirCache dc = git.add().addFilepattern("a.txt").call();

			dc.getEntry(0).getObjectId();

			writer = new PrintWriter(file);
			writer.print("other content");
			writer.close();

			dc = git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:other content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddExistingSingleFileTwiceWithCommit() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			DirCache dc = git.add().addFilepattern("a.txt").call();

			dc.getEntry(0).getObjectId();

			git.commit().setMessage("commit a.txt").call();

			writer = new PrintWriter(file);
			writer.print("other content");
			writer.close();

			dc = git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:other content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddRemovedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			DirCache dc = git.add().addFilepattern("a.txt").call();

			dc.getEntry(0).getObjectId();
			FileUtils.delete(file);

			// is supposed to do nothing
			dc = git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddRemovedCommittedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		try (Git git = new Git(db)) {
			DirCache dc = git.add().addFilepattern("a.txt").call();

			git.commit().setMessage("commit a.txt").call();

			dc.getEntry(0).getObjectId();
			FileUtils.delete(file);

			// is supposed to do nothing
			dc = git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddWithConflicts() throws Exception {
		// prepare conflict

		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		ObjectInserter newObjectInserter = db.newObjectInserter();
		DirCache dc = db.lockDirCache();
		DirCacheBuilder builder = dc.builder();

		addEntryToBuilder("b.txt", file2, newObjectInserter, builder, 0);
		addEntryToBuilder("a.txt", file, newObjectInserter, builder, 1);

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();
		addEntryToBuilder("a.txt", file, newObjectInserter, builder, 3);

		writer = new PrintWriter(file);
		writer.print("our content");
		writer.close();
		addEntryToBuilder("a.txt", file, newObjectInserter, builder, 2)
				.getObjectId();

		builder.commit();

		assertEquals(
				"[a.txt, mode:100644, stage:1, content:content]" +
				"[a.txt, mode:100644, stage:2, content:our content]" +
				"[a.txt, mode:100644, stage:3, content:other content]" +
				"[b.txt, mode:100644, content:content b]",
				indexState(CONTENT));

		// now the test begins

		try (Git git = new Git(db)) {
			dc = git.add().addFilepattern("a.txt").call();

			assertEquals(
					"[a.txt, mode:100644, content:our content]" +
					"[b.txt, mode:100644, content:content b]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddTwoFiles() throws Exception  {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
			assertEquals(
					"[a.txt, mode:100644, content:content]" +
					"[b.txt, mode:100644, content:content b]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddFolder() throws Exception  {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("sub").call();
			assertEquals(
					"[sub/a.txt, mode:100644, content:content]" +
					"[sub/b.txt, mode:100644, content:content b]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddIgnoredFile() throws Exception  {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File ignoreFile = new File(db.getWorkTree(), ".gitignore");
		FileUtils.createNewFile(ignoreFile);
		writer = new PrintWriter(ignoreFile);
		writer.print("sub/b.txt");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("sub").call();

			assertEquals(
					"[sub/a.txt, mode:100644, content:content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAddWholeRepo() throws Exception  {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			assertEquals(
					"[sub/a.txt, mode:100644, content:content]" +
					"[sub/b.txt, mode:100644, content:content b]",
					indexState(CONTENT));
		}
	}

	// the same three cases as in testAddWithParameterUpdate
	// file a exists in workdir and in index -> added
	// file b exists not in workdir but in index -> unchanged
	// file c exists in workdir but not in index -> added
	@Test
	public void testAddWithoutParameterUpdate() throws Exception {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("sub").call();

			assertEquals(
					"[sub/a.txt, mode:100644, content:content]" +
					"[sub/b.txt, mode:100644, content:content b]",
					indexState(CONTENT));

			git.commit().setMessage("commit").call();

			// new unstaged file sub/c.txt
			File file3 = new File(db.getWorkTree(), "sub/c.txt");
			FileUtils.createNewFile(file3);
			writer = new PrintWriter(file3);
			writer.print("content c");
			writer.close();

			// file sub/a.txt is modified
			writer = new PrintWriter(file);
			writer.print("modified content");
			writer.close();

			// file sub/b.txt is deleted
			FileUtils.delete(file2);

			git.add().addFilepattern("sub").call();
			// change in sub/a.txt is staged
			// deletion of sub/b.txt is not staged
			// sub/c.txt is staged
			assertEquals(
					"[sub/a.txt, mode:100644, content:modified content]" +
					"[sub/b.txt, mode:100644, content:content b]" +
					"[sub/c.txt, mode:100644, content:content c]",
					indexState(CONTENT));
		}
	}

	// file a exists in workdir and in index -> added
	// file b exists not in workdir but in index -> deleted
	// file c exists in workdir but not in index -> unchanged
	@Test
	public void testAddWithParameterUpdate() throws Exception {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		FileUtils.createNewFile(file2);
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		try (Git git = new Git(db)) {
			git.add().addFilepattern("sub").call();

			assertEquals(
					"[sub/a.txt, mode:100644, content:content]" +
					"[sub/b.txt, mode:100644, content:content b]",
					indexState(CONTENT));

			git.commit().setMessage("commit").call();

			// new unstaged file sub/c.txt
			File file3 = new File(db.getWorkTree(), "sub/c.txt");
			FileUtils.createNewFile(file3);
			writer = new PrintWriter(file3);
			writer.print("content c");
			writer.close();

			// file sub/a.txt is modified
			writer = new PrintWriter(file);
			writer.print("modified content");
			writer.close();

			FileUtils.delete(file2);

			// change in sub/a.txt is staged
			// deletion of sub/b.txt is staged
			// sub/c.txt is not staged
			git.add().addFilepattern("sub").setUpdate(true).call();
			// change in sub/a.txt is staged
			assertEquals(
					"[sub/a.txt, mode:100644, content:modified content]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testAssumeUnchanged() throws Exception {
		try (Git git = new Git(db)) {
			String path = "a.txt";
			writeTrashFile(path, "content");
			git.add().addFilepattern(path).call();
			String path2 = "b.txt";
			writeTrashFile(path2, "content");
			git.add().addFilepattern(path2).call();
			git.commit().setMessage("commit").call();
			assertEquals("[a.txt, mode:100644, content:"
					+ "content, assume-unchanged:false]"
					+ "[b.txt, mode:100644, content:content, "
					+ "assume-unchanged:false]", indexState(CONTENT
					| ASSUME_UNCHANGED));
			assumeUnchanged(path2);
			assertEquals("[a.txt, mode:100644, content:content, "
					+ "assume-unchanged:false][b.txt, mode:100644, "
					+ "content:content, assume-unchanged:true]", indexState(CONTENT
					| ASSUME_UNCHANGED));
			writeTrashFile(path, "more content");
			writeTrashFile(path2, "more content");

			git.add().addFilepattern(".").call();

			assertEquals("[a.txt, mode:100644, content:more content,"
					+ " assume-unchanged:false][b.txt, mode:100644,"
					+ " content:content, assume-unchanged:true]",
					indexState(CONTENT
					| ASSUME_UNCHANGED));
		}
	}

	@Test
	public void testReplaceFileWithDirectory()
			throws IOException, NoFilepatternException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("df", "before replacement");
			git.add().addFilepattern("df").call();
			assertEquals("[df, mode:100644, content:before replacement]",
					indexState(CONTENT));
			FileUtils.delete(new File(db.getWorkTree(), "df"));
			writeTrashFile("df/f", "after replacement");
			git.add().addFilepattern("df").call();
			assertEquals("[df/f, mode:100644, content:after replacement]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testReplaceDirectoryWithFile()
			throws IOException, NoFilepatternException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("df/f", "before replacement");
			git.add().addFilepattern("df").call();
			assertEquals("[df/f, mode:100644, content:before replacement]",
					indexState(CONTENT));
			FileUtils.delete(new File(db.getWorkTree(), "df"), RECURSIVE);
			writeTrashFile("df", "after replacement");
			git.add().addFilepattern("df").call();
			assertEquals("[df, mode:100644, content:after replacement]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testReplaceFileByPartOfDirectory()
			throws IOException, NoFilepatternException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("src/main", "df", "before replacement");
			writeTrashFile("src/main", "z", "z");
			writeTrashFile("z", "z2");
			git.add().addFilepattern("src/main/df")
				.addFilepattern("src/main/z")
				.addFilepattern("z")
				.call();
			assertEquals(
					"[src/main/df, mode:100644, content:before replacement]" +
					"[src/main/z, mode:100644, content:z]" +
					"[z, mode:100644, content:z2]",
					indexState(CONTENT));
			FileUtils.delete(new File(db.getWorkTree(), "src/main/df"));
			writeTrashFile("src/main/df", "a", "after replacement");
			writeTrashFile("src/main/df", "b", "unrelated file");
			git.add().addFilepattern("src/main/df/a").call();
			assertEquals(
					"[src/main/df/a, mode:100644, content:after replacement]" +
					"[src/main/z, mode:100644, content:z]" +
					"[z, mode:100644, content:z2]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testReplaceDirectoryConflictsWithFile()
			throws IOException, NoFilepatternException, GitAPIException {
		DirCache dc = db.lockDirCache();
		try (ObjectInserter oi = db.newObjectInserter()) {
			DirCacheBuilder builder = dc.builder();
			File f = writeTrashFile("a", "df", "content");
			addEntryToBuilder("a", f, oi, builder, 1);

			f = writeTrashFile("a", "df", "other content");
			addEntryToBuilder("a/df", f, oi, builder, 3);

			f = writeTrashFile("a", "df", "our content");
			addEntryToBuilder("a/df", f, oi, builder, 2);

			f = writeTrashFile("z", "z");
			addEntryToBuilder("z", f, oi, builder, 0);
			builder.commit();
		}
		assertEquals(
				"[a, mode:100644, stage:1, content:content]" +
				"[a/df, mode:100644, stage:2, content:our content]" +
				"[a/df, mode:100644, stage:3, content:other content]" +
				"[z, mode:100644, content:z]",
				indexState(CONTENT));

		try (Git git = new Git(db)) {
			FileUtils.delete(new File(db.getWorkTree(), "a"), RECURSIVE);
			writeTrashFile("a", "merged");
			git.add().addFilepattern("a").call();
			assertEquals("[a, mode:100644, content:merged]" +
					"[z, mode:100644, content:z]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testExecutableRetention() throws Exception {
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		config.save();

		FS executableFs = new FS() {

			public boolean supportsExecute() {
				return true;
			}

			public boolean setExecute(File f, boolean canExec) {
				return true;
			}

			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			public boolean retryFailedLockFileCommit() {
				return false;
			}

			public FS newInstance() {
				return this;
			}

			protected File discoverGitExe() {
				return null;
			}

			public boolean canExecute(File f) {
				return true;
			}

			@Override
			public boolean isCaseSensitive() {
				return false;
			}
		};

		Git git = Git.open(db.getDirectory(), executableFs);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		RevCommit commit1 = git.commit().setMessage("commit").call();
		TreeWalk walk = TreeWalk.forPath(db, path, commit1.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));

		FS nonExecutableFs = new FS() {

			public boolean supportsExecute() {
				return false;
			}

			public boolean setExecute(File f, boolean canExec) {
				return false;
			}

			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			public boolean retryFailedLockFileCommit() {
				return false;
			}

			public FS newInstance() {
				return this;
			}

			protected File discoverGitExe() {
				return null;
			}

			public boolean canExecute(File f) {
				return false;
			}

			@Override
			public boolean isCaseSensitive() {
				return false;
			}
		};

		config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();

		Git git2 = Git.open(db.getDirectory(), nonExecutableFs);
		writeTrashFile(path, "content2");
		git2.add().addFilepattern(path).call();
		RevCommit commit2 = git2.commit().setMessage("commit2").call();
		walk = TreeWalk.forPath(db, path, commit2.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));
	}

	@Test
	public void testAddGitlink() throws Exception {
		createNestedRepo("git-link-dir");
		try (Git git = new Git(db)) {
			git.add().addFilepattern("git-link-dir").call();

			assertEquals(
					"[git-link-dir, mode:160000]",
					indexState(0));
			Set<String> untrackedFiles = git.status().call().getUntracked();
			assert (untrackedFiles.isEmpty());
		}

	}

	@Test
	public void testAddSubrepoWithDirNoGitlinks() throws Exception {
		createNestedRepo("nested-repo");

		// Set DIR_NO_GITLINKS
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_DIRNOGITLINKS, true);
		config.save();

		assert (db.getConfig().get(WorkingTreeOptions.KEY).isDirNoGitLinks());

		try (Git git = new Git(db)) {
			git.add().addFilepattern("nested-repo").call();

			assertEquals(
					"[nested-repo/README1.md, mode:100644]" +
							"[nested-repo/README2.md, mode:100644]",
					indexState(0));
		}

		// Turn off DIR_NO_GITLINKS, ensure nested-repo is still treated as
		// a normal directory
		// Set DIR_NO_GITLINKS
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_DIRNOGITLINKS, false);
		config.save();

		writeTrashFile("nested-repo", "README3.md", "content");

		try (Git git = new Git(db)) {
			git.add().addFilepattern("nested-repo").call();

			assertEquals(
					"[nested-repo/README1.md, mode:100644]" +
							"[nested-repo/README2.md, mode:100644]" +
							"[nested-repo/README3.md, mode:100644]",
					indexState(0));
		}
	}

	@Test
	public void testAddGitlinkDoesNotChange() throws Exception {
		createNestedRepo("nested-repo");

		try (Git git = new Git(db)) {
			git.add().addFilepattern("nested-repo").call();

			assertEquals(
					"[nested-repo, mode:160000]",
					indexState(0));
		}

		// Set DIR_NO_GITLINKS
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_DIRNOGITLINKS, true);
		config.save();

		assert (db.getConfig().get(WorkingTreeOptions.KEY).isDirNoGitLinks());

		try (Git git = new Git(db)) {
			git.add().addFilepattern("nested-repo").call();

			assertEquals(
					"[nested-repo, mode:160000]",
					indexState(0));
		}
	}

	private static DirCacheEntry addEntryToBuilder(String path, File file,
			ObjectInserter newObjectInserter, DirCacheBuilder builder, int stage)
			throws IOException {
		FileInputStream inputStream = new FileInputStream(file);
		ObjectId id = newObjectInserter.insert(
				Constants.OBJ_BLOB, file.length(), inputStream);
		inputStream.close();
		DirCacheEntry entry = new DirCacheEntry(path, stage);
		entry.setObjectId(id);
		entry.setFileMode(FileMode.REGULAR_FILE);
		entry.setLastModified(file.lastModified());
		entry.setLength((int) file.length());

		builder.add(entry);
		return entry;
	}

	private void assumeUnchanged(String path) throws IOException {
		final DirCache dirc = db.lockDirCache();
		final DirCacheEntry ent = dirc.getEntry(path);
		if (ent != null)
			ent.setAssumeValid(true);
		dirc.write();
		if (!dirc.commit())
			throw new IOException("could not commit");
	}

	private void createNestedRepo(String path) throws IOException {
		File gitLinkDir = new File(db.getWorkTree(), path);
		FileUtils.mkdir(gitLinkDir);

		FileRepositoryBuilder nestedBuilder = new FileRepositoryBuilder();
		nestedBuilder.setWorkTree(gitLinkDir);

		Repository nestedRepo = nestedBuilder.build();
		nestedRepo.create();

		writeTrashFile(path, "README1.md", "content");
		writeTrashFile(path, "README2.md", "content");

		// Commit these changes in the subrepo
		try (Git git = new Git(nestedRepo)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("subrepo commit").call();
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}
}
