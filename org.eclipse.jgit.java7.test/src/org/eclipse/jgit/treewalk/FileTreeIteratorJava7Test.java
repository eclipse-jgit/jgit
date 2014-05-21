/*
 * Copyright (C) 2012-2013, Robin Rosenberg <robin.rosenberg@dewire.com>
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class FileTreeIteratorJava7Test extends RepositoryTestCase {
	@Test
	public void testFileModeSymLinkIsNotATree() throws IOException {
		FS fs = db.getFS();
		// mål = target in swedish, just to get som unicode in here
		writeTrashFile("mål/data", "targetdata");
		fs.createSymLink(new File(trash, "länk"), "mål");
		FileTreeIterator fti = new FileTreeIterator(db);
		assertFalse(fti.eof());
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
		ObjectInserter oi = db.newObjectInserter();
		final ObjectId linkid = oi.insert(Constants.OBJ_BLOB,
				UNNORMALIZED_BYTES, 0,
				UNNORMALIZED_BYTES.length);
		oi.release();
		dce.add(new DirCacheEditor.PathEdit("link") {
			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.SYMLINK);
				ent.setObjectId(linkid);
				ent.setLength(UNNORMALIZED_BYTES.length);
			}
		});
		assertTrue(dce.commit());
		new Git(db).commit().setMessage("Adding link").call();
		new Git(db).reset().setMode(ResetType.HARD).call();
		DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
		FileTreeIterator fti = new FileTreeIterator(db);

		// self-check
		assertEquals("link", fti.getEntryPathString());
		assertEquals("link", dci.getEntryPathString());

		// test
		assertFalse(fti.isModified(dci.getDirCacheEntry(), true,
				db.newObjectReader()));
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
		ObjectInserter oi = db.newObjectInserter();
		final ObjectId linkid = oi.insert(Constants.OBJ_BLOB, NORMALIZED_BYTES,
				0, NORMALIZED_BYTES.length);
		oi.release();
		dce.add(new DirCacheEditor.PathEdit("link") {
			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.SYMLINK);
				ent.setObjectId(linkid);
				ent.setLength(NORMALIZED_BYTES.length);
			}
		});
		assertTrue(dce.commit());
		new Git(db).commit().setMessage("Adding link").call();
		new Git(db).reset().setMode(ResetType.HARD).call();
		DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
		FileTreeIterator fti = new FileTreeIterator(db);

		// self-check
		assertEquals("link", fti.getEntryPathString());
		assertEquals("link", dci.getEntryPathString());

		// test
		assertFalse(fti.isModified(dci.getDirCacheEntry(), true));
	}

	/**
	 * Like #testSymlinkNotModifiedThoughNormalized but here the link is
	 * modified.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSymlinkActuallyModified() throws Exception {
		final String NORMALIZED = "target";
		final byte[] NORMALIZED_BYTES = Constants.encode(NORMALIZED);
		ObjectInserter oi = db.newObjectInserter();
		final ObjectId linkid = oi.insert(Constants.OBJ_BLOB, NORMALIZED_BYTES,
				0, NORMALIZED_BYTES.length);
		oi.release();
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
		new Git(db).commit().setMessage("Adding link").call();
		new Git(db).reset().setMode(ResetType.HARD).call();

		FileUtils.delete(new File(trash, "link"), FileUtils.NONE);
		FS.DETECTED.createSymLink(new File(trash, "link"), "newtarget");
		DirCacheIterator dci = new DirCacheIterator(db.readDirCache());
		FileTreeIterator fti = new FileTreeIterator(db);

		// self-check
		assertEquals("link", fti.getEntryPathString());
		assertEquals("link", dci.getEntryPathString());

		// test
		assertTrue(fti.isModified(dci.getDirCacheEntry(), true,
				db.newObjectReader()));
	}
}
