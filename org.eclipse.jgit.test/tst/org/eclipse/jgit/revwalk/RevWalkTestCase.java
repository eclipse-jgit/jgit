/*
 * Copyright (C) 2009-2010, Google Inc.
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

import static org.junit.Assert.assertSame;

import java.util.Date;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.Repository;

/** Support for tests of the {@link RevWalk} class. */
public abstract class RevWalkTestCase extends RepositoryTestCase {
	private TestRepository<Repository> util;

	protected RevWalk rw;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		util = new TestRepository<Repository>(db, createRevWalk());
		rw = util.getRevWalk();
	}

	protected RevWalk createRevWalk() {
		return new RevWalk(db);
	}

	protected Date getClock() {
		return util.getClock();
	}

	protected void tick(final int secDelta) {
		util.tick(secDelta);
	}

	protected RevBlob blob(final String content) throws Exception {
		return util.blob(content);
	}

	protected DirCacheEntry file(final String path, final RevBlob blob)
			throws Exception {
		return util.file(path, blob);
	}

	protected RevTree tree(final DirCacheEntry... entries) throws Exception {
		return util.tree(entries);
	}

	protected RevObject get(final RevTree tree, final String path)
			throws Exception {
		return util.get(tree, path);
	}

	protected RevCommit commit(final RevCommit... parents) throws Exception {
		return util.commit(parents);
	}

	protected RevCommit commit(final RevTree tree, final RevCommit... parents)
			throws Exception {
		return util.commit(tree, parents);
	}

	protected RevCommit commit(final int secDelta, final RevCommit... parents)
			throws Exception {
		return util.commit(secDelta, parents);
	}

	protected RevCommit commit(final int secDelta, final RevTree tree,
			final RevCommit... parents) throws Exception {
		return util.commit(secDelta, tree, parents);
	}

	protected RevTag tag(final String name, final RevObject dst)
			throws Exception {
		return util.tag(name, dst);
	}

	protected CommitBuilder commitBuilder()
			throws Exception {
		return util.commit();
	}

	protected <T extends RevObject> T parseBody(final T t) throws Exception {
		return util.parseBody(t);
	}

	protected void markStart(final RevCommit commit) throws Exception {
		rw.markStart(commit);
	}

	protected void markUninteresting(final RevCommit commit) throws Exception {
		rw.markUninteresting(commit);
	}

	protected void assertCommit(final RevCommit exp, final RevCommit act) {
		assertSame(exp, act);
	}
}
