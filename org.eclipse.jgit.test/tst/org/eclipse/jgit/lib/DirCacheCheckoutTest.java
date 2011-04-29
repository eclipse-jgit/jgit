/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class DirCacheCheckoutTest extends ReadTreeTest {
	private DirCacheCheckout dco;
	@Override
	public void prescanTwoTrees(Tree head, Tree merge)
			throws IllegalStateException, IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, head.getId(), dc, merge.getId());
			dco.preScanTwoTrees();
		} finally {
			dc.unlock();
		}
	}

	@Override
	public void checkout() throws IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, theHead.getId(), dc, theMerge.getId());
			dco.checkout();
		} finally {
			dc.unlock();
		}
	}

	@Override
	public List<String> getRemoved() {
		return dco.getRemoved();
	}

	@Override
	public Map<String, ObjectId> getUpdated() {
		return dco.getUpdated();
	}

	@Override
	public List<String> getConflicts() {
		return dco.getConflicts();
	}

	@Test
	public void testResetHard() throws IOException, NoFilepatternException,
			GitAPIException {
		Git git = new Git(db);
		writeTrashFile("f", "f()");
		writeTrashFile("D/g", "g()");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("inital").call();
		assertIndex(mkmap("f", "f()", "D/g", "g()"));

		git.branchCreate().setName("topic").call();

		writeTrashFile("f", "f()\nmaster");
		writeTrashFile("D/g", "g()\ng2()");
		writeTrashFile("E/h", "h()");
		git.add().addFilepattern(".").call();
		RevCommit master = git.commit().setMessage("master-1").call();
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));

		checkoutBranch("refs/heads/topic");
		assertIndex(mkmap("f", "f()", "D/g", "g()"));

		writeTrashFile("f", "f()\nside");
		assertTrue(new File(db.getWorkTree(), "D/g").delete());
		writeTrashFile("G/i", "i()");
		git.add().addFilepattern(".").call();
		git.add().addFilepattern(".").setUpdate(true).call();
		RevCommit topic = git.commit().setMessage("topic-1").call();
		assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));

		writeTrashFile("untracked", "untracked");

		resetHard(master);
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
		resetHard(topic);
		assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));
		assertWorkDir(mkmap("f", "f()\nside", "G/i", "i()", "untracked",
				"untracked"));

		assertEquals(MergeStatus.CONFLICTING, git.merge().include(master)
				.call().getMergeStatus());
		assertEquals(
				"[D/g, mode:100644, stage:1][D/g, mode:100644, stage:3][E/h, mode:100644][G/i, mode:100644][f, mode:100644, stage:1][f, mode:100644, stage:2][f, mode:100644, stage:3]",
				indexState(0));

		resetHard(master);
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
		assertWorkDir(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h",
				"h()", "untracked", "untracked"));
	}

	private DirCacheCheckout resetHard(RevCommit commit)
			throws NoWorkTreeException,
			CorruptObjectException, IOException {
		DirCacheCheckout dc;
		dc = new DirCacheCheckout(db, null, db.lockDirCache(),
				commit.getTree());
		dc.setFailOnConflict(true);
		assertTrue(dc.checkout());
		return dc;
	}
}
