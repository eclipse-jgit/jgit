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

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.Assert;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

public class RecursiveMergerTest extends RepositoryTestCase {

	@Test
	/**
	 * Merging m2,s2 from the following topology. In master and side different
	 * files are touched. No need to do a real content merge.
	 *
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 */
	public void lowLevelBareRepo_EasyCrissCrossMerge() throws Exception {
		FileRepository r = createBareRepository();
		TestRepository tr = new TestRepository<FileRepository>(r);
		BranchBuilder master = tr.branch("master");
		RevCommit m0 = master.commit().add("m", ",m0").message("m0").create();
		RevCommit m1 = master.commit().add("m", "m1").message("m1").create();
		tr.getRevWalk().parseCommit(m1);

		BranchBuilder side = tr.branch("side");
		RevCommit s1 = side.commit().parent(m0).add("s", "s1").message("s1")
				.create();
		RevCommit s2 = side.commit().parent(m1).add("m", "m1")
				.message("s2(merge)").create();

		RevCommit m2 = master.commit().parent(s1).add("s", "s1")
				.message("m2(merge)").create();

		RecursiveMerger merger = new RecursiveMerger(r, true);
		Assert.assertEquals(true, merger.merge(new RevCommit[] { s2, m2 }));
		assertEquals("m1", contentAsString(r, merger.getResultTreeId(), "m"));
		assertEquals("s1", contentAsString(r, merger.getResultTreeId(), "s"));
	}

	@Test
	/**
	 * Merging m2,s2 from the following topology. The same file is modified
	 * in both branches. The modifications should be mergeable. m2 and s2
	 * contain branch specific conflict resolutions. Therefore m2 and s2
	 * don't contain the same content.
	 *
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 */
	public void lowLevelBareRepo_CrissCrossMerge() throws Exception {
		FileRepository r = createBareRepository();
		TestRepository tr = new TestRepository<FileRepository>(r);
		BranchBuilder master = tr.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n")
				.message("m0").create();
		RevCommit m1 = master.commit()
				.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m1")
				.create();
		tr.getRevWalk().parseCommit(m1);

		BranchBuilder side = tr.branch("side");
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

		RecursiveMerger merger = new RecursiveMerger(r, true);
		Assert.assertEquals(true, merger.merge(new RevCommit[] { s2, m2 }));
		assertEquals(
				"1-master\n2\n3-res(master)\n4\n5\n6\n7-res(side)\n8\n9-side\n",
				contentAsString(r, merger.getResultTreeId(), "f"));
	}

	@Test
	/**
	 * Merging m2,s2 which have three common predecessors.The same file is modified
	 * in all branches. The modifications should be mergeable. m2 and s2
	 * contain branch specific conflict resolutions. Therefore m2 and s2
	 * don't contain the same content.
	 *
	 *     m1-----m2
	 *    /  \/  /
	 *   /   /\ /
	 * m0--o1  x
	 *   \   \/ \
	 *    \  /\  \
	 *     s1-----s2
	 */
	public void lowLevelBareRepo_ThreeCommonPredecessors() throws Exception {
		FileRepository r = createBareRepository();
		TestRepository tr = new TestRepository<FileRepository>(r);
		BranchBuilder master = tr.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n")
				.message("m0").create();
		RevCommit m1 = master.commit()
				.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m1")
				.create();
		BranchBuilder side = tr.branch("side");
		RevCommit s1 = side.commit().parent(m0)
				.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9-side\n").message("s1")
				.create();
		BranchBuilder other = tr.branch("other");
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

		RecursiveMerger merger = new RecursiveMerger(r, true);
		Assert.assertEquals(true, merger.merge(new RevCommit[] { s2, m2 }));
		assertEquals(
				"1-master\n2\n3-res(master)\n4\n5-other\n6\n7-res(side)\n8\n9-side\n",
				contentAsString(r, merger.getResultTreeId(), "f"));
	}

	@Test
	/**
	 * Merging m2,s2 from the following topology. Each commit touches
	 * his own file. No content merges are needed.
	 *
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 */
	public void crissCrossMerge() throws IOException, GitAPIException {
		Git git = Git.wrap(db);

		RevCommit m0 = commitNewFile(git, "m0");
		RevCommit m1 = commitNewFile(git, "m1");

		git.checkout().setCreateBranch(true).setStartPoint(m0).setName("side")
				.call();
		RevCommit s1 = commitNewFile(git, "s1");

		MergeResult res_s2 = git.merge().include(m1)
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_s2.getMergeStatus());

		git.checkout().setName("master").call();

		MergeResult res_m2 = git.merge().include(s1)
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		MergeResult res_m3 = git.merge().include(res_s2.getNewHead())
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_m3.getMergeStatus());
	}

	@Test
	/**
	 * Merging a2,s2 which have three common predecessors. Each commit touches
	 * his own file. No content merges are needed.
	 *
	 *     m1-----m2
	 *    /  \/  /
	 *   /   /\ /
	 * m0--o1  x
	 *   \   \/ \
	 *    \  /\  \
	 *     s1-----s2
	 */
	public void threeCommonPredecesors() throws IOException, GitAPIException {
		Git git = Git.wrap(db);

		RevCommit m0 = commitNewFile(git, "m0");
		RevCommit m1 = commitNewFile(git, "m1");

		git.checkout().setCreateBranch(true).setStartPoint(m0).setName("side")
				.call();
		RevCommit s1 = commitNewFile(git, "s1");

		git.checkout().setCreateBranch(true).setStartPoint(m0).setName("other")
				.call();
		RevCommit o1 = commitNewFile(git, "o1");

		git.checkout().setName("master").call();
		MergeResult res_m2 = mergeMultiple(git, new RevCommit[] { s1, o1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("side").call();
		MergeResult res_s2 = mergeMultiple(git, new RevCommit[] { m1, o1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("master").call();

		MergeResult res_s3 = git.merge().include(res_s2.getNewHead())
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_s3.getMergeStatus());
	}

	@Test
	/**
	 * Merging a2,s2 which have three common predecessors. The same file is modified
	 * in all branches. The modifications should be mergeable. m2 and s2
	 * contain branch specific conflict resolutions. Therefore m2 and s2
	 * don't contain the same content.
	 *
	 *     m1-----m2
	 *    /  \/  /
	 *   /   /\ /
	 * m0--o1  x
	 *   \   \/ \
	 *    \  /\  \
	 *     s1-----s2
	 */
	public void threeCommonPredecesorsWithContentMerge() throws IOException,
			GitAPIException {
		Git git = Git.wrap(db);

		RevCommit m0 = commitNewFile(git, "f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n");
		RevCommit m1 = commitNewFile(git, "f",
				"1-master\n2\n3\n4\n5\n6\n7\n8\n9\n");

		git.checkout().setCreateBranch(true).setStartPoint(m0).setName("side")
				.call();
		RevCommit s1 = commitNewFile(git, "f",
				"1\n2\n3\n4\n5\n6\n7\n8\n9-side\n");

		git.checkout().setCreateBranch(true).setStartPoint(m0).setName("other")
				.call();
		RevCommit o1 = commitNewFile(git, "f",
				"1\n2\n3\n4\n5-other\n6\n7\n8\n9\n");

		git.checkout().setName("master").call();
		MergeResult res_m2 = mergeMultiple(git, new RevCommit[] { s1, o1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("side").call();
		MergeResult res_s2 = mergeMultiple(git, new RevCommit[] { m1, o1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("master").call();

		MergeResult res_s3 = git.merge().include(res_s2.getNewHead())
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_s3.getMergeStatus());
		assertEquals(
				"1-master\n2\n3-res(master)\n4\n5-other\n6\n7-res(side)\n8\n9-side\n",
				read("f"));
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

	private RevCommit commitNewFile(Git git, String name) throws IOException,
			GitAPIException, NoFilepatternException, NoHeadException,
			NoMessageException, UnmergedPathsException,
			ConcurrentRefUpdateException, WrongRepositoryStateException {
		return commitNewFile(git, name, name);
	}

	private RevCommit commitNewFile(Git git, String name, String data)
			throws IOException, GitAPIException, NoFilepatternException,
			NoHeadException, NoMessageException, UnmergedPathsException,
			ConcurrentRefUpdateException, WrongRepositoryStateException {
		writeTrashFile(name, data);
		git.add().addFilepattern(name).call();
		RevCommit m0 = git.commit().setMessage(name).call();
		return m0;
	}

	private MergeResult mergeMultiple(Git git, RevCommit commits[])
			throws GitAPIException {
		MergeResult result = null;
		for (RevCommit c : commits) {
			result = git.merge().include(c)
					.setStrategy(MergeStrategy.RECURSIVE).call();
		}
		return result;
	}

}
