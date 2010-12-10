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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class AddCommandTest extends RepositoryTestCase {

	@Test
	public void testAddNothing() {
		Git git = new Git(db);

		try {
			git.add().call();
			fail("Expected IllegalArgumentException");
		} catch (NoFilepatternException e) {
			// expected
		}

	}

	@Test
	public void testAddNonExistingSingleFile() throws NoFilepatternException {
		Git git = new Git(db);

		DirCache dc = git.add().addFilepattern("a.txt").call();
		assertEquals(0, dc.getEntryCount());

	}

	@Test
	public void testAddExistingSingleFile() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	@Test
	public void testAddExistingSingleFileInSubDir() throws IOException, NoFilepatternException {
		FileUtils.mkdir(new File(db.getWorkTree(), "sub"));
		File file = new File(db.getWorkTree(), "sub/a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		git.add().addFilepattern("sub/a.txt").call();

		assertEquals(
				"[sub/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	@Test
	public void testAddExistingSingleFileTwice() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
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

	@Test
	public void testAddExistingSingleFileTwiceWithCommit() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
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

	@Test
	public void testAddRemovedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		dc.getEntry(0).getObjectId();
		FileUtils.delete(file);

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	@Test
	public void testAddRemovedCommittedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
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

		Git git = new Git(db);
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:our content]" +
				"[b.txt, mode:100644, content:content b]",
				indexState(CONTENT));
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

		Git git = new Git(db);
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		assertEquals(
				"[a.txt, mode:100644, content:content]" +
				"[b.txt, mode:100644, content:content b]",
				indexState(CONTENT));
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

		Git git = new Git(db);
		git.add().addFilepattern("sub").call();
		assertEquals(
				"[sub/a.txt, mode:100644, content:content]" +
				"[sub/b.txt, mode:100644, content:content b]",
				indexState(CONTENT));
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

		Git git = new Git(db);
		git.add().addFilepattern("sub").call();

		assertEquals(
				"[sub/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
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

		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		assertEquals(
				"[sub/a.txt, mode:100644, content:content]" +
				"[sub/b.txt, mode:100644, content:content b]",
				indexState(CONTENT));
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

		Git git = new Git(db);
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

		Git git = new Git(db);
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

	@Test
	public void testAssumeUnchanged() throws Exception {
		Git git = new Git(db);
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
 + "" + ""
				+ " content:content, assume-unchanged:true]",
				indexState(CONTENT
				| ASSUME_UNCHANGED));
	}

	private DirCacheEntry addEntryToBuilder(String path, File file,
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

}
