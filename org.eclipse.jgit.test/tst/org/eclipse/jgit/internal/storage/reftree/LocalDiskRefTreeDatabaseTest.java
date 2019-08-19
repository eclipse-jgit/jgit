/*
 * Copyright (C) 2016 Google Inc.
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

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.ORIG_HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Before;
import org.junit.Test;

public class LocalDiskRefTreeDatabaseTest extends LocalDiskRepositoryTestCase {
	private FileRepository repo;
	private RefTreeDatabase refdb;
	private RefDatabase bootstrap;

	private TestRepository<FileRepository> testRepo;
	private RevCommit A;
	private RevCommit B;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository init = createWorkRepository();
		FileBasedConfig cfg = init.getConfig();
		cfg.setInt("core", null, "repositoryformatversion", 1);
		cfg.setString("extensions", null, "refStorage", "reftree");
		cfg.save();

		repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(init.getDirectory())
				.build();
		refdb = (RefTreeDatabase) repo.getRefDatabase();
		bootstrap = refdb.getBootstrap();
		addRepoToClose(repo);

		RefUpdate head = refdb.newUpdate(HEAD, true);
		head.link(R_HEADS + MASTER);

		testRepo = new TestRepository<>(init);
		A = testRepo.commit().create();
		B = testRepo.commit(testRepo.getRevWalk().parseCommit(A));
	}

	@Test
	public void testHeadOrigHead() throws IOException {
		RefUpdate master = refdb.newUpdate(HEAD, false);
		master.setExpectedOldObjectId(ObjectId.zeroId());
		master.setNewObjectId(A);
		assertEquals(RefUpdate.Result.NEW, master.update());
		assertEquals(A, refdb.exactRef(HEAD).getObjectId());

		RefUpdate orig = refdb.newUpdate(ORIG_HEAD, true);
		orig.setNewObjectId(B);
		assertEquals(RefUpdate.Result.NEW, orig.update());

		File origFile = new File(repo.getDirectory(), ORIG_HEAD);
		assertEquals(B.name() + '\n', read(origFile));
		assertEquals(B, bootstrap.exactRef(ORIG_HEAD).getObjectId());
		assertEquals(B, refdb.exactRef(ORIG_HEAD).getObjectId());
		assertFalse(refdb.getRefs(ALL).containsKey(ORIG_HEAD));

		List<Ref> addl = refdb.getAdditionalRefs();
		assertEquals(2, addl.size());
		assertEquals(ORIG_HEAD, addl.get(1).getName());
		assertEquals(B, addl.get(1).getObjectId());
	}
}
