/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.merge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class RecursiveMergerTest extends RepositoryTestCase {
	static int counter = 0;

	@DataPoints
	public static MergeStrategy[] strategiesUnderTest = new MergeStrategy[] {
			MergeStrategy.RECURSIVE, MergeStrategy.RESOLVE };

	public enum IndexState {
		Bare, Missing, SameAsHead, DifferentFromHead
	}

	@DataPoints
	public static IndexState[] indexStates = IndexState.values();

	public enum WorktreeState {
		Bare, Missing, SameAsIndex, SameAsHeadDifferentFromIndex, DifferentFromIndexAndHead;
	}

	@DataPoints
	public static WorktreeState[] worktreeStates = WorktreeState.values();

	private TestRepository<FileRepository> db_t;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db_t = new TestRepository<FileRepository>(db);
	}

	@Theory
	/**
	 * Merging m2,s2 from the following topology. In master and side different
	 * files are touched. No need to do a real content merge.
	 *
	 * <pre>
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 * </pre>
	 */
	public void crissCrossMerge(MergeStrategy strategy, IndexState indexState,
			WorktreeState worktreeState) throws Exception {
		if (!validateStates(indexState, worktreeState))
			return;
		// fill the repo
		BranchBuilder master = db_t.branch("master");
		RevCommit m0 = master.commit().add("m", ",m0").message("m0").create();
		RevCommit m1 = master.commit().add("m", "m1").message("m1").create();
		db_t.getRevWalk().parseCommit(m1);

		BranchBuilder side = db_t.branch("side");
		RevCommit s1 = side.commit().parent(m0).add("s", "s1").message("s1")
				.create();
		RevCommit s2 = side.commit().parent(m1).add("m", "m1")
				.message("s2(merge)").create();
		RevCommit m2 = master.commit().parent(s1).add("s", "s1")
				.message("m2(merge)").create();

		modifyIndex(indexState, "m");
		modifyWorktree(worktreeState, "m", "refs/heads/master");

		Merger merger = strategy.newMerger(db);
		try {
			Assert.assertTrue(merger.merge(new RevCommit[] { s2, m2 }));
			Assert.assertEquals(MergeStrategy.RECURSIVE, strategy);
			Assert.assertEquals("m1",
					contentAsString(db, merger.getResultTreeId(), "m"));
			Assert.assertEquals("s1",
					contentAsString(db, merger.getResultTreeId(), "s"));
			if (indexState != IndexState.Bare)
				Assert.assertEquals(
						"[m, mode:100644, content:m1][s, mode:100644, content:s1]",
						indexState(RepositoryTestCase.CONTENT));
			if (worktreeState != WorktreeState.Bare) {
				Assert.assertEquals("m1", read("m"));
				Assert.assertEquals("s1", read("s"));
			}
		} catch (IOException ex) {
			Assert.assertEquals(strategy, MergeStrategy.RESOLVE);
		}
	}

	@Theory
	/**
	 * Merging m2,s2 from the following topology. The same file is modified
	 * in both branches. The modifications should be mergeable. m2 and s2
	 * contain branch specific conflict resolutions. Therefore m2 and don't contain the same content.
	 *
	 * <pre>
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 * </pre>
	 */
	public void crissCrossMerge_mergeable(MergeStrategy strategy,
			IndexState indexState, WorktreeState worktreeState)
			throws Exception {
		if (!validateStates(indexState, worktreeState))
			return;

		BranchBuilder master = db_t.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n")
				.message("m0").create();
		RevCommit m1 = master.commit()
				.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m1")
				.create();
		db_t.getRevWalk().parseCommit(m1);

		BranchBuilder side = db_t.branch("side");
		RevCommit s1 = side.commit().parent(m0)
				.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9-side\n").message("s1")
				.create();
		RevCommit s2 = side.commit().parent(m1)
				.add("f", "1-master\n2\n3\n4\n5\n6\n7-res(side)\n8\n9-side\n")
				.message("s2(merge)").create();
		RevCommit m2 = master
				.commit()
				.parent(s1)
				.add("f", "1-master\n2\n3-res(master)\n4\n5\n6\n7\n8\n9-side\n")
				.message("m2(merge)").create();

		modifyIndex(indexState, "f");
		modifyWorktree(worktreeState, "f", "refs/heads/master");

		Merger merger = strategy.newMerger(db);
		try {
			Assert.assertTrue(merger.merge(new RevCommit[] { s2, m2 }));
			Assert.assertEquals(MergeStrategy.RECURSIVE, strategy);
			Assert.assertEquals(
					"1-master\n2\n3-res(master)\n4\n5\n6\n7-res(side)\n8\n9-side",
					contentAsString(db, merger.getResultTreeId(), "f"));
			if (indexState != IndexState.Bare)
				Assert.assertEquals(
						"[f, mode:100644, content:1-master\n2\n3-res(master)\n4\n5\n6\n7-res(side)\n8\n9-side\n]",
						indexState(RepositoryTestCase.CONTENT));
			if (worktreeState != WorktreeState.Bare
					&& worktreeState != WorktreeState.Missing)
				Assert.assertEquals(
						"1-master\n2\n3-res(master)\n4\n5\n6\n7-res(side)\n8\n9-side\n",
						read("f"));
		} catch (IOException ex) {
			Assert.assertEquals(MergeStrategy.RESOLVE, strategy);
		}
	}

	@Theory
	/**
	 * Merging m2,s2 from the following topology. The same file is modified
	 * in both branches. The modifications should are not automatically
	 * mergeable. m2 and s2 contain branch specific conflict resolutions.
	 * Therefore m2 and s2 don't contain the same content.
	 *
	 * <pre>
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 * </pre>
	 */
	public void crissCrossMerge_nonmergeable(MergeStrategy strategy,
			IndexState indexState, WorktreeState worktreeState)
			throws Exception {
		if (!validateStates(indexState, worktreeState))
			return;

		BranchBuilder master = db_t.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n")
				.message("m0").create();
		RevCommit m1 = master.commit()
				.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m1")
				.create();
		db_t.getRevWalk().parseCommit(m1);

		BranchBuilder side = db_t.branch("side");
		RevCommit s1 = side.commit().parent(m0)
				.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9-side\n").message("s1")
				.create();
		RevCommit s2 = side.commit().parent(m1)
				.add("f", "1-master\n2\n3\n4\n5\n6\n7-res(side)\n8\n9-side\n")
				.message("s2(merge)").create();
		RevCommit m2 = master.commit().parent(s1)
				.add("f", "1-master\n2\n3\n4\n5\n6\n7-conflict\n8\n9-side\n")
				.message("m2(merge)").create();

		modifyIndex(indexState, "f");
		modifyWorktree(worktreeState, "f", "refs/heads/master");

		Merger merger = strategy.newMerger(db);
		try {
			Assert.assertFalse(merger.merge(new RevCommit[] { s2, m2 }));
			Assert.assertEquals(MergeStrategy.RECURSIVE, strategy);
			if (indexState != IndexState.Bare)
				Assert.assertEquals(
						"[f, mode:100644, stage:1, content:1-master\n2\n3\n4\n5\n6\n7\n8\n9-side\n]"
								+ "[f, mode:100644, stage:2, content:1-master\n2\n3\n4\n5\n6\n7-res(side)\n8\n9-side\n]"
								+ "[f, mode:100644, stage:3, content:1-master\n2\n3\n4\n5\n6\n7-conflict\n8\n9-side\n]",
						indexState(RepositoryTestCase.CONTENT));
			if (worktreeState != WorktreeState.Bare
					&& worktreeState != WorktreeState.Missing)
				Assert.assertEquals(
						"1-master\n2\n3\n4\n5\n6\n<<<<<<< OURS\n7-res(side)\n=======\n7-conflict\n>>>>>>> THEIRS\n8\n9-side\n",
						read("f"));
		} catch (IOException ex) {
			Assert.assertEquals(MergeStrategy.RESOLVE, strategy);
		}
	}

	@Theory
	/**
	 * Merging m2,s2 which have three common predecessors.The same file is modified
	 * in all branches. The modifications should be mergeable. m2 and s2
	 * contain branch specific conflict resolutions. Therefore m2 and s2
	 * don't contain the same content.
	 *
	 * <pre>
	 *     m1-----m2
	 *    /  \/  /
	 *   /   /\ /
	 * m0--o1  x
	 *   \   \/ \
	 *    \  /\  \
	 *     s1-----s2
	 * </pre>
	 */
	public void crissCrossMerge_ThreeCommonPredecessors(MergeStrategy strategy,
			IndexState indexState, WorktreeState worktreeState)
			throws Exception {
		if (!validateStates(indexState, worktreeState))
			return;

		BranchBuilder master = db_t.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n")
				.message("m0").create();
		RevCommit m1 = master.commit()
				.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m1")
				.create();
		BranchBuilder side = db_t.branch("side");
		RevCommit s1 = side.commit().parent(m0)
				.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9-side\n").message("s1")
				.create();
		BranchBuilder other = db_t.branch("other");
		RevCommit o1 = other.commit().parent(m0)
				.add("f", "1\n2\n3\n4\n5-other\n6\n7\n8\n9\n").message("o1")
				.create();

		RevCommit m2 = master
				.commit()
				.parent(s1)
				.parent(o1)
				.add("f",
						"1-master\n2\n3-res(master)\n4\n5-other\n6\n7\n8\n9-side\n")
				.message("m2(merge)").create();

		RevCommit s2 = side
				.commit()
				.parent(m1)
				.parent(o1)
				.add("f",
						"1-master\n2\n3\n4\n5-other\n6\n7-res(side)\n8\n9-side\n")
				.message("s2(merge)").create();

		modifyIndex(indexState, "f");
		modifyWorktree(worktreeState, "f", "refs/heads/master");

		Merger merger = strategy.newMerger(db);
		try {
			Assert.assertTrue(merger.merge(new RevCommit[] { s2, m2 }));
			Assert.assertEquals(MergeStrategy.RECURSIVE, strategy);
			Assert.assertEquals(
					"1-master\n2\n3-res(master)\n4\n5-other\n6\n7-res(side)\n8\n9-side",
					contentAsString(db, merger.getResultTreeId(), "f"));
			if (indexState != IndexState.Bare)
				Assert.assertEquals(
						"[f, mode:100644, content:1-master\n2\n3-res(master)\n4\n5-other\n6\n7-res(side)\n8\n9-side\n]",
						indexState(RepositoryTestCase.CONTENT));
			if (worktreeState != WorktreeState.Bare
					&& worktreeState != WorktreeState.Missing)
				Assert.assertEquals(
						"1-master\n2\n3-res(master)\n4\n5-other\n6\n7-res(side)\n8\n9-side\n",
						read("f"));
		} catch (IOException ex) {
			Assert.assertEquals(MergeStrategy.RESOLVE, strategy);
		}
	}

	void modifyIndex(IndexState indexState, String path) throws Exception {
		switch (indexState) {
		case Missing:
			db_t.commit().rm(path);
			break;
		case DifferentFromHead:
			db_t.commit().add(path, Integer.toString(counter++));
			break;
		case SameAsHead:
			RevCommit headCommit = db_t.getRevWalk().parseCommit(
					db.resolve("HEAD"));
			db_t.parseBody(headCommit);
			db_t.commit().add(path,
					(RevBlob) (db_t.get(headCommit.getTree(), path)));
			break;
		case Bare:
			new File(db.getDirectory(), "index").delete();
			break;
		}
	}

	void modifyWorktree(WorktreeState worktreeState, String path,
			String branchName) throws Exception {
		FileOutputStream fos = null;

		new Git(db).reset().setMode(ResetType.HARD).setRef(branchName).call();

		try {
			switch (worktreeState) {
			case Missing:
				new File(db.getWorkTree(), path).delete();
				break;
			case DifferentFromIndexAndHead:
				write(new File(db.getWorkTree(), path),
						Integer.toString(counter++));
				break;
			case SameAsHeadDifferentFromIndex:
				RevCommit headCommit = db_t.getRevWalk().parseCommit(
						db.resolve("HEAD"));
				db_t.parseBody(headCommit);
				ObjectId bloblId = db_t.get(headCommit.getTree(), path).getId();
				fos = new FileOutputStream(new File(db.getWorkTree(), path));
				db.newObjectReader().open(bloblId).copyTo(fos);
				break;
			case SameAsIndex:
				DirCache dc = DirCache.read(db);
				bloblId = dc.getEntry(path).getObjectId();
				fos = new FileOutputStream(new File(db_t.getRepository()
						.getWorkTree(), path));
				db.newObjectReader().open(bloblId).copyTo(fos);
				break;
			case Bare:
				db.getConfig().setBoolean("core", null, "bare", true);
				db = new FileRepository(db.getDirectory());
			}
		} finally {
			if (fos != null)
				fos.close();
		}
	}

	private boolean validateStates(IndexState indexState,
			WorktreeState worktreeState) {
		if (worktreeState == WorktreeState.Bare
				&& indexState != IndexState.Bare)
			return false;
		if (worktreeState != WorktreeState.Bare
				&& indexState == IndexState.Bare)
			return false;
		if (worktreeState == WorktreeState.SameAsIndex
				&& indexState == IndexState.Missing)
			return false;
		if (worktreeState == WorktreeState.SameAsHeadDifferentFromIndex
				&& indexState == IndexState.SameAsHead)
			return false;
		return true;
	}

	private String contentAsString(Repository r, ObjectId treeId, String path)
			throws MissingObjectException, IOException {
		TreeWalk tw = new TreeWalk(r);
		tw.addTree(treeId);
		tw.setFilter(PathFilter.create(path));
		tw.setRecursive(true);
		if (!tw.next())
			return null;
		AnyObjectId blobId = tw.getObjectId(0);

		StringBuilder result = new StringBuilder();
		BufferedReader br = null;
		ObjectReader or = r.newObjectReader();
		try {
			br = new BufferedReader(new InputStreamReader(or.open(blobId)
					.openStream()));
			String line;
			boolean first = true;
			while ((line = br.readLine()) != null) {
				if (!first)
					result.append('\n');
				result.append(line);
				first = false;
			}
			return result.toString();
		} finally {
			if (br != null)
				br.close();
		}
	}
}
