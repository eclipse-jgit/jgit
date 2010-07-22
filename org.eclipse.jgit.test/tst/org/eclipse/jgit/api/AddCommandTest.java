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
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
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
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		DirCache dc = git.add().addFilepattern("a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry(0).getPathString());
		assertNotNull(dc.getEntry(0).getObjectId());
		assertEquals(file.lastModified(), dc.getEntry(0).getLastModified());
		assertEquals(file.length(), dc.getEntry(0).getLength());
		assertEquals(FileMode.REGULAR_FILE, dc.getEntry(0).getFileMode());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddExistingSingleFileInSubDir() throws IOException, NoFilepatternException {
		new File(db.getWorkDir(), "sub").mkdir();
		File file = new File(db.getWorkDir(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);

		DirCache dc = git.add().addFilepattern("sub/a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("sub/a.txt", dc.getEntry(0).getPathString());
		assertNotNull(dc.getEntry(0).getObjectId());
		assertEquals(file.lastModified(), dc.getEntry(0).getLastModified());
		assertEquals(file.length(), dc.getEntry(0).getLength());
		assertEquals(FileMode.REGULAR_FILE, dc.getEntry(0).getFileMode());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddExistingSingleFileTwice() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		ObjectId id1 = dc.getEntry(0).getObjectId();

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();

		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry(0).getPathString());
		assertNotSame(id1, dc.getEntry(0).getObjectId());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddExistingSingleFileTwiceWithCommit() throws Exception {
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		ObjectId id1 = dc.getEntry(0).getObjectId();

		git.commit().setMessage("commit a.txt").call();

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();

		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry(0).getPathString());
		assertNotSame(id1, dc.getEntry(0).getObjectId());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddRemovedFile() throws Exception {
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		ObjectId id1 = dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry(0).getPathString());
		assertEquals(id1, dc.getEntry(0).getObjectId());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddRemovedCommittedFile() throws Exception {
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		git.commit().setMessage("commit a.txt").call();

		ObjectId id1 = dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(1, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry(0).getPathString());
		assertEquals(id1, dc.getEntry(0).getObjectId());
		assertEquals(0, dc.getEntry(0).getStage());
	}

	public void testAddWithConflicts() throws Exception {
		// prepare conflict

		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkDir(), "b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		ObjectWriter ow = new ObjectWriter(db);
		DirCache dc = DirCache.lock(db);
		DirCacheBuilder builder = dc.builder();

		addEntryToBuilder("b.txt", file2, ow, builder, 0);
		addEntryToBuilder("a.txt", file, ow, builder, 1);

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();
		addEntryToBuilder("a.txt", file, ow, builder, 3);

		writer = new PrintWriter(file);
		writer.print("our content");
		writer.close();
		ObjectId id1 = addEntryToBuilder("a.txt", file, ow, builder, 2)
				.getObjectId();

		builder.commit();

		assertEquals(4, dc.getEntryCount());

		// now the test begins

		Git git = new Git(db);
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(2, dc.getEntryCount());
		assertEquals("a.txt", dc.getEntry("a.txt").getPathString());
		assertEquals(id1, dc.getEntry("a.txt").getObjectId());
		assertEquals(0, dc.getEntry("a.txt").getStage());
		assertEquals(0, dc.getEntry("b.txt").getStage());
	}

	public void testAddTwoFiles() throws Exception  {
		File file = new File(db.getWorkDir(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkDir(), "b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		assertEquals("a.txt", dc.getEntry("a.txt").getPathString());
		assertEquals("b.txt", dc.getEntry("b.txt").getPathString());
		assertNotNull(dc.getEntry("a.txt").getObjectId());
		assertNotNull(dc.getEntry("b.txt").getObjectId());
		assertEquals(0, dc.getEntry("a.txt").getStage());
		assertEquals(0, dc.getEntry("b.txt").getStage());
	}

	public void testAddFolder() throws Exception  {
		new File(db.getWorkDir(), "sub").mkdir();
		File file = new File(db.getWorkDir(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkDir(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("sub").call();
		assertEquals("sub/a.txt", dc.getEntry("sub/a.txt").getPathString());
		assertEquals("sub/b.txt", dc.getEntry("sub/b.txt").getPathString());
		assertNotNull(dc.getEntry("sub/a.txt").getObjectId());
		assertNotNull(dc.getEntry("sub/b.txt").getObjectId());
		assertEquals(0, dc.getEntry("sub/a.txt").getStage());
		assertEquals(0, dc.getEntry("sub/b.txt").getStage());
	}

	public void testAddIgnoredFile() throws Exception  {
		new File(db.getWorkDir(), "sub").mkdir();
		File file = new File(db.getWorkDir(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File ignoreFile = new File(db.getWorkDir(), ".gitignore");
		ignoreFile.createNewFile();
		writer = new PrintWriter(ignoreFile);
		writer.print("sub/b.txt");
		writer.close();

		File file2 = new File(db.getWorkDir(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("sub").call();
		assertEquals("sub/a.txt", dc.getEntry("sub/a.txt").getPathString());
		assertNull(dc.getEntry("sub/b.txt"));
		assertNotNull(dc.getEntry("sub/a.txt").getObjectId());
		assertEquals(0, dc.getEntry("sub/a.txt").getStage());
	}

	public void testAddWholeRepo() throws Exception  {
		new File(db.getWorkDir(), "sub").mkdir();
		File file = new File(db.getWorkDir(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		File file2 = new File(db.getWorkDir(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern(".").call();
		assertEquals("sub/a.txt", dc.getEntry("sub/a.txt").getPathString());
		assertEquals("sub/b.txt", dc.getEntry("sub/b.txt").getPathString());
	}

	private DirCacheEntry addEntryToBuilder(String path, File file,
			ObjectWriter ow, DirCacheBuilder builder, int stage)
			throws IOException {
		ObjectId id = ow.writeBlob(file);
		DirCacheEntry entry = new DirCacheEntry(path, stage);
		entry.setObjectId(id);
		entry.setFileMode(FileMode.REGULAR_FILE);
		entry.setLastModified(file.lastModified());
		entry.setLength((int) file.length());

		builder.add(entry);
		return entry;
	}

}
