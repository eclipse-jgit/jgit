/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		tr.close();
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

	protected long lastModified(AnyObjectId objectId) {
		return repo.getFS()
				.lastModifiedInstant(repo.getObjectDatabase().fileFor(objectId))
				.toEpochMilli();
	}

	protected static void fsTick() throws InterruptedException, IOException {
		RepositoryTestCase.fsTick(null);
	}
}
