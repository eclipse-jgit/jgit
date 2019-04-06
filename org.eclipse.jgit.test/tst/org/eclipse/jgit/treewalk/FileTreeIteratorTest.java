/*
 * Copyright (C) 2008, 2017, Google Inc.
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

package org.eclipse.jgit.treewalk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.security.MessageDigest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.WorkingTreeIterator.MetadataDiff;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class FileTreeIteratorTest extends RepositoryTestCase {
	private final String[] paths = { "a,", "a,b", "a/b", "a0b" };

	private long[] mtime;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		// We build the entries backwards so that on POSIX systems we
		// are likely to get the entries in the trash directory in the
		// opposite order of what they should be in for the iteration.
		// This should stress the sorting code better than doing it in
		// the correct order.
		//
		mtime = new long[paths.length];
		for (int i = paths.length - 1; i >= 0; i--) {
			final String s = paths[i];
			writeTrashFile(s, s);
			mtime[i] = FS.DETECTED.lastModified(new File(trash, s));
		}
	}

	@Test
	public void testGetEntryContentLength() throws Exception {
		final FileTreeIterator fti = new FileTreeIterator(db);
		fti.next(1);
		assertEquals(3, fti.getEntryContentLength());
		fti.back(1);
		assertEquals(2, fti.getEntryContentLength());
		fti.next(1);
		assertEquals(3, fti.getEntryContentLength());
		fti.reset();
		assertEquals(2, fti.getEntryContentLength());
	}

	@Test
	public void testEmptyIfRootIsFile() throws Exception {
		final File r = new File(trash, paths[0]);
		assertTrue(r.isFile());
		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testEmptyIfRootDoesNotExist() throws Exception {
		final File r = new File(trash, "not-existing-file");
		assertFalse(r.exists());
		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testEmptyIfRootIsEmpty() throws Exception {
		final File r = new File(trash, "not-existing-file");
		assertFalse(r.exists());
		FileUtils.mkdir(r);

		final FileTreeIterator fti = new FileTreeIterator(r, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(fti.first());
		assertTrue(fti.eof());
	}

	@Test
	public void testEmptyIteratorOnEmptyDirectory() throws Exception {
		String nonExistingFileName = "not-existing-file";
		final File r = new File(trash, nonExistingFileName);
		assertFalse(r.exists());
		FileUtils.mkdir(r);

		final FileTreeIterator parent = new FileTreeIterator(db);

		while (!parent.getEntryPathString().equals(nonExistingFileName))
			parent.next(1);

		final FileTreeIterator childIter = new FileTreeIterator(parent, r,
				db.getFS());
		assertTrue(childIter.first());
		assertTrue(childIter.eof());

		String parentPath = parent.getEntryPathString();
		assertEquals(nonExistingFileName, parentPath);

		// must be "not-existing-file/", but getEntryPathString() was broken by
		// 445363 too
		String childPath = childIter.getEntryPathString();

		// in bug 445363 the iterator wrote garbage to the parent "path" field
		EmptyTreeIterator e = childIter.createEmptyTreeIterator();
		assertNotNull(e);

		// check if parent path is not overridden by empty iterator (bug 445363)
		// due bug 445363 this was "/ot-existing-file" instead of
		// "not-existing-file"
		assertEquals(parentPath, parent.getEntryPathString());
		assertEquals(parentPath + "/", childPath);
		assertEquals(parentPath + "/", childIter.getEntryPathString());
		assertEquals(childPath + "/", e.getEntryPathString());
	}

	@Test
	public void testSimpleIterate() throws Exception {
		final FileTreeIterator top = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));

		assertTrue(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[0], nameOf(top));
		assertEquals(paths[0].length(), top.getEntryLength());
		assertEquals(mtime[0], top.getEntryLastModified());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[1], nameOf(top));
		assertEquals(paths[1].length(), top.getEntryLength());
		assertEquals(mtime[1], top.getEntryLastModified());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.TREE.getBits(), top.mode);

		final ObjectReader reader = db.newObjectReader();
		final AbstractTreeIterator sub = top.createSubtreeIterator(reader);
		assertTrue(sub instanceof FileTreeIterator);
		final FileTreeIterator subfti = (FileTreeIterator) sub;
		assertTrue(sub.first());
		assertFalse(sub.eof());
		assertEquals(paths[2], nameOf(sub));
		assertEquals(paths[2].length(), subfti.getEntryLength());
		assertEquals(mtime[2], subfti.getEntryLastModified());

		sub.next(1);
		assertTrue(sub.eof());

		top.next(1);
		assertFalse(top.first());
		assertFalse(top.eof());
		assertEquals(FileMode.REGULAR_FILE.getBits(), top.mode);
		assertEquals(paths[3], nameOf(top));
		assertEquals(paths[3].length(), top.getEntryLength());
		assertEquals(mtime[3], top.getEntryLastModified());

		top.next(1);
		assertTrue(top.eof());
	}

	@Test
	public void testComputeFileObjectId() throws Exception {
		final FileTreeIterator top = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));

		final MessageDigest md = Constants.newMessageDigest();
		md.update(Constants.encodeASCII(Constants.TYPE_BLOB));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(paths[0].length()));
		md.update((byte) 0);
		md.update(Constants.encode(paths[0]));
		final ObjectId expect = ObjectId.fromRaw(md.digest());

		assertEquals(expect, top.getEntryObjectId());

		// Verify it was cached by removing the file and getting it again.
		//
		FileUtils.delete(new File(trash, paths[0]));
		assertEquals(expect, top.getEntryObjectId());
	}

	@Test
	public void testDirCacheMatchingId() throws Exception {
		File f = writeTrashFile("file", "content");
		try (Git git = new Git(db)) {
			writeTrashFile("file", "content");
			fsTick(f);
			git.add().addFilepattern("file").call();
		}
		DirCacheEntry dce = db.readDirCache().getEntry("file");
		TreeWalk tw = new TreeWalk(db);
		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		tw.addTree(fti);
		DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
		tw.addTree(dci);
		fti.setDirCacheIterator(tw, 1);
		while (tw.next() && !tw.getPathString().equals("file")) {
			//
		}
		assertEquals(MetadataDiff.EQUAL, fti.compareMetadata(dce));
		ObjectId fromRaw = ObjectId.fromRaw(fti.idBuffer(), fti.idOffset());
		assertEquals("6b584e8ece562ebffc15d38808cd6b98fc3d97ea",
				fromRaw.getName());
		try (ObjectReader objectReader = db.newObjectReader()) {
			assertFalse(fti.isModified(dce, false, objectReader));
		}
	}

	@Test
	public void testTreewalkEnterSubtree() throws Exception {
		try (Git git = new Git(db); TreeWalk tw = new TreeWalk(db)) {
			writeTrashFile("b/c", "b/c");
			writeTrashFile("z/.git", "gitdir: /tmp/somewhere");
			git.add().addFilepattern(".").call();
			git.rm().addFilepattern("a,").addFilepattern("a,b")
					.addFilepattern("a0b").call();
			assertEquals("[a/b, mode:100644][b/c, mode:100644][z, mode:160000]",
					indexState(0));
			FileUtils.delete(new File(db.getWorkTree(), "b"),
					FileUtils.RECURSIVE);

			tw.addTree(new DirCacheIterator(db.readDirCache()));
			tw.addTree(new FileTreeIterator(db));
			assertTrue(tw.next());
			assertEquals("a", tw.getPathString());
			tw.enterSubtree();
			tw.next();
			assertEquals("a/b", tw.getPathString());
			tw.next();
			assertEquals("b", tw.getPathString());
			tw.enterSubtree();
			tw.next();
			assertEquals("b/c", tw.getPathString());
			assertNotNull(tw.getTree(0, AbstractTreeIterator.class));
			assertNotNull(tw.getTree(EmptyTreeIterator.class));
		}
	}

	@Test
	public void testIsModifiedSymlinkAsFile() throws Exception {
		writeTrashFile("symlink", "content");
		try (Git git = new Git(db)) {
			db.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_SYMLINKS, "false");
			git.add().addFilepattern("symlink").call();
			git.commit().setMessage("commit").call();
		}

		// Modify previously committed DirCacheEntry and write it back to disk
		DirCacheEntry dce = db.readDirCache().getEntry("symlink");
		dce.setFileMode(FileMode.SYMLINK);
		try (ObjectReader objectReader = db.newObjectReader()) {
			DirCacheCheckout.checkoutEntry(db, dce, objectReader, false, null);

			FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(),
					db.getConfig().get(WorkingTreeOptions.KEY));
			while (!fti.getEntryPathString().equals("symlink"))
				fti.next(1);
			assertFalse(fti.isModified(dce, false, objectReader));
		}
	}

	@Test
	public void testIsModifiedFileSmudged() throws Exception {
		File f = writeTrashFile("file", "content");
		try (Git git = new Git(db)) {
			// The idea of this test is to check the smudged handling
			// Hopefully fsTick will make sure our entry gets smudged
			fsTick(f);
			writeTrashFile("file", "content");
			long lastModified = f.lastModified();
			git.add().addFilepattern("file").call();
			writeTrashFile("file", "conten2");
			f.setLastModified(lastModified);
			// We cannot trust this to go fast enough on
			// a system with less than one-second lastModified
			// resolution, so we force the index to have the
			// same timestamp as the file we look at.
			db.getIndexFile().setLastModified(lastModified);
		}
		DirCacheEntry dce = db.readDirCache().getEntry("file");
		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		while (!fti.getEntryPathString().equals("file"))
			fti.next(1);
		// If the rounding trick does not work we could skip the compareMetaData
		// test and hope that we are usually testing the intended code path.
		assertEquals(MetadataDiff.SMUDGED, fti.compareMetadata(dce));
		try (ObjectReader objectReader = db.newObjectReader()) {
			assertTrue(fti.isModified(dce, false, objectReader));
		}
	}

	@Test
	public void submoduleHeadMatchesIndex() throws Exception {
		try (Git git = new Git(db); TreeWalk walk = new TreeWalk(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			final RevCommit id = git.commit().setMessage("create file").call();
			final String path = "sub";
			DirCache cache = db.lockDirCache();
			DirCacheEditor editor = cache.editor();
			editor.add(new PathEdit(path) {

				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.GITLINK);
					ent.setObjectId(id);
				}
			});
			editor.commit();

			Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
					.setDirectory(new File(db.getWorkTree(), path)).call()
					.getRepository().close();

			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(db);
			walk.addTree(indexIter);
			walk.addTree(workTreeIter);
			walk.setFilter(PathFilter.create(path));

			assertTrue(walk.next());
			assertTrue(indexIter.idEqual(workTreeIter));
		}
	}

	@Test
	public void submoduleWithNoGitDirectory() throws Exception {
		try (Git git = new Git(db); TreeWalk walk = new TreeWalk(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			final RevCommit id = git.commit().setMessage("create file").call();
			final String path = "sub";
			DirCache cache = db.lockDirCache();
			DirCacheEditor editor = cache.editor();
			editor.add(new PathEdit(path) {

				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.GITLINK);
					ent.setObjectId(id);
				}
			});
			editor.commit();

			File submoduleRoot = new File(db.getWorkTree(), path);
			assertTrue(submoduleRoot.mkdir());
			assertTrue(new File(submoduleRoot, Constants.DOT_GIT).mkdir());

			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(db);
			walk.addTree(indexIter);
			walk.addTree(workTreeIter);
			walk.setFilter(PathFilter.create(path));

			assertTrue(walk.next());
			assertFalse(indexIter.idEqual(workTreeIter));
			assertEquals(ObjectId.zeroId(), workTreeIter.getEntryObjectId());
		}
	}

	@Test
	public void submoduleWithNoHead() throws Exception {
		try (Git git = new Git(db); TreeWalk walk = new TreeWalk(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			final RevCommit id = git.commit().setMessage("create file").call();
			final String path = "sub";
			DirCache cache = db.lockDirCache();
			DirCacheEditor editor = cache.editor();
			editor.add(new PathEdit(path) {

				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.GITLINK);
					ent.setObjectId(id);
				}
			});
			editor.commit();

			assertNotNull(
					Git.init().setDirectory(new File(db.getWorkTree(), path))
							.call().getRepository());

			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(db);
			walk.addTree(indexIter);
			walk.addTree(workTreeIter);
			walk.setFilter(PathFilter.create(path));

			assertTrue(walk.next());
			assertFalse(indexIter.idEqual(workTreeIter));
			assertEquals(ObjectId.zeroId(), workTreeIter.getEntryObjectId());
		}
	}

	@Test
	public void submoduleDirectoryIterator() throws Exception {
		try (Git git = new Git(db); TreeWalk walk = new TreeWalk(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			final RevCommit id = git.commit().setMessage("create file").call();
			final String path = "sub";
			DirCache cache = db.lockDirCache();
			DirCacheEditor editor = cache.editor();
			editor.add(new PathEdit(path) {

				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.GITLINK);
					ent.setObjectId(id);
				}
			});
			editor.commit();

			Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
					.setDirectory(new File(db.getWorkTree(), path)).call()
					.getRepository().close();

			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(
					db.getWorkTree(), db.getFS(),
					db.getConfig().get(WorkingTreeOptions.KEY));
			walk.addTree(indexIter);
			walk.addTree(workTreeIter);
			walk.setFilter(PathFilter.create(path));

			assertTrue(walk.next());
			assertTrue(indexIter.idEqual(workTreeIter));
		}
	}

	@Test
	public void submoduleNestedWithHeadMatchingIndex() throws Exception {
		try (Git git = new Git(db); TreeWalk walk = new TreeWalk(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			final RevCommit id = git.commit().setMessage("create file").call();
			final String path = "sub/dir1/dir2";
			DirCache cache = db.lockDirCache();
			DirCacheEditor editor = cache.editor();
			editor.add(new PathEdit(path) {

				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.GITLINK);
					ent.setObjectId(id);
				}
			});
			editor.commit();

			Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
					.setDirectory(new File(db.getWorkTree(), path)).call()
					.getRepository().close();

			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(db);
			walk.addTree(indexIter);
			walk.addTree(workTreeIter);
			walk.setFilter(PathFilter.create(path));

			assertTrue(walk.next());
			assertTrue(indexIter.idEqual(workTreeIter));
		}
	}

	@Test
	public void idOffset() throws Exception {
		try (Git git = new Git(db); TreeWalk tw = new TreeWalk(db)) {
			writeTrashFile("fileAinfsonly", "A");
			File fileBinindex = writeTrashFile("fileBinindex", "B");
			fsTick(fileBinindex);
			git.add().addFilepattern("fileBinindex").call();
			writeTrashFile("fileCinfsonly", "C");
			DirCacheIterator indexIter = new DirCacheIterator(
					db.readDirCache());
			FileTreeIterator workTreeIter = new FileTreeIterator(db);
			tw.addTree(indexIter);
			tw.addTree(workTreeIter);
			workTreeIter.setDirCacheIterator(tw, 0);
			assertEntry("d46c305e85b630558ee19cc47e73d2e5c8c64cdc", "a,", tw);
			assertEntry("58ee403f98538ec02409538b3f80adf610accdec", "a,b", tw);
			assertEntry("0000000000000000000000000000000000000000", "a", tw);
			assertEntry("b8d30ff397626f0f1d3538d66067edf865e201d6", "a0b", tw);
			// The reason for adding this test. Check that the id is correct for
			// mixed
			assertEntry("8c7e5a667f1b771847fe88c01c3de34413a1b220",
					"fileAinfsonly", tw);
			assertEntry("7371f47a6f8bd23a8fa1a8b2a9479cdd76380e54",
					"fileBinindex", tw);
			assertEntry("96d80cd6c4e7158dbebd0849f4fb7ce513e5828c",
					"fileCinfsonly", tw);
			assertFalse(tw.next());
		}
	}

	private final FileTreeIterator.FileModeStrategy NO_GITLINKS_STRATEGY = (
			File f, FS.Attributes attributes) -> {
		if (attributes.isSymbolicLink()) {
			return FileMode.SYMLINK;
		} else if (attributes.isDirectory()) {
			// NOTE: in the production DefaultFileModeStrategy, there is
			// a check here for a subdirectory called '.git', and if it
			// exists, we create a GITLINK instead of recursing into the
			// tree. In this custom strategy, we ignore nested git dirs
			// and treat all directories the same.
			return FileMode.TREE;
		} else if (attributes.isExecutable()) {
			return FileMode.EXECUTABLE_FILE;
		} else {
			return FileMode.REGULAR_FILE;
		}
	};

	private Repository createNestedRepo() throws IOException {
		File gitdir = createUniqueTestGitDir(false);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setGitDir(gitdir);
		Repository nestedRepo = builder.build();
		nestedRepo.create();

		JGitTestUtil.writeTrashFile(nestedRepo, "sub", "a.txt", "content");

		File nestedRepoPath = new File(nestedRepo.getWorkTree(), "sub/nested");
		FileRepositoryBuilder nestedBuilder = new FileRepositoryBuilder();
		nestedBuilder.setWorkTree(nestedRepoPath);
		nestedBuilder.build().create();

		JGitTestUtil.writeTrashFile(nestedRepo, "sub/nested", "b.txt",
				"content b");

		return nestedRepo;
	}

	@Test
	public void testCustomFileModeStrategy() throws Exception {
		Repository nestedRepo = createNestedRepo();

		try (Git git = new Git(nestedRepo)) {
			// validate that our custom strategy is honored
			WorkingTreeIterator customIterator = new FileTreeIterator(
					nestedRepo, NO_GITLINKS_STRATEGY);
			git.add().setWorkingTreeIterator(customIterator).addFilepattern(".")
					.call();
			assertEquals("[sub/a.txt, mode:100644, content:content]"
					+ "[sub/nested/b.txt, mode:100644, content:content b]",
					indexState(nestedRepo, CONTENT));
		}
	}

	@Test
	public void testCustomFileModeStrategyFromParentIterator()
			throws Exception {
		Repository nestedRepo = createNestedRepo();

		try (Git git = new Git(nestedRepo)) {
			FileTreeIterator customIterator = new FileTreeIterator(nestedRepo,
					NO_GITLINKS_STRATEGY);
			File r = new File(nestedRepo.getWorkTree(), "sub");

			// here we want to validate that if we create a new iterator using
			// the constructor that accepts a parent iterator, that the child
			// iterator correctly inherits the FileModeStrategy from the parent
			// iterator.
			FileTreeIterator childIterator = new FileTreeIterator(
					customIterator, r, nestedRepo.getFS());
			git.add().setWorkingTreeIterator(childIterator).addFilepattern(".")
					.call();
			assertEquals("[sub/a.txt, mode:100644, content:content]"
					+ "[sub/nested/b.txt, mode:100644, content:content b]",
					indexState(nestedRepo, CONTENT));
		}
	}

	@Test
	public void testFileModeSymLinkIsNotATree() throws IOException {
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = db.getFS();
		// mål = target in swedish, just to get some unicode in here
		writeTrashFile("mål/data", "targetdata");
		File file = new File(trash, "länk");

		try {
			file.toPath();
		} catch (InvalidPathException e) {
			// When executing a test with LANG environment variable set to non
			// UTF-8 encoding, it seems that JRE cannot handle Unicode file
			// paths. This happens when this test is executed in Bazel as it
			// unsets LANG
			// (https://docs.bazel.build/versions/master/test-encyclopedia.html#initial-conditions).
			// Skip the test if the runtime cannot handle Unicode characters.
			assumeNoException(e);
		}

		fs.createSymLink(file, "mål");
		FileTreeIterator fti = new FileTreeIterator(db);
		assertFalse(fti.eof());
		while (!fti.getEntryPathString().equals("länk")) {
			fti.next(1);
		}
		assertEquals("länk", fti.getEntryPathString());
		assertEquals(FileMode.SYMLINK, fti.getEntryFileMode());
		fti.next(1);
		assertFalse(fti.eof());
		assertEquals("mål", fti.getEntryPathString());
		assertEquals(FileMode.TREE, fti.getEntryFileMode());
		fti.next(1);
		assertTrue(fti.eof());
	}

	@Test
	public void testSymlinkNotModifiedThoughNormalized() throws Exception {
		DirCache dc = db.lockDirCache();
		DirCacheEditor dce = dc.editor();
		final String UNNORMALIZED = "target/";
		final byte[] UNNORMALIZED_BYTES = Constants.encode(UNNORMALIZED);
		try (ObjectInserter oi = db.newObjectInserter()) {
			final ObjectId linkid = oi.insert(Constants.OBJ_BLOB,
					UNNORMALIZED_BYTES, 0, UNNORMALIZED_BYTES.length);
			dce.add(new DirCacheEditor.PathEdit("link") {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.SYMLINK);
					ent.setObjectId(linkid);
					ent.setLength(UNNORMALIZED_BYTES.length);
				}
			});
			assertTrue(dce.commit());
		}
		try (Git git = new Git(db)) {
			git.commit().setMessage("Adding link").call();
			git.reset().setMode(ResetType.HARD).call();
			DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
			FileTreeIterator fti = new FileTreeIterator(db);

			// self-check
			while (!fti.getEntryPathString().equals("link")) {
				fti.next(1);
			}
			assertEquals("link", fti.getEntryPathString());
			assertEquals("link", dci.getEntryPathString());

			// test
			assertFalse(fti.isModified(dci.getDirCacheEntry(), true,
					db.newObjectReader()));
		}
	}

	/**
	 * Like #testSymlinkNotModifiedThoughNormalized but there is no
	 * normalization being done.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSymlinkModifiedNotNormalized() throws Exception {
		DirCache dc = db.lockDirCache();
		DirCacheEditor dce = dc.editor();
		final String NORMALIZED = "target";
		final byte[] NORMALIZED_BYTES = Constants.encode(NORMALIZED);
		try (ObjectInserter oi = db.newObjectInserter()) {
			final ObjectId linkid = oi.insert(Constants.OBJ_BLOB,
					NORMALIZED_BYTES, 0, NORMALIZED_BYTES.length);
			dce.add(new DirCacheEditor.PathEdit("link") {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.SYMLINK);
					ent.setObjectId(linkid);
					ent.setLength(NORMALIZED_BYTES.length);
				}
			});
			assertTrue(dce.commit());
		}
		try (Git git = new Git(db)) {
			git.commit().setMessage("Adding link").call();
			git.reset().setMode(ResetType.HARD).call();
			DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
			FileTreeIterator fti = new FileTreeIterator(db);

			// self-check
			while (!fti.getEntryPathString().equals("link")) {
				fti.next(1);
			}
			assertEquals("link", fti.getEntryPathString());
			assertEquals("link", dci.getEntryPathString());

			// test
			assertFalse(fti.isModified(dci.getDirCacheEntry(), true,
					db.newObjectReader()));
		}
	}

	/**
	 * Like #testSymlinkNotModifiedThoughNormalized but here the link is
	 * modified.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSymlinkActuallyModified() throws Exception {
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		final String NORMALIZED = "target";
		final byte[] NORMALIZED_BYTES = Constants.encode(NORMALIZED);
		try (ObjectInserter oi = db.newObjectInserter()) {
			final ObjectId linkid = oi.insert(Constants.OBJ_BLOB,
					NORMALIZED_BYTES, 0, NORMALIZED_BYTES.length);
			DirCache dc = db.lockDirCache();
			DirCacheEditor dce = dc.editor();
			dce.add(new DirCacheEditor.PathEdit("link") {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.SYMLINK);
					ent.setObjectId(linkid);
					ent.setLength(NORMALIZED_BYTES.length);
				}
			});
			assertTrue(dce.commit());
		}
		try (Git git = new Git(db)) {
			git.commit().setMessage("Adding link").call();
			git.reset().setMode(ResetType.HARD).call();

			FileUtils.delete(new File(trash, "link"), FileUtils.NONE);
			FS.DETECTED.createSymLink(new File(trash, "link"), "newtarget");
			DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
			FileTreeIterator fti = new FileTreeIterator(db);

			// self-check
			while (!fti.getEntryPathString().equals("link")) {
				fti.next(1);
			}
			assertEquals("link", fti.getEntryPathString());
			assertEquals("link", dci.getEntryPathString());

			// test
			assertTrue(fti.isModified(dci.getDirCacheEntry(), true,
					db.newObjectReader()));
		}
	}

	private static void assertEntry(String sha1string, String path, TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		assertTrue(tw.next());
		assertEquals(path, tw.getPathString());
		assertEquals(sha1string,
				tw.getObjectId(1).getName() /* 1=filetree here */);
	}

	private static String nameOf(AbstractTreeIterator i) {
		return RawParseUtils.decode(UTF_8, i.path, 0, i.pathLen);
	}
}
