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

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileOutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.junit.Before;
import org.junit.Test;

public class CrissCrossMergeTest_Bug534285 extends RepositoryTestCase {
	private TestRepository<FileRepository> db_t;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db_t = new TestRepository<>(db);
	}

	@Test
	/**
	 * Merging m2,s2 from the following topology. The same file is modified in
	 * both branches. The modifications should be mergeable but only if the
	 * automerge of m1 and s1 is choosen as parent. Choosing m0 as parent would
	 * not be sufficient (in contrast to the merge in
	 * crissCrossMerge_mergeable). m2 and s2 contain branch specific conflict
	 * resolutions. Therefore m2 and s2 don't contain the same content.
	 *
	 * <pre>
	 * m0--m1--m2
	 *   \   \/
	 *    \  /\
	 *     s1--s2
	 * </pre>
	 */
	public void crissCrossMerge_mergeable2_bug() throws Exception {

		BranchBuilder master = db_t.branch("master");
		RevCommit m0 = master.commit().add("f", "1\n2\n3\n").message("m0")
				.create();
		RevCommit m1 = master.commit().add("f", "1-master\n2\n3\n")
				.message("m1").create();
		db_t.getRevWalk().parseCommit(m1);

		BranchBuilder side = db_t.branch("side");
		RevCommit s1 = side.commit().parent(m0).add("f", "1\n2\n3-side\n")
				.message("s1").create();
		RevCommit s2 = side.commit().parent(m1)
				.add("f", "1-master\n2\n3-side-r\n").message("s2(merge)")
				.create();
		RevCommit m2 = master.commit().parent(s1)
				.add("f", "1-master-r\n2\n3-side\n").message("m2(merge)")
				.create();

		Git git = Git.wrap(db);
		git.checkout().setName("master").call();
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getWorkTree(), "f"))) {
			db.newObjectReader().open(contentId("side", "f")).copyTo(fos);
		}

		ResolveMerger merger = (ResolveMerger) MergeStrategy.RECURSIVE
				.newMerger(db);
		merger.setWorkingTreeIterator(new FileTreeIterator(db));
		assertFalse(merger.merge(new RevCommit[] { m2, s2 }));
	}

	private ObjectId contentId(String revName, String path) throws Exception {
		RevCommit headCommit = db_t.getRevWalk()
				.parseCommit(db.resolve(revName));
		db_t.parseBody(headCommit);
		return db_t.get(headCommit.getTree(), path).getId();
	}
}
