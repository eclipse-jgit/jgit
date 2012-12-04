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

import java.io.IOException;

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
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class RecursiveMergerTest extends RepositoryTestCase {

	@Test
	/**
	 * Merging m2,s2 from the following topology
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
	 * Merging a2,s2 which have three common predecessors
	 *
	 *     m1-----m2
	 *    /  \/  /
	 *   /   /\ /
	 * m0--a1  x
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

		git.checkout().setCreateBranch(true).setStartPoint(m0)
				.setName("another").call();
		RevCommit a1 = commitNewFile(git, "a1");

		git.checkout().setName("master").call();
		MergeResult res_m2 = mergeMultiple(git, new RevCommit[] { s1, a1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("side").call();
		MergeResult res_s2 = mergeMultiple(git, new RevCommit[] { m1, a1 });
		assertEquals(MergeStatus.MERGED, res_m2.getMergeStatus());

		git.checkout().setName("master").call();

		MergeResult res_s3 = git.merge().include(res_s2.getNewHead())
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(MergeStatus.MERGED, res_s3.getMergeStatus());
	}

	private RevCommit commitNewFile(Git git, String name) throws IOException,
			GitAPIException, NoFilepatternException, NoHeadException,
			NoMessageException, UnmergedPathsException,
			ConcurrentRefUpdateException, WrongRepositoryStateException {
		writeTrashFile(name, name);
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
