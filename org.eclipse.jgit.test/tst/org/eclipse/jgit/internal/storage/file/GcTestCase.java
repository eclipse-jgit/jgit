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

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;

public abstract class GcTestCase extends LocalDiskRepositoryTestCase {
	protected TestRepository<FileRepository> tr;
	protected FileRepository repo;
	protected GC gc;
	protected RepoStatistics stats;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createWorkRepository();
		tr = new TestRepository<>(repo, new RevWalk(repo),
				mockSystemReader);
		gc = new GC(repo);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Create a chain of commits of given depth.
	 * <p>
	 * Each commit contains one file named "a" containing the index of the
	 * commit in the chain as its content. The created commit chain is
	 * referenced from any ref.
	 * <p>
	 * A chain of depth = N will create 3*N objects in Gits object database. For
	 * each depth level three objects are created: the commit object, the
	 * top-level tree object and a blob for the content of the file "a".
	 *
	 * @param depth
	 *            the depth of the commit chain.
	 * @return the commit that is the tip of the commit chain
	 * @throws Exception
	 */
	protected RevCommit commitChain(int depth) throws Exception {
		if (depth <= 0)
			throw new IllegalArgumentException("Chain depth must be > 0");
		CommitBuilder cb = tr.commit();
		RevCommit tip;
		do {
			--depth;
			tip = cb.add("a", "" + depth).message("" + depth).create();
			cb = cb.child();
		} while (depth > 0);
		return tip;
	}

	/**
	 * Create a chain of commits of given depth with given number of added files
	 * per commit.
	 * <p>
	 * Each commit contains {@code files} files as its content. The created
	 * commit chain is referenced from any ref.
	 * <p>
	 * A chain will create {@code (2 + files) * depth} objects in Gits object
	 * database. For each depth level the following objects are created: the
	 * commit object, the top-level tree object and @code files} blobs for the
	 * content of the file "a".
	 *
	 * @param depth
	 *            the depth of the commit chain.
	 * @param width
	 *            number of files added per commit
	 * @return the commit that is the tip of the commit chain
	 * @throws Exception
	 */
	protected RevCommit commitChain(int depth, int width) throws Exception {
		if (depth <= 0) {
			throw new IllegalArgumentException("Chain depth must be > 0");
		}
		if (width <= 0) {
			throw new IllegalArgumentException("Number of files per commit must be > 0");
		}
		CommitBuilder cb = tr.commit();
		RevCommit tip = null;
		do {
			--depth;
			for (int i=0; i < width; i++) {
				String id = depth + "-" + i;
				cb.add("a" + id, id).message(id);
			}
			tip = cb.create();
			cb = cb.child();
		} while (depth > 0);
		return tip;
	}

	protected long lastModified(AnyObjectId objectId) throws IOException {
		return repo.getFS().lastModified(
				repo.getObjectDatabase().fileFor(objectId));
	}

	protected static void fsTick() throws InterruptedException, IOException {
		RepositoryTestCase.fsTick(null);
	}
}
