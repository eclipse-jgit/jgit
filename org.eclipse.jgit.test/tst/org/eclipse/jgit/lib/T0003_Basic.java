/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.errors.ConfigInvalidException;

public class T0003_Basic extends SampleDataRepositoryTestCase {
	public void test001_Initalize() {
		final File gitdir = new File(trash, Constants.DOT_GIT);
		final File objects = new File(gitdir, "objects");
		final File objects_pack = new File(objects, "pack");
		final File objects_info = new File(objects, "info");
		final File refs = new File(gitdir, "refs");
		final File refs_heads = new File(refs, "heads");
		final File refs_tags = new File(refs, "tags");
		final File HEAD = new File(gitdir, "HEAD");

		assertTrue("Exists " + trash, trash.isDirectory());
		assertTrue("Exists " + objects, objects.isDirectory());
		assertTrue("Exists " + objects_pack, objects_pack.isDirectory());
		assertTrue("Exists " + objects_info, objects_info.isDirectory());
		assertEquals(2, objects.listFiles().length);
		assertTrue("Exists " + refs, refs.isDirectory());
		assertTrue("Exists " + refs_heads, refs_heads.isDirectory());
		assertTrue("Exists " + refs_tags, refs_tags.isDirectory());
		assertTrue("Exists " + HEAD, HEAD.isFile());
		assertEquals(23, HEAD.length());
	}

	public void test000_openRepoBadArgs() throws IOException {
		try {
			new Repository(null, null);
			fail("Must pass either GIT_DIR or GIT_WORK_TREE");
		} catch (IllegalArgumentException e) {
			assertEquals(
					"Either GIT_DIR or GIT_WORK_TREE must be passed to Repository constructor",
					e.getMessage());
		}
	}

	/**
	 * Check the default rules for looking up directories and files within a
	 * repo when the gitDir is given.
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_default_gitDirSet() throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(theDir, null);
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent, r.getWorkDir());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectsDirectory());
	}

	/**
	 * Check that we can pass both a git directory and a work tree
	 * repo when the gitDir is given.
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_default_gitDirAndWorkTreeSet() throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(theDir, repo1Parent.getParentFile());
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent.getParentFile(), r.getWorkDir());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectsDirectory());
	}

	/**
	 * Check the default rules for looking up directories and files within a
	 * repo when the workTree is given.
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_default_workDirSet() throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(null, repo1Parent);
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent, r.getWorkDir());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectsDirectory());
	}

	/**
	 * Check that worktree config has an effect, given absolute path.
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_default_absolute_workdirconfig()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File workdir = new File(trash.getParentFile(), "rw");
		workdir.mkdir();
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.getConfig().setString("core", null, "worktree",
				workdir.getAbsolutePath());
		repo1initial.getConfig().save();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(theDir, null);
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(workdir, r.getWorkDir());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectsDirectory());
	}

	/**
	 * Check that worktree config has an effect, given a relative path.
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_default_relative_workdirconfig()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File workdir = new File(trash.getParentFile(), "rw");
		workdir.mkdir();
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.getConfig()
				.setString("core", null, "worktree", "../../rw");
		repo1initial.getConfig().save();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(theDir, null);
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(workdir, r.getWorkDir());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectsDirectory());
	}

	/**
	 * Check that the given index file is honored and the alternate object
	 * directories too
	 *
	 * @throws IOException
	 */
	public void test000_openrepo_alternate_index_file_and_objdirs()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File indexFile = new File(trash, "idx");
		File objDir = new File(trash, "../obj");
		File[] altObjDirs = new File[] { db.getObjectsDirectory() };
		Repository repo1initial = new Repository(new File(repo1Parent, Constants.DOT_GIT));
		repo1initial.create();
		repo1initial.close();

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		Repository r = new Repository(theDir, null, objDir, altObjDirs,
				indexFile);
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(theDir.getParentFile(), r.getWorkDir());
		assertEqualsPath(indexFile, r.getIndexFile());
		assertEqualsPath(objDir, r.getObjectsDirectory());
		assertNotNull(r.mapCommit("6db9c2ebf75590eef973081736730a9ea169a0c4"));
		// Must close or the default repo pack files created by this test gets
		// locked via the alternate object directories on Windows.
		r.close();
	}

	protected void assertEqualsPath(File expected, File actual)
			throws IOException {
		assertEquals(expected.getCanonicalPath(), actual.getCanonicalPath());
	}

	public void test002_WriteEmptyTree() throws IOException {
		// One of our test packs contains the empty tree object. If the pack is
		// open when we create it we won't write the object file out as a loose
		// object (as it already exists in the pack).
		//
		final Repository newdb = createBareRepository();
		final Tree t = new Tree(newdb);
		t.accept(new WriteTree(trash, newdb), TreeEntry.MODIFIED_ONLY);
		assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", t.getId()
				.name());
		final File o = new File(new File(new File(newdb.getDirectory(),
				"objects"), "4b"), "825dc642cb6eb9a060e54bf8d69288fbee4904");
		assertTrue("Exists " + o, o.isFile());
		assertTrue("Read-only " + o, !o.canWrite());
	}

	public void test002_WriteEmptyTree2() throws IOException {
		// File shouldn't exist as it is in a test pack.
		//
		final Tree t = new Tree(db);
		t.accept(new WriteTree(trash, db), TreeEntry.MODIFIED_ONLY);
		assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", t.getId()
				.name());
		final File o = new File(new File(
				new File(db.getDirectory(), "objects"), "4b"),
				"825dc642cb6eb9a060e54bf8d69288fbee4904");
		assertFalse("Exists " + o, o.isFile());
	}

	public void test003_WriteShouldBeEmptyTree() throws IOException {
		final Tree t = new Tree(db);
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		t.addFile("should-be-empty").setId(emptyId);
		t.accept(new WriteTree(trash, db), TreeEntry.MODIFIED_ONLY);
		assertEquals("7bb943559a305bdd6bdee2cef6e5df2413c3d30a", t.getId()
				.name());

		File o;
		o = new File(new File(new File(db.getDirectory(), "objects"), "7b"),
				"b943559a305bdd6bdee2cef6e5df2413c3d30a");
		assertTrue("Exists " + o, o.isFile());
		assertTrue("Read-only " + o, !o.canWrite());

		o = new File(new File(new File(db.getDirectory(), "objects"), "e6"),
				"9de29bb2d1d6434b8b29ae775ad8c2e48c5391");
		assertTrue("Exists " + o, o.isFile());
		assertTrue("Read-only " + o, !o.canWrite());
	}

	public void test005_ReadSimpleConfig() {
		final RepositoryConfig c = db.getConfig();
		assertNotNull(c);
		assertEquals("0", c.getString("core", null, "repositoryformatversion"));
		assertEquals("0", c.getString("CoRe", null, "REPOSITORYFoRmAtVeRsIoN"));
		assertEquals("true", c.getString("core", null, "filemode"));
		assertEquals("true", c.getString("cOrE", null, "fIlEModE"));
		assertNull(c.getString("notavalue", null, "reallyNotAValue"));
	}

	public void test006_ReadUglyConfig() throws IOException,
			ConfigInvalidException {
		final RepositoryConfig c = db.getConfig();
		final File cfg = new File(db.getDirectory(), "config");
		final FileWriter pw = new FileWriter(cfg);
		final String configStr = "  [core];comment\n\tfilemode = yes\n"
				+ "[user]\n"
				+ "  email = A U Thor <thor@example.com> # Just an example...\n"
				+ " name = \"A  Thor \\\\ \\\"\\t \"\n"
				+ "    defaultCheckInComment = a many line\\n\\\ncomment\\n\\\n"
				+ " to test\n";
		pw.write(configStr);
		pw.close();
		c.load();
		assertEquals("yes", c.getString("core", null, "filemode"));
		assertEquals("A U Thor <thor@example.com>", c
				.getString("user", null, "email"));
		assertEquals("A  Thor \\ \"\t ", c.getString("user", null, "name"));
		assertEquals("a many line\ncomment\n to test", c.getString("user",
				null, "defaultCheckInComment"));
		c.save();
		final FileReader fr = new FileReader(cfg);
		final char[] cbuf = new char[configStr.length()];
		fr.read(cbuf);
		fr.close();
		assertEquals(configStr, new String(cbuf));
	}

	public void test007_Open() throws IOException {
		final Repository db2 = new Repository(db.getDirectory());
		assertEquals(db.getDirectory(), db2.getDirectory());
		assertEquals(db.getObjectsDirectory(), db2.getObjectsDirectory());
		assertNotSame(db.getConfig(), db2.getConfig());
	}

	public void test008_FailOnWrongVersion() throws IOException {
		final File cfg = new File(db.getDirectory(), "config");
		final FileWriter pw = new FileWriter(cfg);
		final String badvers = "ihopethisisneveraversion";
		final String configStr = "[core]\n" + "\trepositoryFormatVersion="
				+ badvers + "\n";
		pw.write(configStr);
		pw.close();

		try {
			new Repository(db.getDirectory());
			fail("incorrectly opened a bad repository");
		} catch (IOException ioe) {
			assertTrue(ioe.getMessage().indexOf("format") > 0);
			assertTrue(ioe.getMessage().indexOf(badvers) > 0);
		}
	}

	public void test009_CreateCommitOldFormat() throws IOException,
			ConfigInvalidException {
		writeTrashFile(".git/config", "[core]\n" + "legacyHeaders=1\n");
		db.getConfig().load();

		final Tree t = new Tree(db);
		final FileTreeEntry f = t.addFile("i-am-a-file");
		writeTrashFile(f.getName(), "and this is the data in me\n");
		t.accept(new WriteTree(trash, db), TreeEntry.MODIFIED_ONLY);
		assertEquals(ObjectId.fromString("00b1f73724f493096d1ffa0b0f1f1482dbb8c936"),
				t.getTreeId());

		final Commit c = new Commit(db);
		c.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c.setMessage("A Commit\n");
		c.setTree(t);
		assertEquals(t.getTreeId(), c.getTreeId());
		c.commit();
		final ObjectId cmtid = ObjectId.fromString(
				"803aec4aba175e8ab1d666873c984c0308179099");
		assertEquals(cmtid, c.getCommitId());

		// Verify the commit we just wrote is in the correct format.
		final XInputStream xis = new XInputStream(new FileInputStream(db
				.toFile(cmtid)));
		try {
			assertEquals(0x78, xis.readUInt8());
			assertEquals(0x9c, xis.readUInt8());
			assertTrue(0x789c % 31 == 0);
		} finally {
			xis.close();
		}

		// Verify we can read it.
		final Commit c2 = db.mapCommit(cmtid);
		assertNotNull(c2);
		assertEquals(c.getMessage(), c2.getMessage());
		assertEquals(c.getTreeId(), c2.getTreeId());
		assertEquals(c.getAuthor(), c2.getAuthor());
		assertEquals(c.getCommitter(), c2.getCommitter());
	}

	public void test012_SubtreeExternalSorting() throws IOException {
		final ObjectId emptyBlob = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tree t = new Tree(db);
		final FileTreeEntry e0 = t.addFile("a-");
		final FileTreeEntry e1 = t.addFile("a-b");
		final FileTreeEntry e2 = t.addFile("a/b");
		final FileTreeEntry e3 = t.addFile("a=");
		final FileTreeEntry e4 = t.addFile("a=b");

		e0.setId(emptyBlob);
		e1.setId(emptyBlob);
		e2.setId(emptyBlob);
		e3.setId(emptyBlob);
		e4.setId(emptyBlob);

		t.accept(new WriteTree(trash, db), TreeEntry.MODIFIED_ONLY);
		assertEquals(ObjectId.fromString("b47a8f0a4190f7572e11212769090523e23eb1ea"),
				t.getId());
	}

	public void test020_createBlobTag() throws IOException {
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tag t = new Tag(db);
		t.setObjId(emptyId);
		t.setType("blob");
		t.setTag("test020");
		t.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test020 tagged\n");
		t.tag();
		assertEquals("6759556b09fbb4fd8ae5e315134481cc25d46954", t.getTagId().name());

		Tag mapTag = db.mapTag("test020");
		assertEquals("blob", mapTag.getType());
		assertEquals("test020 tagged\n", mapTag.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag.getAuthor());
		assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", mapTag.getObjId().name());
	}

	public void test020b_createBlobPlainTag() throws IOException {
		test020_createBlobTag();
		Tag t = new Tag(db);
		t.setTag("test020b");
		t.setObjId(ObjectId.fromString("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"));
		t.tag();

		Tag mapTag = db.mapTag("test020b");
		assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", mapTag.getObjId().name());

		// We do not repeat the plain tag test for other object types
	}

	public void test021_createTreeTag() throws IOException {
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tree almostEmptyTree = new Tree(db);
		almostEmptyTree.addEntry(new FileTreeEntry(almostEmptyTree, emptyId, "empty".getBytes(), false));
		final ObjectId almostEmptyTreeId = new ObjectWriter(db).writeTree(almostEmptyTree);
		final Tag t = new Tag(db);
		t.setObjId(almostEmptyTreeId);
		t.setType("tree");
		t.setTag("test021");
		t.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test021 tagged\n");
		t.tag();
		assertEquals("b0517bc8dbe2096b419d42424cd7030733f4abe5", t.getTagId().name());

		Tag mapTag = db.mapTag("test021");
		assertEquals("tree", mapTag.getType());
		assertEquals("test021 tagged\n", mapTag.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag.getAuthor());
		assertEquals("417c01c8795a35b8e835113a85a5c0c1c77f67fb", mapTag.getObjId().name());
	}

	public void test022_createCommitTag() throws IOException {
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tree almostEmptyTree = new Tree(db);
		almostEmptyTree.addEntry(new FileTreeEntry(almostEmptyTree, emptyId, "empty".getBytes(), false));
		final ObjectId almostEmptyTreeId = new ObjectWriter(db).writeTree(almostEmptyTree);
		final Commit almostEmptyCommit = new Commit(db);
		almostEmptyCommit.setAuthor(new PersonIdent(author, 1154236443000L, -2 * 60)); // not exactly the same
		almostEmptyCommit.setCommitter(new PersonIdent(author, 1154236443000L, -2 * 60));
		almostEmptyCommit.setMessage("test022\n");
		almostEmptyCommit.setTreeId(almostEmptyTreeId);
		ObjectId almostEmptyCommitId = new ObjectWriter(db).writeCommit(almostEmptyCommit);
		final Tag t = new Tag(db);
		t.setObjId(almostEmptyCommitId);
		t.setType("commit");
		t.setTag("test022");
		t.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test022 tagged\n");
		t.tag();
		assertEquals("0ce2ebdb36076ef0b38adbe077a07d43b43e3807", t.getTagId().name());

		Tag mapTag = db.mapTag("test022");
		assertEquals("commit", mapTag.getType());
		assertEquals("test022 tagged\n", mapTag.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag.getAuthor());
		assertEquals("b5d3b45a96b340441f5abb9080411705c51cc86c", mapTag.getObjId().name());
	}

	public void test023_createCommitNonAnullii() throws IOException {
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tree almostEmptyTree = new Tree(db);
		almostEmptyTree.addEntry(new FileTreeEntry(almostEmptyTree, emptyId, "empty".getBytes(), false));
		final ObjectId almostEmptyTreeId = new ObjectWriter(db).writeTree(almostEmptyTree);
		Commit commit = new Commit(db);
		commit.setTreeId(almostEmptyTreeId);
		commit.setAuthor(new PersonIdent("Joe H\u00e4cker","joe@example.com",4294967295000L,60));
		commit.setCommitter(new PersonIdent("Joe Hacker","joe2@example.com",4294967295000L,60));
		commit.setEncoding("UTF-8");
		commit.setMessage("\u00dcbergeeks");
		ObjectId cid = new ObjectWriter(db).writeCommit(commit);
		assertEquals("4680908112778718f37e686cbebcc912730b3154", cid.name());
		Commit loadedCommit = db.mapCommit(cid);
		assertNotSame(loadedCommit, commit);
		assertEquals(commit.getMessage(), loadedCommit.getMessage());
	}

	public void test024_createCommitNonAscii() throws IOException {
		final ObjectId emptyId = new ObjectWriter(db).writeBlob(new byte[0]);
		final Tree almostEmptyTree = new Tree(db);
		almostEmptyTree.addEntry(new FileTreeEntry(almostEmptyTree, emptyId, "empty".getBytes(), false));
		final ObjectId almostEmptyTreeId = new ObjectWriter(db).writeTree(almostEmptyTree);
		Commit commit = new Commit(db);
		commit.setTreeId(almostEmptyTreeId);
		commit.setAuthor(new PersonIdent("Joe H\u00e4cker","joe@example.com",4294967295000L,60));
		commit.setCommitter(new PersonIdent("Joe Hacker","joe2@example.com",4294967295000L,60));
		commit.setEncoding("ISO-8859-1");
		commit.setMessage("\u00dcbergeeks");
		ObjectId cid = new ObjectWriter(db).writeCommit(commit);
		assertEquals("2979b39d385014b33287054b87f77bcb3ecb5ebf", cid.name());
	}

	public void test025_packedRefs() throws IOException {
		test020_createBlobTag();
		test021_createTreeTag();
		test022_createCommitTag();

		if (!new File(db.getDirectory(),"refs/tags/test020").delete()) throw new Error("Cannot delete unpacked tag");
		if (!new File(db.getDirectory(),"refs/tags/test021").delete()) throw new Error("Cannot delete unpacked tag");
		if (!new File(db.getDirectory(),"refs/tags/test022").delete()) throw new Error("Cannot delete unpacked tag");

		// We cannot resolve it now, since we have no ref
		Tag mapTag20missing = db.mapTag("test020");
		assertNull(mapTag20missing);

		// Construct packed refs file
		PrintWriter w = new PrintWriter(new FileWriter(new File(db.getDirectory(), "packed-refs")));
		w.println("# packed-refs with: peeled");
		w.println("6759556b09fbb4fd8ae5e315134481cc25d46954 refs/tags/test020");
		w.println("^e69de29bb2d1d6434b8b29ae775ad8c2e48c5391");
		w.println("b0517bc8dbe2096b419d42424cd7030733f4abe5 refs/tags/test021");
		w.println("^417c01c8795a35b8e835113a85a5c0c1c77f67fb");
		w.println("0ce2ebdb36076ef0b38adbe077a07d43b43e3807 refs/tags/test022");
		w.println("^b5d3b45a96b340441f5abb9080411705c51cc86c");
		w.close();
		((RefDirectory)db.getRefDatabase()).rescan();

		Tag mapTag20 = db.mapTag("test020");
		assertNotNull("have tag test020", mapTag20);
		assertEquals("blob", mapTag20.getType());
		assertEquals("test020 tagged\n", mapTag20.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag20.getAuthor());
		assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", mapTag20.getObjId().name());

		Tag mapTag21 = db.mapTag("test021");
		assertEquals("tree", mapTag21.getType());
		assertEquals("test021 tagged\n", mapTag21.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag21.getAuthor());
		assertEquals("417c01c8795a35b8e835113a85a5c0c1c77f67fb", mapTag21.getObjId().name());

		Tag mapTag22 = db.mapTag("test022");
		assertEquals("commit", mapTag22.getType());
		assertEquals("test022 tagged\n", mapTag22.getMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag22.getAuthor());
		assertEquals("b5d3b45a96b340441f5abb9080411705c51cc86c", mapTag22.getObjId().name());
	}

	public void test025_computeSha1NoStore() throws IOException {
		byte[] data = "test025 some data, more than 16 bytes to get good coverage"
				.getBytes("ISO-8859-1");
		// TODO: but we do not test legacy header writing
		final ObjectId id = new ObjectWriter(db).computeBlobSha1(data.length,
				new ByteArrayInputStream(data));
		assertEquals("4f561df5ecf0dfbd53a0dc0f37262fef075d9dde", id.name());
	}

	public void test026_CreateCommitMultipleparents() throws IOException {
		final Tree t = new Tree(db);
		final FileTreeEntry f = t.addFile("i-am-a-file");
		writeTrashFile(f.getName(), "and this is the data in me\n");
		t.accept(new WriteTree(trash, db), TreeEntry.MODIFIED_ONLY);
		assertEquals(ObjectId.fromString("00b1f73724f493096d1ffa0b0f1f1482dbb8c936"),
				t.getTreeId());

		final Commit c1 = new Commit(db);
		c1.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c1.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c1.setMessage("A Commit\n");
		c1.setTree(t);
		assertEquals(t.getTreeId(), c1.getTreeId());
		c1.commit();
		final ObjectId cmtid1 = ObjectId.fromString(
				"803aec4aba175e8ab1d666873c984c0308179099");
		assertEquals(cmtid1, c1.getCommitId());

		final Commit c2 = new Commit(db);
		c2.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c2.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c2.setMessage("A Commit 2\n");
		c2.setTree(t);
		assertEquals(t.getTreeId(), c2.getTreeId());
		c2.setParentIds(new ObjectId[] { c1.getCommitId() } );
		c2.commit();
		final ObjectId cmtid2 = ObjectId.fromString(
				"95d068687c91c5c044fb8c77c5154d5247901553");
		assertEquals(cmtid2, c2.getCommitId());

		Commit rm2 = db.mapCommit(cmtid2);
		assertNotSame(c2, rm2); // assert the parsed objects is not from the cache
		assertEquals(c2.getAuthor(), rm2.getAuthor());
		assertEquals(c2.getCommitId(), rm2.getCommitId());
		assertEquals(c2.getMessage(), rm2.getMessage());
		assertEquals(c2.getTree().getTreeId(), rm2.getTree().getTreeId());
		assertEquals(1, rm2.getParentIds().length);
		assertEquals(c1.getCommitId(), rm2.getParentIds()[0]);

		final Commit c3 = new Commit(db);
		c3.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c3.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c3.setMessage("A Commit 3\n");
		c3.setTree(t);
		assertEquals(t.getTreeId(), c3.getTreeId());
		c3.setParentIds(new ObjectId[] { c1.getCommitId(), c2.getCommitId() });
		c3.commit();
		final ObjectId cmtid3 = ObjectId.fromString(
				"ce6e1ce48fbeeb15a83f628dc8dc2debefa066f4");
		assertEquals(cmtid3, c3.getCommitId());

		Commit rm3 = db.mapCommit(cmtid3);
		assertNotSame(c3, rm3); // assert the parsed objects is not from the cache
		assertEquals(c3.getAuthor(), rm3.getAuthor());
		assertEquals(c3.getCommitId(), rm3.getCommitId());
		assertEquals(c3.getMessage(), rm3.getMessage());
		assertEquals(c3.getTree().getTreeId(), rm3.getTree().getTreeId());
		assertEquals(2, rm3.getParentIds().length);
		assertEquals(c1.getCommitId(), rm3.getParentIds()[0]);
		assertEquals(c2.getCommitId(), rm3.getParentIds()[1]);

		final Commit c4 = new Commit(db);
		c4.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c4.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c4.setMessage("A Commit 4\n");
		c4.setTree(t);
		assertEquals(t.getTreeId(), c3.getTreeId());
		c4.setParentIds(new ObjectId[] { c1.getCommitId(), c2.getCommitId(), c3.getCommitId() });
		c4.commit();
		final ObjectId cmtid4 = ObjectId.fromString(
				"d1fca9fe3fef54e5212eb67902c8ed3e79736e27");
		assertEquals(cmtid4, c4.getCommitId());

		Commit rm4 = db.mapCommit(cmtid4);
		assertNotSame(c4, rm3); // assert the parsed objects is not from the cache
		assertEquals(c4.getAuthor(), rm4.getAuthor());
		assertEquals(c4.getCommitId(), rm4.getCommitId());
		assertEquals(c4.getMessage(), rm4.getMessage());
		assertEquals(c4.getTree().getTreeId(), rm4.getTree().getTreeId());
		assertEquals(3, rm4.getParentIds().length);
		assertEquals(c1.getCommitId(), rm4.getParentIds()[0]);
		assertEquals(c2.getCommitId(), rm4.getParentIds()[1]);
		assertEquals(c3.getCommitId(), rm4.getParentIds()[2]);
	}

	public void test027_UnpackedRefHigherPriorityThanPacked() throws IOException {
		PrintWriter writer = new PrintWriter(new FileWriter(new File(db.getDirectory(), "refs/heads/a")));
		String unpackedId = "7f822839a2fe9760f386cbbbcb3f92c5fe81def7";
		writer.print(unpackedId);
		writer.print('\n');
		writer.close();

		ObjectId resolved = db.resolve("refs/heads/a");
		assertEquals(unpackedId, resolved.name());
	}

	public void test028_LockPackedRef() throws IOException {
		writeTrashFile(".git/packed-refs", "7f822839a2fe9760f386cbbbcb3f92c5fe81def7 refs/heads/foobar");
		writeTrashFile(".git/HEAD", "ref: refs/heads/foobar\n");
		BUG_WorkAroundRacyGitIssues("packed-refs");
		BUG_WorkAroundRacyGitIssues("HEAD");

		ObjectId resolve = db.resolve("HEAD");
		assertEquals("7f822839a2fe9760f386cbbbcb3f92c5fe81def7", resolve.name());

		RefUpdate lockRef = db.updateRef("HEAD");
		ObjectId newId = ObjectId.fromString("07f822839a2fe9760f386cbbbcb3f92c5fe81def");
		lockRef.setNewObjectId(newId);
		assertEquals(RefUpdate.Result.FORCED, lockRef.forceUpdate());

		assertTrue(new File(db.getDirectory(), "refs/heads/foobar").exists());
		assertEquals(newId, db.resolve("refs/heads/foobar"));

		// Again. The ref already exists
		RefUpdate lockRef2 = db.updateRef("HEAD");
		ObjectId newId2 = ObjectId.fromString("7f822839a2fe9760f386cbbbcb3f92c5fe81def7");
		lockRef2.setNewObjectId(newId2);
		assertEquals(RefUpdate.Result.FORCED, lockRef2.forceUpdate());

		assertTrue(new File(db.getDirectory(), "refs/heads/foobar").exists());
		assertEquals(newId2, db.resolve("refs/heads/foobar"));
	}

	public void test029_mapObject() throws IOException {
		assertEquals(new byte[0].getClass(), db.mapObject(ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259"), null).getClass());
		assertEquals(Commit.class, db.mapObject(ObjectId.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab"), null).getClass());
		assertEquals(Tree.class, db.mapObject(ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035"), null).getClass());
		assertEquals(Tag.class, db.mapObject(ObjectId.fromString("17768080a2318cd89bba4c8b87834401e2095703"), null).getClass());
	}

	public void test30_stripWorkDir() {
		File relCwd = new File(".");
		File absCwd = relCwd.getAbsoluteFile();
		File absBase = new File(new File(absCwd, "repo"), "workdir");
		File relBase = new File(new File(relCwd, "repo"), "workdir");
		assertEquals(absBase.getAbsolutePath(), relBase.getAbsolutePath());

		File relBaseFile = new File(new File(relBase, "other"), "module.c");
		File absBaseFile = new File(new File(absBase, "other"), "module.c");
		assertEquals("other/module.c", Repository.stripWorkDir(relBase, relBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(relBase, absBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(absBase, relBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(absBase, absBaseFile));

		File relNonFile = new File(new File(relCwd, "not-repo"), ".gitignore");
		File absNonFile = new File(new File(absCwd, "not-repo"), ".gitignore");
		assertEquals("", Repository.stripWorkDir(relBase, relNonFile));
		assertEquals("", Repository.stripWorkDir(absBase, absNonFile));

		assertEquals("", Repository.stripWorkDir(db.getWorkDir(), db.getWorkDir()));

		File file = new File(new File(db.getWorkDir(), "subdir"), "File.java");
		assertEquals("subdir/File.java", Repository.stripWorkDir(db.getWorkDir(), file));

	}

	/**
	 * Kick the timestamp of a local file.
	 * <p>
	 * We shouldn't have to make these method calls. The cache is using file
	 * system timestamps, and on many systems unit tests run faster than the
	 * modification clock. Dumping the cache after we make an edit behind
	 * RefDirectory's back allows the tests to pass.
	 *
	 * @param name
	 *            the file in the repository to force a time change on.
	 */
	private void BUG_WorkAroundRacyGitIssues(String name) {
		File path = new File(db.getDirectory(), name);
		long old = path.lastModified();
		long set = 1250379778668L; // Sat Aug 15 20:12:58 GMT-03:30 2009
		path.setLastModified(set);
		assertTrue("time changed", old != path.lastModified());
	}
}
