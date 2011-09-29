/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.submodule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

/**
 * Unit tests of using a {@link WorkingTreeIterator} for a repository with
 * submodules.
 */
public class WorkingTreeIteratorTest extends RepositoryTestCase {

	/**
	 * Test case where submodule HEAD commit matches the object id in the index
	 *
	 * @throws Exception
	 */
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
				.setDirectory(new File(db.getWorkTree(), path)).call();

		TreeWalk walk = new TreeWalk(db);
		DirCacheIterator indexIter = new DirCacheIterator(db.readDirCache());
		FileTreeIterator workTreeIter = new FileTreeIterator(db);
		walk.addTree(indexIter);
		walk.addTree(workTreeIter);
		walk.setFilter(PathFilter.create(path));

		assertTrue(walk.next());
		assertTrue(indexIter.idEqual(workTreeIter));
	}

	/**
	 * Test case where submodule folder in the working tree has no .git
	 * directory
	 *
	 * @throws Exception
	 */
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

		// Delete before walk begins so entry will be visited but repository
		// lookup will fail
		FileUtils.delete(submoduleRoot, FileUtils.RECURSIVE);

		assertTrue(walk.next());
		assertFalse(indexIter.idEqual(workTreeIter));
	}

	/**
	 * Test case where a repository exists in the submodule but the HEAD commit
	 * cannot be resolved
	 *
	 * @throws Exception
	 */
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
	}

	/**
	 * Test case where {@link FileTreeIterator} is created without a repository
	 * but the id of the submodule in the working tree can still be resolved and
	 * successfully matched against the index
	 *
	 * @throws Exception
	 */
	@Test
	public void iteratorWithNoRepository() throws Exception {
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
				.setDirectory(new File(db.getWorkTree(), path)).call();

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
}
