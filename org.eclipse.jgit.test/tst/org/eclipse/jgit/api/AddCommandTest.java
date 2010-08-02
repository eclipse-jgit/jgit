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
import java.util.TreeSet;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RepositoryTestCase;

public class AddCommandTest extends RepositoryTestCase {

	private TreeSet<Long> modTimes;

	public void setUp() throws Exception {
		super.setUp();
		modTimes = new TreeSet<Long>();
	}

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

	@SuppressWarnings("boxing")
	public void testAddExistingSingleFile() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		modTimes.add(file.lastModified());
		Git git = new Git(db);

		git.add().addFilepattern("a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		assertEquals(
				"[a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddExistingSingleFileInSubDir() throws IOException, NoFilepatternException {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		modTimes.add(file.lastModified());
		Git git = new Git(db);

		git.add().addFilepattern("sub/a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		assertEquals(
				"[sub/a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddExistingSingleFileTwice() throws IOException, NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		modTimes.add(file.lastModified());
		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();

		modTimes.add(db.getIndexFile().lastModified());
		dc.getEntry(0).getObjectId();

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();

		modTimes.add(file.lastModified());
		dc = git.add().addFilepattern("a.txt").call();

		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[a.txt, mode:100644, sha1:4f41554f6e0045ef53848fc0c3f33b6a9abc24a9]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddExistingSingleFileTwiceWithCommit() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		modTimes.add(file.lastModified());
		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		dc.getEntry(0).getObjectId();

		git.commit().setMessage("commit a.txt").call();

		writer = new PrintWriter(file);
		writer.print("other content");
		writer.close();
		modTimes.add(file.lastModified());

		dc = git.add().addFilepattern("a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		assertEquals(
				"[a.txt, mode:100644, sha1:4f41554f6e0045ef53848fc0c3f33b6a9abc24a9]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddRemovedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddRemovedCommittedFile() throws Exception {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("a.txt").call();
		modTimes.add(db.getIndexFile().lastModified());

		git.commit().setMessage("commit a.txt").call();

		dc.getEntry(0).getObjectId();
		file.delete();

		// is supposed to do nothing
		dc = git.add().addFilepattern("a.txt").call();

		assertEquals(
				"[a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddWithConflicts() throws Exception {
		// prepare conflict

		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();
		modTimes.add(file2.lastModified());

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

		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[a.txt, mode:100644, stage:1, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[a.txt, mode:100644, stage:2, sha1:b9f89ff733bdaf49e02711535867bb821f9db55e]" +
				"[a.txt, mode:100644, stage:3, sha1:4f41554f6e0045ef53848fc0c3f33b6a9abc24a9]" +
				"[b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));

		// now the test begins

		Git git = new Git(db);
		dc = git.add().addFilepattern("a.txt").call();

		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[a.txt, mode:100644, sha1:b9f89ff733bdaf49e02711535867bb821f9db55e]" +
				"[b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddTwoFiles() throws Exception  {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();

		modTimes.add(file2.lastModified());
		Git git = new Git(db);
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddFolder() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();
		modTimes.add(file2.lastModified());

		Git git = new Git(db);
		git.add().addFilepattern("sub").call();
		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[sub/a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[sub/b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
	}

	@SuppressWarnings("boxing")
	public void testAddIgnoredFile() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

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
		modTimes.add(file2.lastModified());

		Git git = new Git(db);
		git.add().addFilepattern("sub").call();
		modTimes.add(db.getIndexFile().lastModified());

		DirCache dc = git.add().addFilepattern("sub").call();
		assertEquals("sub/a.txt", dc.getEntry("sub/a.txt").getPathString());
		assertNull(dc.getEntry("sub/b.txt"));
		assertNotNull(dc.getEntry("sub/a.txt").getObjectId());
		assertEquals(0, dc.getEntry("sub/a.txt").getStage());
	}

	@SuppressWarnings("boxing")
	public void testAddWholeRepo() throws Exception  {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();
		modTimes.add(file2.lastModified());

		Git git = new Git(db);
		git.add().addFilepattern(".").call();
		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[sub/a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[sub/b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
	}

	// the same three cases as in testAddWithParameterUpdate
	// file a exists in workdir and in index -> added
	// file b exists not in workdir but in index -> unchanged
	// file c exists in workdir but not in index -> added
	@SuppressWarnings("boxing")
	public void testAddWithoutParameterUpdate() throws Exception {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();
		modTimes.add(file2.lastModified());

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("sub").call();

		modTimes.add(db.getIndexFile().lastModified());
		assertEquals(
				"[sub/a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[sub/b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
		assertTrue(dc.getEntry("sub/a.txt").getLength() == 7);
		// deletion of sub/b.txt is staged
		assertNotNull(dc.getEntry("sub/b.txt"));

		git.commit().setMessage("commit").call();

		// new unstaged file sub/c.txt
		File file3 = new File(db.getWorkTree(), "sub/c.txt");
		file3.createNewFile();
		writer = new PrintWriter(file3);
		writer.print("content c");
		writer.close();
		modTimes.add(file2.lastModified());

		// file sub/a.txt is modified
		writer = new PrintWriter(file);
		writer.print("modified content");
		writer.close();
		modTimes.add(file.lastModified());

		// file sub/b.txt is deleted
		file2.delete();

		dc = git.add().addFilepattern("sub").call();
		modTimes.add(db.getIndexFile().lastModified());
		// change in sub/a.txt is staged
		// deletion of sub/b.txt is not staged
		// sub/c.txt is staged
		assertEquals(
				"[sub/a.txt, mode:100644, sha1:268af4e306cfcf6e79edd50fed9c553d211f68e3]" +
				"[sub/b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]" +
				"[sub/c.txt, mode:100644, sha1:fa08654474ae2ddc4f61ee3a43d017ba65a439c3]",
				indexState(CONTENT_ID));
	}

	// file a exists in workdir and in index -> added
	// file b exists not in workdir but in index -> deleted
	// file c exists in workdir but not in index -> unchanged
	@SuppressWarnings("boxing")
	public void testAddWithParameterUpdate() throws Exception {
		new File(db.getWorkTree(), "sub").mkdir();
		File file = new File(db.getWorkTree(), "sub/a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();
		modTimes.add(file.lastModified());

		File file2 = new File(db.getWorkTree(), "sub/b.txt");
		file2.createNewFile();
		writer = new PrintWriter(file2);
		writer.print("content b");
		writer.close();
		modTimes.add(file2.lastModified());

		Git git = new Git(db);
		DirCache dc = git.add().addFilepattern("sub").call();
		modTimes.add(db.getIndexFile().lastModified());

		assertEquals(
				"[sub/a.txt, mode:100644, sha1:6b584e8ece562ebffc15d38808cd6b98fc3d97ea]" +
				"[sub/b.txt, mode:100644, sha1:50e9cdb03f9719261dd39d7f2920b906db3711a3]",
				indexState(CONTENT_ID));
		assertTrue(dc.getEntry("sub/a.txt").getLength() == 7);
		// deletion of sub/b.txt is staged
		assertNotNull(dc.getEntry("sub/b.txt"));

		git.commit().setMessage("commit").call();

		// new unstaged file sub/c.txt
		File file3 = new File(db.getWorkTree(), "sub/c.txt");
		file3.createNewFile();
		writer = new PrintWriter(file3);
		writer.print("content c");
		writer.close();
		modTimes.add(file3.lastModified());

		// file sub/a.txt is modified
		writer = new PrintWriter(file);
		writer.print("modified content");
		writer.close();
		modTimes.add(file.lastModified());

		file2.delete();

		dc = git.add().addFilepattern("sub").setUpdate(true).call();
		modTimes.add(db.getIndexFile().lastModified());

		// change in sub/a.txt is staged
		// deletion of sub/b.txt is staged
		// sub/c.txt is not staged
		dc = git.add().addFilepattern("sub").setUpdate(true).call();
		// change in sub/a.txt is staged
		assertTrue(dc.getEntry("sub/a.txt").getLastModified() ==
			file.lastModified());
		assertNull(dc.getEntry("sub/c.txt"));
		// deletion of sub/b.txt is staged
		assertNull(dc.getEntry("sub/b.txt"));
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
