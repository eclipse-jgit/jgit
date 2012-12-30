/*
 * Copyright (C) 2011, 2013 Dariusz Luksza <dariusz@luksza.org>
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
package org.eclipse.jgit.diff;

import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;
import static org.eclipse.jgit.util.FileUtils.delete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class DiffEntryTest extends RepositoryTestCase {

	@Test
	public void shouldListAddedFileInInitialCommit() throws Exception {
		// given
		writeTrashFile("a.txt", "content");
		Git git = new Git(db);
		git.add().addFilepattern("a.txt").call();
		RevCommit c = git.commit().setMessage("initial commit").call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(new EmptyTreeIterator());
		walk.addTree(c.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getChangeType(), is(ChangeType.ADD));
		assertThat(entry.getNewPath(), is("a.txt"));
		assertThat(entry.getOldPath(), is(DEV_NULL));
	}

	@Test
	public void shouldListAddedFileBetweenTwoCommits() throws Exception {
		// given
		Git git = new Git(db);
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit c2 = git.commit().setMessage("second commit").call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getChangeType(), is(ChangeType.ADD));
		assertThat(entry.getNewPath(), is("a.txt"));
		assertThat(entry.getOldPath(), is(DEV_NULL));
	}

	@Test
	public void shouldListModificationBetweenTwoCommits() throws Exception {
		// given
		Git git = new Git(db);
		File file = writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		write(file, "new content");
		RevCommit c2 = git.commit().setAll(true).setMessage("second commit")
				.call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getChangeType(), is(ChangeType.MODIFY));
		assertThat(entry.getNewPath(), is("a.txt"));
	}

	@Test
	public void shouldListDeletionBetweenTwoCommits() throws Exception {
		// given
		Git git = new Git(db);
		File file = writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		delete(file);
		RevCommit c2 = git.commit().setAll(true).setMessage("delete a.txt")
				.call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getOldPath(), is("a.txt"));
		assertThat(entry.getNewPath(), is(DEV_NULL));
		assertThat(entry.getChangeType(), is(ChangeType.DELETE));
	}

	@Test
	public void shouldListModificationInDirWithoutModifiedTrees()
			throws Exception {
		// given
		Git git = new Git(db);
		File tree = new File(new File(db.getWorkTree(), "a"), "b");
		FileUtils.mkdirs(tree);
		File file = new File(tree, "c.txt");
		FileUtils.createNewFile(file);
		write(file, "content");
		git.add().addFilepattern("a").call();
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		write(file, "new line");
		RevCommit c2 = git.commit().setAll(true).setMessage("second commit")
				.call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		walk.setRecursive(true);
		List<DiffEntry> result = DiffEntry.scan(walk);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getChangeType(), is(ChangeType.MODIFY));
		assertThat(entry.getNewPath(), is("a/b/c.txt"));
	}

	@Test
	public void shouldListModificationInDirWithModifiedTrees() throws Exception {
		// given
		Git git = new Git(db);
		File tree = new File(new File(db.getWorkTree(), "a"), "b");
		FileUtils.mkdirs(tree);
		File file = new File(tree, "c.txt");
		FileUtils.createNewFile(file);
		write(file, "content");
		git.add().addFilepattern("a").call();
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		write(file, "new line");
		RevCommit c2 = git.commit().setAll(true).setMessage("second commit")
				.call();

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk, true);

		// then
		assertThat(result, notNullValue());
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(3)));

		DiffEntry entry = result.get(0);
		assertThat(entry.getChangeType(), is(ChangeType.MODIFY));
		assertThat(entry.getNewPath(), is("a"));

		entry = result.get(1);
		assertThat(entry.getChangeType(), is(ChangeType.MODIFY));
		assertThat(entry.getNewPath(), is("a/b"));

		entry = result.get(2);
		assertThat(entry.getChangeType(), is(ChangeType.MODIFY));
		assertThat(entry.getNewPath(), is("a/b/c.txt"));
	}

	@Test
	public void shouldListChangesInWorkingTree() throws Exception {
		// given
		writeTrashFile("a.txt", "content");
		Git git = new Git(db);
		git.add().addFilepattern("a.txt").call();
		RevCommit c = git.commit().setMessage("initial commit").call();
		writeTrashFile("b.txt", "new line");

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c.getTree());
		walk.addTree(new FileTreeIterator(db));
		List<DiffEntry> result = DiffEntry.scan(walk, true);

		// then
		assertThat(Integer.valueOf(result.size()), is(Integer.valueOf(1)));
		DiffEntry entry = result.get(0);

		assertThat(entry.getChangeType(), is(ChangeType.ADD));
		assertThat(entry.getNewPath(), is("b.txt"));
	}

	@Test
	public void shouldMarkEntriesWhenGivenMarkTreeFilter() throws Exception {
		// given
		Git git = new Git(db);
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		FileUtils.mkdir(new File(db.getWorkTree(), "b"));
		writeTrashFile("a.txt", "a");
		writeTrashFile("b/1.txt", "b1");
		writeTrashFile("b/2.txt", "b2");
		writeTrashFile("c.txt", "c");
		git.add().addFilepattern("a.txt").addFilepattern("b")
				.addFilepattern("c.txt").call();
		RevCommit c2 = git.commit().setMessage("second commit").call();
		TreeFilter filterA = PathFilterGroup.createFromStrings("a.txt");
		TreeFilter filterB = PathFilterGroup.createFromStrings("b");
		TreeFilter filterB2 = PathFilterGroup.createFromStrings("b/2.txt");

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> result = DiffEntry.scan(walk, true, new TreeFilter[] {
				filterA, filterB, filterB2 });

		// then
		assertThat(result, notNullValue());
		assertEquals(5, result.size());

		DiffEntry entryA = result.get(0);
		DiffEntry entryB = result.get(1);
		DiffEntry entryB1 = result.get(2);
		DiffEntry entryB2 = result.get(3);
		DiffEntry entryC = result.get(4);

		assertThat(entryA.getNewPath(), is("a.txt"));
		assertTrue(entryA.isMarked(0));
		assertFalse(entryA.isMarked(1));
		assertFalse(entryA.isMarked(2));
		assertEquals(1, entryA.getTreeFilterMarks());

		assertThat(entryB.getNewPath(), is("b"));
		assertFalse(entryB.isMarked(0));
		assertTrue(entryB.isMarked(1));
		assertTrue(entryB.isMarked(2));
		assertEquals(6, entryB.getTreeFilterMarks());

		assertThat(entryB1.getNewPath(), is("b/1.txt"));
		assertFalse(entryB1.isMarked(0));
		assertTrue(entryB1.isMarked(1));
		assertFalse(entryB1.isMarked(2));
		assertEquals(2, entryB1.getTreeFilterMarks());

		assertThat(entryB2.getNewPath(), is("b/2.txt"));
		assertFalse(entryB2.isMarked(0));
		assertTrue(entryB2.isMarked(1));
		assertTrue(entryB2.isMarked(2));
		assertEquals(6, entryB2.getTreeFilterMarks());

		assertThat(entryC.getNewPath(), is("c.txt"));
		assertFalse(entryC.isMarked(0));
		assertFalse(entryC.isMarked(1));
		assertFalse(entryC.isMarked(2));
		assertEquals(0, entryC.getTreeFilterMarks());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldThrowIAEWhenTreeWalkHasLessThanTwoTrees()
			throws Exception {
		// given - we don't need anything here

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(new EmptyTreeIterator());
		DiffEntry.scan(walk);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldThrowIAEWhenTreeWalkHasMoreThanTwoTrees()
			throws Exception {
		// given - we don't need anything here

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(new EmptyTreeIterator());
		walk.addTree(new EmptyTreeIterator());
		walk.addTree(new EmptyTreeIterator());
		DiffEntry.scan(walk);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldThrowIAEWhenScanShouldIncludeTreesAndWalkIsRecursive()
			throws Exception {
		// given - we don't need anything here

		// when
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(new EmptyTreeIterator());
		walk.addTree(new EmptyTreeIterator());
		walk.setRecursive(true);
		DiffEntry.scan(walk, true);
	}

	@Test
	public void shouldReportFileModeChange() throws Exception {
		writeTrashFile("a.txt", "content");
		Git git = new Git(db);
		git.add().addFilepattern("a.txt").call();
		RevCommit c1 = git.commit().setMessage("initial commit").call();
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		final TreeWalk walk = new TreeWalk(db);
		walk.addTree(c1.getTree());
		walk.setRecursive(true);
		assertTrue(walk.next());

		editor.add(new PathEdit("a.txt") {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.EXECUTABLE_FILE);
				ent.setObjectId(walk.getObjectId(0));
			}
		});
		assertTrue(editor.commit());
		RevCommit c2 = git.commit().setMessage("second commit").call();
		walk.reset();
		walk.addTree(c1.getTree());
		walk.addTree(c2.getTree());
		List<DiffEntry> diffs = DiffEntry.scan(walk, false);
		assertEquals(1, diffs.size());
		DiffEntry diff = diffs.get(0);
		assertEquals(ChangeType.MODIFY,diff.getChangeType());
		assertEquals(diff.getOldId(), diff.getNewId());
		assertEquals("a.txt", diff.getOldPath());
		assertEquals(diff.getOldPath(), diff.getNewPath());
		assertEquals(FileMode.EXECUTABLE_FILE, diff.getNewMode());
		assertEquals(FileMode.REGULAR_FILE, diff.getOldMode());
	}
}
