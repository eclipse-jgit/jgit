/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class T0003_BasicTest extends SampleDataRepositoryTestCase {
	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void test001_Initalize() {
		final File gitdir = new File(trash, Constants.DOT_GIT);
		final File hooks = new File(gitdir, "hooks");
		final File objects = new File(gitdir, "objects");
		final File objects_pack = new File(objects, "pack");
		final File objects_info = new File(objects, "info");
		final File refs = new File(gitdir, "refs");
		final File refs_heads = new File(refs, "heads");
		final File refs_tags = new File(refs, "tags");
		final File HEAD = new File(gitdir, "HEAD");

		assertTrue("Exists " + trash, trash.isDirectory());
		assertTrue("Exists " + hooks, hooks.isDirectory());
		assertTrue("Exists " + objects, objects.isDirectory());
		assertTrue("Exists " + objects_pack, objects_pack.isDirectory());
		assertTrue("Exists " + objects_info, objects_info.isDirectory());
		assertEquals(2L, objects.listFiles().length);
		assertTrue("Exists " + refs, refs.isDirectory());
		assertTrue("Exists " + refs_heads, refs_heads.isDirectory());
		assertTrue("Exists " + refs_tags, refs_tags.isDirectory());
		assertTrue("Exists " + HEAD, HEAD.isFile());
		assertEquals(23, HEAD.length());
	}

	@Test
	public void test000_openRepoBadArgs() throws IOException {
		try {
			new FileRepositoryBuilder().build();
			fail("Must pass either GIT_DIR or GIT_WORK_TREE");
		} catch (IllegalArgumentException e) {
			assertEquals(JGitText.get().eitherGitDirOrWorkTreeRequired, e
					.getMessage());
		}
	}

	/**
	 * Check the default rules for looking up directories and files within a
	 * repo when the gitDir is given.
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_default_gitDirSet() throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		try (Repository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		FileRepository r = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(theDir).build();
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent, r.getWorkTree());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectDatabase()
				.getDirectory());
	}

	/**
	 * Check that we can pass both a git directory and a work tree repo when the
	 * gitDir is given.
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_default_gitDirAndWorkTreeSet()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		try (Repository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		FileRepository r = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(theDir).setWorkTree(repo1Parent.getParentFile())
				.build();
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent.getParentFile(), r.getWorkTree());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectDatabase()
				.getDirectory());
	}

	/**
	 * Check the default rules for looking up directories and files within a
	 * repo when the workTree is given.
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_default_workDirSet() throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		try (Repository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		FileRepository r = (FileRepository) new FileRepositoryBuilder()
				.setWorkTree(repo1Parent).build();
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(repo1Parent, r.getWorkTree());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectDatabase()
				.getDirectory());
	}

	/**
	 * Check that worktree config has an effect, given absolute path.
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_default_absolute_workdirconfig()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File workdir = new File(trash.getParentFile(), "rw");
		FileUtils.mkdir(workdir);
		try (FileRepository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
			final FileBasedConfig cfg = repo1initial.getConfig();
			cfg.setString("core", null, "worktree", workdir.getAbsolutePath());
			cfg.save();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		FileRepository r = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(theDir).build();
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(workdir, r.getWorkTree());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectDatabase()
				.getDirectory());
	}

	/**
	 * Check that worktree config has an effect, given a relative path.
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_default_relative_workdirconfig()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File workdir = new File(trash.getParentFile(), "rw");
		FileUtils.mkdir(workdir);
		try (FileRepository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
			final FileBasedConfig cfg = repo1initial.getConfig();
			cfg.setString("core", null, "worktree", "../../rw");
			cfg.save();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		FileRepository r = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(theDir).build();
		assertEqualsPath(theDir, r.getDirectory());
		assertEqualsPath(workdir, r.getWorkTree());
		assertEqualsPath(new File(theDir, "index"), r.getIndexFile());
		assertEqualsPath(new File(theDir, "objects"), r.getObjectDatabase()
				.getDirectory());
	}

	/**
	 * Check that the given index file is honored and the alternate object
	 * directories too
	 *
	 * @throws IOException
	 */
	@Test
	public void test000_openrepo_alternate_index_file_and_objdirs()
			throws IOException {
		File repo1Parent = new File(trash.getParentFile(), "r1");
		File indexFile = new File(trash, "idx");
		File objDir = new File(trash, "../obj");
		File altObjDir = db.getObjectDatabase().getDirectory();
		try (Repository repo1initial = new FileRepository(
				new File(repo1Parent, Constants.DOT_GIT))) {
			repo1initial.create();
		}

		File theDir = new File(repo1Parent, Constants.DOT_GIT);
		try (FileRepository r = (FileRepository) new FileRepositoryBuilder() //
				.setGitDir(theDir).setObjectDirectory(objDir) //
				.addAlternateObjectDirectory(altObjDir) //
				.setIndexFile(indexFile) //
				.build()) {
			assertEqualsPath(theDir, r.getDirectory());
			assertEqualsPath(theDir.getParentFile(), r.getWorkTree());
			assertEqualsPath(indexFile, r.getIndexFile());
			assertEqualsPath(objDir, r.getObjectDatabase().getDirectory());
			assertNotNull(r.open(ObjectId
					.fromString("6db9c2ebf75590eef973081736730a9ea169a0c4")));
		}
	}

	protected void assertEqualsPath(File expected, File actual)
			throws IOException {
		assertEquals(expected.getCanonicalPath(), actual.getCanonicalPath());
	}

	@Test
	public void test002_WriteEmptyTree() throws IOException {
		// One of our test packs contains the empty tree object. If the pack is
		// open when we create it we won't write the object file out as a loose
		// object (as it already exists in the pack).
		//
		final Repository newdb = createBareRepository();
		try (ObjectInserter oi = newdb.newObjectInserter()) {
			final ObjectId treeId = oi.insert(new TreeFormatter());
			assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904",
					treeId.name());
		}

		final File o = new File(new File(new File(newdb.getDirectory(),
				"objects"), "4b"), "825dc642cb6eb9a060e54bf8d69288fbee4904");
		assertTrue("Exists " + o, o.isFile());
		assertTrue("Read-only " + o, !o.canWrite());
	}

	@Test
	public void test002_WriteEmptyTree2() throws IOException {
		// File shouldn't exist as it is in a test pack.
		//
		final ObjectId treeId = insertTree(new TreeFormatter());
		assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", treeId.name());
		final File o = new File(new File(
				new File(db.getDirectory(), "objects"), "4b"),
				"825dc642cb6eb9a060e54bf8d69288fbee4904");
		assertFalse("Exists " + o, o.isFile());
	}

	@Test
	public void test002_CreateBadTree() throws Exception {
		// We won't create a tree entry with an empty filename
		//
		final TreeFormatter formatter = new TreeFormatter();
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage(JGitText.get().invalidTreeZeroLengthName);
		formatter.append("", FileMode.TREE,
				ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));
	}

	@Test
	public void test006_ReadUglyConfig() throws IOException,
			ConfigInvalidException {
		final File cfg = new File(db.getDirectory(), Constants.CONFIG);
		final FileBasedConfig c = new FileBasedConfig(cfg, db.getFS());
		final String configStr = "  [core];comment\n\tfilemode = yes\n"
				+ "[user]\n"
				+ "  email = A U Thor <thor@example.com> # Just an example...\n"
				+ " name = \"A  Thor \\\\ \\\"\\t \"\n"
				+ "    defaultCheckInComment = a many line\\n\\\ncomment\\n\\\n"
				+ " to test\n";
		write(cfg, configStr);
		c.load();
		assertEquals("yes", c.getString("core", null, "filemode"));
		assertEquals("A U Thor <thor@example.com>", c.getString("user", null,
				"email"));
		assertEquals("A  Thor \\ \"\t ", c.getString("user", null, "name"));
		assertEquals("a many line\ncomment\n to test", c.getString("user",
				null, "defaultCheckInComment"));
		c.save();

		// Saving normalizes out the weird "\\n\\\n" to a single escaped newline,
		// and quotes the whole string.
		final String expectedStr = "  [core];comment\n\tfilemode = yes\n"
				+ "[user]\n"
				+ "  email = A U Thor <thor@example.com> # Just an example...\n"
				+ " name = \"A  Thor \\\\ \\\"\\t \"\n"
				+ "    defaultCheckInComment = a many line\\ncomment\\n to test\n";
		assertEquals(expectedStr, new String(IO.readFully(cfg), UTF_8));
	}

	@Test
	public void test007_Open() throws IOException {
		try (FileRepository db2 = new FileRepository(db.getDirectory())) {
			assertEquals(db.getDirectory(), db2.getDirectory());
			assertEquals(db.getObjectDatabase().getDirectory(), db2
					.getObjectDatabase().getDirectory());
			assertNotSame(db.getConfig(), db2.getConfig());
		}
	}

	@Test
	public void test008_FailOnWrongVersion() throws IOException {
		final File cfg = new File(db.getDirectory(), Constants.CONFIG);
		final String badvers = "ihopethisisneveraversion";
		final String configStr = "[core]\n" + "\trepositoryFormatVersion="
				+ badvers + "\n";
		write(cfg, configStr);

		try (FileRepository unused = new FileRepository(db.getDirectory())) {
			fail("incorrectly opened a bad repository");
		} catch (IllegalArgumentException ioe) {
			assertNotNull(ioe.getMessage());
		}
	}

	@Test
	public void test009_CreateCommitOldFormat() throws IOException {
		final ObjectId treeId = insertTree(new TreeFormatter());
		final CommitBuilder c = new CommitBuilder();
		c.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c.setMessage("A Commit\n");
		c.setTreeId(treeId);
		assertEquals(treeId, c.getTreeId());

		ObjectId actid = insertCommit(c);

		final ObjectId cmtid = ObjectId
				.fromString("9208b2459ea6609a5af68627cc031796d0d9329b");
		assertEquals(cmtid, actid);

		// Verify the commit we just wrote is in the correct format.
		ObjectDatabase odb = db.getObjectDatabase();
		assertTrue("is ObjectDirectory", odb instanceof ObjectDirectory);
		try (XInputStream xis = new XInputStream(
				new FileInputStream(((ObjectDirectory) odb).fileFor(cmtid)))) {
			assertEquals(0x78, xis.readUInt8());
			assertEquals(0x9c, xis.readUInt8());
			assertEquals(0, 0x789c % 31);
		}

		// Verify we can read it.
		RevCommit c2 = parseCommit(actid);
		assertNotNull(c2);
		assertEquals(c.getMessage(), c2.getFullMessage());
		assertEquals(c.getTreeId(), c2.getTree());
		assertEquals(c.getAuthor(), c2.getAuthorIdent());
		assertEquals(c.getCommitter(), c2.getCommitterIdent());
	}

	@Test
	public void test020_createBlobTag() throws IOException {
		final ObjectId emptyId = insertEmptyBlob();
		final TagBuilder t = new TagBuilder();
		t.setObjectId(emptyId, Constants.OBJ_BLOB);
		t.setTag("test020");
		t.setTagger(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test020 tagged\n");
		ObjectId actid = insertTag(t);
		assertEquals("6759556b09fbb4fd8ae5e315134481cc25d46954", actid.name());

		RevTag mapTag = parseTag(actid);
		assertEquals(Constants.OBJ_BLOB, mapTag.getObject().getType());
		assertEquals("test020 tagged\n", mapTag.getFullMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag
				.getTaggerIdent());
		assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", mapTag
				.getObject().getId().name());
	}

	@Test
	public void test021_createTreeTag() throws IOException {
		final ObjectId emptyId = insertEmptyBlob();
		TreeFormatter almostEmptyTree = new TreeFormatter();
		almostEmptyTree.append("empty", FileMode.REGULAR_FILE, emptyId);
		final ObjectId almostEmptyTreeId = insertTree(almostEmptyTree);
		final TagBuilder t = new TagBuilder();
		t.setObjectId(almostEmptyTreeId, Constants.OBJ_TREE);
		t.setTag("test021");
		t.setTagger(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test021 tagged\n");
		ObjectId actid = insertTag(t);
		assertEquals("b0517bc8dbe2096b419d42424cd7030733f4abe5", actid.name());

		RevTag mapTag = parseTag(actid);
		assertEquals(Constants.OBJ_TREE, mapTag.getObject().getType());
		assertEquals("test021 tagged\n", mapTag.getFullMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag
				.getTaggerIdent());
		assertEquals("417c01c8795a35b8e835113a85a5c0c1c77f67fb", mapTag
				.getObject().getId().name());
	}

	@Test
	public void test022_createCommitTag() throws IOException {
		final ObjectId emptyId = insertEmptyBlob();
		TreeFormatter almostEmptyTree = new TreeFormatter();
		almostEmptyTree.append("empty", FileMode.REGULAR_FILE, emptyId);
		final ObjectId almostEmptyTreeId = insertTree(almostEmptyTree);
		final CommitBuilder almostEmptyCommit = new CommitBuilder();
		almostEmptyCommit.setAuthor(new PersonIdent(author, 1154236443000L,
				-2 * 60)); // not exactly the same
		almostEmptyCommit.setCommitter(new PersonIdent(author, 1154236443000L,
				-2 * 60));
		almostEmptyCommit.setMessage("test022\n");
		almostEmptyCommit.setTreeId(almostEmptyTreeId);
		ObjectId almostEmptyCommitId = insertCommit(almostEmptyCommit);
		final TagBuilder t = new TagBuilder();
		t.setObjectId(almostEmptyCommitId, Constants.OBJ_COMMIT);
		t.setTag("test022");
		t.setTagger(new PersonIdent(author, 1154236443000L, -4 * 60));
		t.setMessage("test022 tagged\n");
		ObjectId actid = insertTag(t);
		assertEquals("0ce2ebdb36076ef0b38adbe077a07d43b43e3807", actid.name());

		RevTag mapTag = parseTag(actid);
		assertEquals(Constants.OBJ_COMMIT, mapTag.getObject().getType());
		assertEquals("test022 tagged\n", mapTag.getFullMessage());
		assertEquals(new PersonIdent(author, 1154236443000L, -4 * 60), mapTag
				.getTaggerIdent());
		assertEquals("b5d3b45a96b340441f5abb9080411705c51cc86c", mapTag
				.getObject().getId().name());
	}

	@Test
	public void test023_createCommitNonAnullii() throws IOException {
		final ObjectId emptyId = insertEmptyBlob();
		TreeFormatter almostEmptyTree = new TreeFormatter();
		almostEmptyTree.append("empty", FileMode.REGULAR_FILE, emptyId);
		final ObjectId almostEmptyTreeId = insertTree(almostEmptyTree);
		CommitBuilder commit = new CommitBuilder();
		commit.setTreeId(almostEmptyTreeId);
		commit.setAuthor(new PersonIdent("Joe H\u00e4cker", "joe@example.com",
				4294967295000L, 60));
		commit.setCommitter(new PersonIdent("Joe Hacker", "joe2@example.com",
				4294967295000L, 60));
		commit.setEncoding(UTF_8);
		commit.setMessage("\u00dcbergeeks");
		ObjectId cid = insertCommit(commit);
		assertEquals("4680908112778718f37e686cbebcc912730b3154", cid.name());

		RevCommit loadedCommit = parseCommit(cid);
		assertEquals(commit.getMessage(), loadedCommit.getFullMessage());
	}

	@Test
	public void test024_createCommitNonAscii() throws IOException {
		final ObjectId emptyId = insertEmptyBlob();
		TreeFormatter almostEmptyTree = new TreeFormatter();
		almostEmptyTree.append("empty", FileMode.REGULAR_FILE, emptyId);
		final ObjectId almostEmptyTreeId = insertTree(almostEmptyTree);
		CommitBuilder commit = new CommitBuilder();
		commit.setTreeId(almostEmptyTreeId);
		commit.setAuthor(new PersonIdent("Joe H\u00e4cker", "joe@example.com",
				4294967295000L, 60));
		commit.setCommitter(new PersonIdent("Joe Hacker", "joe2@example.com",
				4294967295000L, 60));
		commit.setEncoding("ISO-8859-1");
		commit.setMessage("\u00dcbergeeks");
		ObjectId cid = insertCommit(commit);
		assertEquals("2979b39d385014b33287054b87f77bcb3ecb5ebf", cid.name());
	}

	@Test
	public void test025_computeSha1NoStore() {
		byte[] data = "test025 some data, more than 16 bytes to get good coverage"
				.getBytes(ISO_8859_1);
		try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
			final ObjectId id = formatter.idFor(Constants.OBJ_BLOB, data);
			assertEquals("4f561df5ecf0dfbd53a0dc0f37262fef075d9dde", id.name());
		}
	}

	@Test
	public void test026_CreateCommitMultipleparents() throws IOException {
		final ObjectId treeId;
		try (ObjectInserter oi = db.newObjectInserter()) {
			final ObjectId blobId = oi.insert(Constants.OBJ_BLOB,
					"and this is the data in me\n".getBytes(UTF_8
							.name()));
			TreeFormatter fmt = new TreeFormatter();
			fmt.append("i-am-a-file", FileMode.REGULAR_FILE, blobId);
			treeId = oi.insert(fmt);
			oi.flush();
		}
		assertEquals(ObjectId
				.fromString("00b1f73724f493096d1ffa0b0f1f1482dbb8c936"), treeId);

		final CommitBuilder c1 = new CommitBuilder();
		c1.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c1.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c1.setMessage("A Commit\n");
		c1.setTreeId(treeId);
		assertEquals(treeId, c1.getTreeId());
		ObjectId actid1 = insertCommit(c1);
		final ObjectId cmtid1 = ObjectId
				.fromString("803aec4aba175e8ab1d666873c984c0308179099");
		assertEquals(cmtid1, actid1);

		final CommitBuilder c2 = new CommitBuilder();
		c2.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c2.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c2.setMessage("A Commit 2\n");
		c2.setTreeId(treeId);
		assertEquals(treeId, c2.getTreeId());
		c2.setParentIds(actid1);
		ObjectId actid2 = insertCommit(c2);
		final ObjectId cmtid2 = ObjectId
				.fromString("95d068687c91c5c044fb8c77c5154d5247901553");
		assertEquals(cmtid2, actid2);

		RevCommit rm2 = parseCommit(cmtid2);
		assertNotSame(c2, rm2); // assert the parsed objects is not from the
		// cache
		assertEquals(c2.getAuthor(), rm2.getAuthorIdent());
		assertEquals(actid2, rm2.getId());
		assertEquals(c2.getMessage(), rm2.getFullMessage());
		assertEquals(c2.getTreeId(), rm2.getTree().getId());
		assertEquals(1, rm2.getParentCount());
		assertEquals(actid1, rm2.getParent(0));

		final CommitBuilder c3 = new CommitBuilder();
		c3.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c3.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c3.setMessage("A Commit 3\n");
		c3.setTreeId(treeId);
		assertEquals(treeId, c3.getTreeId());
		c3.setParentIds(actid1, actid2);
		ObjectId actid3 = insertCommit(c3);
		final ObjectId cmtid3 = ObjectId
				.fromString("ce6e1ce48fbeeb15a83f628dc8dc2debefa066f4");
		assertEquals(cmtid3, actid3);

		RevCommit rm3 = parseCommit(cmtid3);
		assertNotSame(c3, rm3); // assert the parsed objects is not from the
		// cache
		assertEquals(c3.getAuthor(), rm3.getAuthorIdent());
		assertEquals(actid3, rm3.getId());
		assertEquals(c3.getMessage(), rm3.getFullMessage());
		assertEquals(c3.getTreeId(), rm3.getTree().getId());
		assertEquals(2, rm3.getParentCount());
		assertEquals(actid1, rm3.getParent(0));
		assertEquals(actid2, rm3.getParent(1));

		final CommitBuilder c4 = new CommitBuilder();
		c4.setAuthor(new PersonIdent(author, 1154236443000L, -4 * 60));
		c4.setCommitter(new PersonIdent(committer, 1154236443000L, -4 * 60));
		c4.setMessage("A Commit 4\n");
		c4.setTreeId(treeId);
		assertEquals(treeId, c3.getTreeId());
		c4.setParentIds(actid1, actid2, actid3);
		ObjectId actid4 = insertCommit(c4);
		final ObjectId cmtid4 = ObjectId
				.fromString("d1fca9fe3fef54e5212eb67902c8ed3e79736e27");
		assertEquals(cmtid4, actid4);

		RevCommit rm4 = parseCommit(cmtid4);
		assertNotSame(c4, rm3); // assert the parsed objects is not from the
		// cache
		assertEquals(c4.getAuthor(), rm4.getAuthorIdent());
		assertEquals(actid4, rm4.getId());
		assertEquals(c4.getMessage(), rm4.getFullMessage());
		assertEquals(c4.getTreeId(), rm4.getTree().getId());
		assertEquals(3, rm4.getParentCount());
		assertEquals(actid1, rm4.getParent(0));
		assertEquals(actid2, rm4.getParent(1));
		assertEquals(actid3, rm4.getParent(2));
	}

	@Test
	public void test027_UnpackedRefHigherPriorityThanPacked()
			throws IOException {
		String unpackedId = "7f822839a2fe9760f386cbbbcb3f92c5fe81def7";
		write(new File(db.getDirectory(), "refs/heads/a"), unpackedId + "\n");

		ObjectId resolved = db.resolve("refs/heads/a");
		assertEquals(unpackedId, resolved.name());
	}

	@Test
	public void test028_LockPackedRef() throws IOException {
		ObjectId id1;
		ObjectId id2;
		try (ObjectInserter ins = db.newObjectInserter()) {
			id1 = ins.insert(
					Constants.OBJ_BLOB, "contents1".getBytes(UTF_8));
			id2 = ins.insert(
					Constants.OBJ_BLOB, "contents2".getBytes(UTF_8));
			ins.flush();
		}

		writeTrashFile(".git/packed-refs",
				id1.name() + " refs/heads/foobar");
		writeTrashFile(".git/HEAD", "ref: refs/heads/foobar\n");
		BUG_WorkAroundRacyGitIssues("packed-refs");
		BUG_WorkAroundRacyGitIssues("HEAD");

		ObjectId resolve = db.resolve("HEAD");
		assertEquals(id1, resolve);

		RefUpdate lockRef = db.updateRef("HEAD");
		lockRef.setNewObjectId(id2);
		assertEquals(RefUpdate.Result.FORCED, lockRef.forceUpdate());

		assertTrue(new File(db.getDirectory(), "refs/heads/foobar").exists());
		assertEquals(id2, db.resolve("refs/heads/foobar"));

		// Again. The ref already exists
		RefUpdate lockRef2 = db.updateRef("HEAD");
		lockRef2.setNewObjectId(id1);
		assertEquals(RefUpdate.Result.FORCED, lockRef2.forceUpdate());

		assertTrue(new File(db.getDirectory(), "refs/heads/foobar").exists());
		assertEquals(id1, db.resolve("refs/heads/foobar"));
	}

	@Test
	public void test30_stripWorkDir() {
		File relCwd = new File(".");
		File absCwd = relCwd.getAbsoluteFile();
		File absBase = new File(new File(absCwd, "repo"), "workdir");
		File relBase = new File(new File(relCwd, "repo"), "workdir");
		assertEquals(absBase.getAbsolutePath(), relBase.getAbsolutePath());

		File relBaseFile = new File(new File(relBase, "other"), "module.c");
		File absBaseFile = new File(new File(absBase, "other"), "module.c");
		assertEquals("other/module.c", Repository.stripWorkDir(relBase,
				relBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(relBase,
				absBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(absBase,
				relBaseFile));
		assertEquals("other/module.c", Repository.stripWorkDir(absBase,
				absBaseFile));

		File relNonFile = new File(new File(relCwd, "not-repo"), ".gitignore");
		File absNonFile = new File(new File(absCwd, "not-repo"), ".gitignore");
		assertEquals("", Repository.stripWorkDir(relBase, relNonFile));
		assertEquals("", Repository.stripWorkDir(absBase, absNonFile));

		assertEquals("", Repository.stripWorkDir(db.getWorkTree(), db
				.getWorkTree()));

		File file = new File(new File(db.getWorkTree(), "subdir"), "File.java");
		assertEquals("subdir/File.java", Repository.stripWorkDir(db
				.getWorkTree(), file));

	}

	private ObjectId insertEmptyBlob() throws IOException {
		final ObjectId emptyId;
		try (ObjectInserter oi = db.newObjectInserter()) {
			emptyId = oi.insert(Constants.OBJ_BLOB, new byte[] {});
			oi.flush();
		}
		return emptyId;
	}

	private ObjectId insertTree(TreeFormatter tree) throws IOException {
		try (ObjectInserter oi = db.newObjectInserter()) {
			ObjectId id = oi.insert(tree);
			oi.flush();
			return id;
		}
	}

	private ObjectId insertCommit(CommitBuilder builder)
			throws IOException, UnsupportedEncodingException {
		try (ObjectInserter oi = db.newObjectInserter()) {
			ObjectId id = oi.insert(builder);
			oi.flush();
			return id;
		}
	}

	private RevCommit parseCommit(AnyObjectId id)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (RevWalk rw = new RevWalk(db)) {
			return rw.parseCommit(id);
		}
	}

	private ObjectId insertTag(TagBuilder tag) throws IOException,
			UnsupportedEncodingException {
		try (ObjectInserter oi = db.newObjectInserter()) {
			ObjectId id = oi.insert(tag);
			oi.flush();
			return id;
		}
	}

	private RevTag parseTag(AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		try (RevWalk rw = new RevWalk(db)) {
			return rw.parseTag(id);
		}
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
