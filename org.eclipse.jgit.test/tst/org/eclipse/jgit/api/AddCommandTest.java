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

public class AddCommandTest extends RepositoryTestCase {

	public void testAddNothing() {
		Git git = new Git(db);

		try {
			git.add().call();
			fail("Expected IllegalArgumentException");
		} catch (NoFilepatternException e) {
			// expected
		}

	}

	public void testAddNonExistingSingleFile() throws NoFilepatternException {
		Git git = new Git(db);

		DirCache dc = git.add().addFilepattern("a.txt").call();
		assertEquals(0, dc.getEntryCount());

	}

	public void testAddExistingSingleFile() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	public void testAddExistingSingleFileInSubDir() throws IOException, NoFilepatternException {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		git.add().addFilepattern("sub/a.txt").call();

		assertEquals(
				"[sub/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	public void testAddExistingSingleFileTwice() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
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

	public void testAddExistingSingleFileTwiceWithCommit() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
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

	public void testAddRemovedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	public void testAddRemovedCommittedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		git.commit().setMessage("commit a.txt").call();

		dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	public void testAddWithConflicts() throws Exception {
		// prepare conflict

		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "b.txt");
		file2.createNewFile();
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

	public void testAddTwoFiles() throws Exception  {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "b.txt");
		file2.createNewFile();
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

	public void testAddFolder() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
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

	public void testAddIgnoredFile() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File ignoreFile = new File(db.getWorkTree(), ".gitignore");
		ignoreFile.createNewFile();
		writer = new PrintWriter(ignoreFile);
		writer.print("sub/b.txt");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		Git git = new Git(db);
		git.add().addFilepattern("sub").call();

		assertEquals(
				"[sub/a.txt, mode:100644, content:content]",
				indexState(CONTENT));
	}

	public void testAddWholeRepo() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
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
	public void testAddWithoutParameterUpdate() throws Exception {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
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
		file3.createNewFile();
		writer = new PrintWriter(file3);
		writer.print("content c");
		writer.close();

		// file sub/a.txt is modified
		writer = new PrintWriter(file);
		writer.print("modified content");
		writer.close();

		// file sub/b.txt is deleted
		file2.delete();

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
	public void testAddWithParameterUpdate() throws Exception {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
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
		file3.createNewFile();
		writer = new PrintWriter(file3);
		writer.print("content c");
		writer.close();

		// file sub/a.txt is modified
		writer = new PrintWriter(file);
		writer.print("modified content");
		writer.close();

		file2.delete();

		// change in sub/a.txt is staged
		// deletion of sub/b.txt is staged
		// sub/c.txt is not staged
		git.add().addFilepattern("sub").setUpdate(true).call();
		// change in sub/a.txt is staged
		assertEquals(
				"[sub/a.txt, mode:100644, content:modified content]",
				indexState(CONTENT));
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

}
