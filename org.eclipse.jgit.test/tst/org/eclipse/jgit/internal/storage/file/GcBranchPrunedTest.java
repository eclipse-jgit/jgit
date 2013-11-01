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

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.junit.Test;

public class GcBranchPrunedTest extends GcTestCase {

	@Test
	public void branch_historyNotPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		do {
			assertTrue(repo.hasObject(tip));
			tr.parseBody(tip);
			RevTree t = tip.getTree();
			assertTrue(repo.hasObject(t));
			assertTrue(repo.hasObject(tr.get(t, "a")));
			tip = tip.getParentCount() > 0 ? tip.getParent(0) : null;
		} while (tip != null);
	}

	@Test
	public void deleteBranch_historyPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		RefUpdate update = repo.updateRef("refs/heads/b");
		update.setForceUpdate(true);
		update.delete();
		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(gc.getStatistics().numberOfLooseObjects == 0);
	}

	@Test
	public void deleteMergedBranch_historyNotPruned() throws Exception {
		RevCommit parent = tr.commit().create();
		RevCommit b1Tip = tr.branch("b1").commit().parent(parent).add("x", "x")
				.create();
		RevCommit b2Tip = tr.branch("b2").commit().parent(parent).add("y", "y")
				.create();

		// merge b1Tip and b2Tip and update refs/heads/b1 to the merge commit
		Merger merger = MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repo);
		merger.merge(b1Tip, b2Tip);
		CommitBuilder cb = tr.commit();
		cb.parent(b1Tip).parent(b2Tip);
		cb.setTopLevelTree(merger.getResultTreeId());
		RevCommit mergeCommit = cb.create();
		RefUpdate u = repo.updateRef("refs/heads/b1");
		u.setNewObjectId(mergeCommit);
		u.update();

		RefUpdate update = repo.updateRef("refs/heads/b2");
		update.setForceUpdate(true);
		update.delete();

		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.hasObject(b2Tip));
	}
}
