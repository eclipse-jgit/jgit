/*
 * Copyright (C) 2012, Marc Strapetz <marc.strapetz@syntevo.com>
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

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class RevWalkShallowTest extends RevWalkTestCase {

	// Accessing ==============================================================

	@Test
	public void testDepth1() throws Exception {
		RevCommit[] commits = setupLinearChain();

		createShallowFile(commits[3]);
		updateCommits(commits);

		rw.markStart(commits[3]);
		assertCommit(commits[3], rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testDepth2() throws Exception {
		RevCommit[] commits = setupLinearChain();

		createShallowFile(commits[2]);
		updateCommits(commits);

		rw.markStart(commits[3]);
		assertCommit(commits[3], rw.next());
		assertCommit(commits[2], rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testDepth3() throws Exception {
		RevCommit[] commits = setupLinearChain();

		createShallowFile(commits[1]);
		updateCommits(commits);

		rw.markStart(commits[3]);
		assertCommit(commits[3], rw.next());
		assertCommit(commits[2], rw.next());
		assertCommit(commits[1], rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testObjectDirectorySnapshot() throws Exception {
		RevCommit[] commits = setupLinearChain();

		createShallowFile(commits[3]);
		updateCommits(commits);

		markStart(commits[3]);
		assertCommit(commits[3], rw.next());
		assertNull(rw.next());

		createShallowFile(commits[2]);
		updateCommits(commits);

		markStart(commits[3]);
		assertCommit(commits[3], rw.next());
		assertCommit(commits[2], rw.next());
		assertNull(rw.next());
	}

	private RevCommit[] setupLinearChain() throws Exception {
		RevCommit[] commits = new RevCommit[4];
		RevCommit parent = null;
		for (int i = 0; i < commits.length; i++) {
			commits[i] = parent != null ? commit(parent) : commit();
			parent = commits[i];
		}
		return commits;
	}

	@Test
	public void testMergeCommitOneParentShallow() throws Exception {
		RevCommit[] commits = setupMergeChain();

		createShallowFile(commits[4]);
		updateCommits(commits);

		markStart(commits[5]);
		assertCommit(commits[5], rw.next());
		assertCommit(commits[4], rw.next());
		assertCommit(commits[2], rw.next());
		assertCommit(commits[1], rw.next());
		assertCommit(commits[0], rw.next());
		assertNull(rw.next());
	}

	@Test
	public void testMergeCommitEntirelyShallow() throws Exception {
		RevCommit[] commits = setupMergeChain();

		createShallowFile(commits[2], commits[4]);
		updateCommits(commits);

		markStart(commits[5]);
		assertCommit(commits[5], rw.next());
		assertCommit(commits[4], rw.next());
		assertCommit(commits[2], rw.next());
		assertNull(rw.next());
	}

	private RevCommit[] setupMergeChain() throws Exception {
		/*-
		 * Create a history like this, diverging at 1 and merging at 5:
		 *
		 *      ---o--o       commits 3,4
		 *     /       \
		 * o--o--o------o   commits 0,1,2,5
		 */
		RevCommit[] commits = new RevCommit[6];
		commits[0] = commit();
		commits[1] = commit(commits[0]);
		commits[2] = commit(commits[1]);
		commits[3] = commit(commits[1]);
		commits[4] = commit(commits[3]);
		commits[5] = commit(commits[2], commits[4]);
		return commits;
	}

	private void updateCommits(RevCommit[] commits) {
		// Relookup commits using the new RevWalk
		for (int i = 0; i < commits.length; i++) {
			commits[i] = rw.lookupCommit(commits[i].getId());
		}
	}

	private void createShallowFile(ObjectId... shallowCommits)
			throws IOException {
		// Reset the RevWalk since the new shallow file invalidates the existing
		// RevWalk's shallow state.
		rw.close();
		rw = createRevWalk();
		StringBuilder builder = new StringBuilder();
		for (ObjectId commit : shallowCommits) {
			builder.append(commit.getName() + "\n");
		}
		JGitTestUtil.write(db.getDirectoryChild(Constants.SHALLOW),
				builder.toString());
	}

	@Test
	public void testShallowCommitParse() throws Exception {
		RevCommit a = commit();
		RevCommit b = commit(a);

		createShallowFile(b);

		rw.close();
		rw = createRevWalk();
		b = rw.parseCommit(b);

		markStart(b);
		assertCommit(b, rw.next());
		assertNull(rw.next());
	}
}
