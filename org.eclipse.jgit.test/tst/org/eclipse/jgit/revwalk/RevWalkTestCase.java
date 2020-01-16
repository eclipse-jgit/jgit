/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertSame;

import java.util.Date;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/** Support for tests of the {@link RevWalk} class. */
public abstract class RevWalkTestCase extends RepositoryTestCase {
	private TestRepository<Repository> util;

	protected RevWalk rw;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		util = new TestRepository<>(db, createRevWalk());
		rw = util.getRevWalk();
	}

	protected RevWalk createRevWalk() {
		return new RevWalk(db);
	}

	protected Date getDate() {
		return util.getDate();
	}

	protected void tick(int secDelta) {
		util.tick(secDelta);
	}

	protected RevBlob blob(String content) throws Exception {
		return util.blob(content);
	}

	protected DirCacheEntry file(String path, RevBlob blob)
			throws Exception {
		return util.file(path, blob);
	}

	protected RevTree tree(DirCacheEntry... entries) throws Exception {
		return util.tree(entries);
	}

	protected RevObject get(RevTree tree, String path)
			throws Exception {
		return util.get(tree, path);
	}

	protected ObjectId unparsedCommit(ObjectId... parents) throws Exception {
		return util.unparsedCommit(parents);
	}

	protected RevCommit commit(RevCommit... parents) throws Exception {
		return util.commit(parents);
	}

	protected RevCommit commit(RevTree tree, RevCommit... parents)
			throws Exception {
		return util.commit(tree, parents);
	}

	protected RevCommit commit(int secDelta, RevCommit... parents)
			throws Exception {
		return util.commit(secDelta, parents);
	}

	protected RevCommit commit(final int secDelta, final RevTree tree,
			final RevCommit... parents) throws Exception {
		return util.commit(secDelta, tree, parents);
	}

	protected RevTag tag(String name, RevObject dst)
			throws Exception {
		return util.tag(name, dst);
	}

	protected CommitBuilder commitBuilder()
			throws Exception {
		return util.commit();
	}

	protected <T extends RevObject> T parseBody(T t) throws Exception {
		return util.parseBody(t);
	}

	protected void markStart(RevCommit commit) throws Exception {
		rw.markStart(commit);
	}

	protected void markUninteresting(RevCommit commit) throws Exception {
		rw.markUninteresting(commit);
	}

	protected void assertCommit(RevCommit exp, RevCommit act) {
		assertSame(exp, act);
	}
}
