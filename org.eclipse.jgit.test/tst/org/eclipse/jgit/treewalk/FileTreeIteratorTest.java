/*
 * Copyright (C) 2008, Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.WorkingTreeIterator.MetadataDiff;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class FileTreeIteratorTest extends RepositoryTestCase {
	private final String[] paths = { "a,", "a,b", "a/b", "a0b" };

	private long[] mtime;

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
			mtime[i] = new File(trash, s).lastModified();
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
		Git git = new Git(db);
		writeTrashFile("file", "content");
		fsTick(f);
		git.add().addFilepattern("file").call();
		DirCacheEntry dce = db.readDirCache().getEntry("file");
		TreeWalk tw = new TreeWalk(db);
		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(), db
				.getConfig().get(WorkingTreeOptions.KEY));
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
		assertFalse(fti.isModified(dce, false));
	}

	@Test
	public void testIsModifiedSymlink() throws Exception {
		File f = writeTrashFile("symlink", "content");
		Git git = new Git(db);
		git.add().addFilepattern("symlink").call();
		git.commit().setMessage("commit").call();

		// Modify previously committed DirCacheEntry and write it back to disk
		DirCacheEntry dce = db.readDirCache().getEntry("symlink");
		dce.setFileMode(FileMode.SYMLINK);
		DirCacheCheckout.checkoutEntry(db, f, dce);

		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(), db
				.getConfig().get(WorkingTreeOptions.KEY));
		while (!fti.getEntryPathString().equals("symlink"))
			fti.next(1);
		assertFalse(fti.isModified(dce, false));
	}

	@Test
	public void testIsModifiedFileSmudged() throws Exception {
		File f = writeTrashFile("file", "content");
		Git git = new Git(db);
		// The idea of this test is to check the smudged handling
		// Hopefully fsTick will make sure our entry gets smudged
		fsTick(f);
		writeTrashFile("file", "content");
		long lastModified = f.lastModified();
		git.add().addFilepattern("file").call();
		writeTrashFile("file", "conten2");
		f.setLastModified(lastModified);
		DirCacheEntry dce = db.readDirCache().getEntry("file");
		FileTreeIterator fti = new FileTreeIterator(trash, db.getFS(), db
				.getConfig().get(WorkingTreeOptions.KEY));
		while (!fti.getEntryPathString().equals("file"))
			fti.next(1);
		// If the rounding trick does not work we could skip the compareMetaData
		// test and hope that we are usually testing the intended code path.
		assertEquals(MetadataDiff.SMUDGED, fti.compareMetadata(dce));
		assertTrue(fti.isModified(dce, false));
	}

	@Test
	public void submoduleHeadMatchesIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository().close();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	@Test
	public void submoduleWithNoGitDirectory() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		File submoduleRoot = new File(db.getWorkTree(), path);
		assertTrue(submoduleRoot.mkdir());
		assertTrue(new File(submoduleRoot, Constants.DOT_GIT).mkdir());

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertFalse(indexIter.idEqual(workTreeIter));
		assertEquals(ObjectId.zeroId(), workTreeIter.getEntryObjectId());
	}

	@Test
	public void submoduleWithNoHead() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		assertNotNull(Git.init().setDirectory(new File(db.getWorkTree(), path))
				.call().getRepository());

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertFalse(indexIter.idEqual(workTreeIter));
		assertEquals(ObjectId.zeroId(), workTreeIter.getEntryObjectId());
	}

	@Test
	public void submoduleDirectoryIterator() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository().close();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db.getWorkTree(),
				db.getFS(), db.getConfig().get(WorkingTreeOptions.KEY));
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	@Test
	public void submoduleNestedWithHeadMatchingIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit id = git.commit().setMessage("create file").call();
		final String path = "sub/dir1/dir2";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		Git.cloneRepository().setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository().close();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	@Test
	public void idOffset() throws Exception {
		Git git = new Git(db);
		writeTrashFile("fileAinfsonly", "A");
		File fileBinindex = writeTrashFile("fileBinindex", "B");
		fsTick(fileBinindex);
		git.add().addFilepattern("fileBinindex").call();
		writeTrashFile("fileCinfsonly", "C");
		TreeWalk tw = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
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
		assertEntry("7371f47a6f8bd23a8fa1a8b2a9479cdd76380e54", "fileBinindex",
				tw);
		assertEntry("96d80cd6c4e7158dbebd0849f4fb7ce513e5828c",
				"fileCinfsonly", tw);
		assertFalse(tw.next());
	}

	private static void assertEntry(String sha1string, String path, TreeWalk tw)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		assertTrue(tw.next());
		assertEquals(path, tw.getPathString());
		assertEquals(sha1string, tw.getObjectId(1).getName() /* 1=filetree here */);
	}

	private static String nameOf(final AbstractTreeIterator i) {
		return RawParseUtils.decode(Constants.CHARSET, i.path, 0, i.pathLen);
	}
}
