/*
 * Copyright (C) 2012, Markus Duft <markus.duft@salomon.at>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class IndexModificationTimesTest extends RepositoryTestCase {

	@Test
	public void testLastModifiedTimes() throws Exception {
		try (Git git = new Git(db)) {
			String path = "file";
			writeTrashFile(path, "content");
			String path2 = "file2";
			writeTrashFile(path2, "content2");

			git.add().addFilepattern(path).call();
			git.add().addFilepattern(path2).call();
			git.commit().setMessage("commit").call();

			DirCache dc = db.readDirCache();
			DirCacheEntry entry = dc.getEntry(path);
			DirCacheEntry entry2 = dc.getEntry(path);

			assertTrue("last modified shall not be zero!",
					entry.getLastModified() != 0);

			assertTrue("last modified shall not be zero!",
					entry2.getLastModified() != 0);

			writeTrashFile(path, "new content");
			git.add().addFilepattern(path).call();
			git.commit().setMessage("commit2").call();

			dc = db.readDirCache();
			entry = dc.getEntry(path);
			entry2 = dc.getEntry(path);

			assertTrue("last modified shall not be zero!",
					entry.getLastModified() != 0);

			assertTrue("last modified shall not be zero!",
					entry2.getLastModified() != 0);
		}
	}

	@Test
	public void testModify() throws Exception {
		try (Git git = new Git(db)) {
			String path = "file";
			writeTrashFile(path, "content");

			git.add().addFilepattern(path).call();
			git.commit().setMessage("commit").call();

			DirCache dc = db.readDirCache();
			DirCacheEntry entry = dc.getEntry(path);

			long masterLastMod = entry.getLastModified();

			git.checkout().setCreateBranch(true).setName("side").call();

			Thread.sleep(10);
			String path2 = "file2";
			writeTrashFile(path2, "side content");
			git.add().addFilepattern(path2).call();
			git.commit().setMessage("commit").call();

			dc = db.readDirCache();
			entry = dc.getEntry(path);

			long sideLastMode = entry.getLastModified();

			Thread.sleep(2000);

			writeTrashFile(path, "uncommitted content");
			git.checkout().setName("master").call();

			dc = db.readDirCache();
			entry = dc.getEntry(path);

			assertTrue("shall have equal mod time!", masterLastMod == sideLastMode);
			assertTrue("shall not equal master timestamp!",
					entry.getLastModified() == masterLastMod);
		}
	}

}
