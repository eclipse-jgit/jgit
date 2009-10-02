/*
 * Copyright (C) 2009, Google Inc.
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

import java.util.Collections;
import java.util.Date;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.Tag;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/** Support for tests of the {@link RevWalk} class. */
public abstract class RevWalkTestCase extends RepositoryTestCase {
	protected ObjectWriter ow;

	protected RevTree emptyTree;

	protected long nowTick;

	protected RevWalk rw;

	public void setUp() throws Exception {
		super.setUp();
		ow = new ObjectWriter(db);
		rw = createRevWalk();
		emptyTree = rw.parseTree(ow.writeTree(new Tree(db)));
		nowTick = 1236977987000L;
	}

	protected RevWalk createRevWalk() {
		return new RevWalk(db);
	}

	protected void tick(final int secDelta) {
		nowTick += secDelta * 1000L;
	}

	protected RevBlob blob(final String content) throws Exception {
		return rw.lookupBlob(ow.writeBlob(Constants.encode(content)));
	}

	protected DirCacheEntry file(final String path, final RevBlob blob)
			throws Exception {
		final DirCacheEntry e = new DirCacheEntry(path);
		e.setFileMode(FileMode.REGULAR_FILE);
		e.setObjectId(blob);
		return e;
	}

	protected RevTree tree(final DirCacheEntry... entries) throws Exception {
		final DirCache dc = DirCache.newInCore();
		final DirCacheBuilder b = dc.builder();
		for (final DirCacheEntry e : entries)
			b.add(e);
		b.finish();
		return rw.lookupTree(dc.writeTree(ow));
	}

	protected RevObject get(final RevTree tree, final String path)
			throws Exception {
		final TreeWalk tw = new TreeWalk(db);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections
				.singleton(path)));
		tw.reset(tree);
		while (tw.next()) {
			if (tw.isSubtree() && !path.equals(tw.getPathString())) {
				tw.enterSubtree();
				continue;
			}
			final ObjectId entid = tw.getObjectId(0);
			final FileMode entmode = tw.getFileMode(0);
			return rw.lookupAny(entid, entmode.getObjectType());
		}
		fail("Can't find " + path + " in tree " + tree.name());
		return null; // never reached.
	}

	protected RevCommit commit(final RevCommit... parents) throws Exception {
		return commit(1, emptyTree, parents);
	}

	protected RevCommit commit(final RevTree tree, final RevCommit... parents)
			throws Exception {
		return commit(1, tree, parents);
	}

	protected RevCommit commit(final int secDelta, final RevCommit... parents)
			throws Exception {
		return commit(secDelta, emptyTree, parents);
	}

	protected RevCommit commit(final int secDelta, final RevTree tree,
			final RevCommit... parents) throws Exception {
		tick(secDelta);
		final Commit c = new Commit(db);
		c.setTreeId(tree);
		c.setParentIds(parents);
		c.setAuthor(new PersonIdent(jauthor, new Date(nowTick)));
		c.setCommitter(new PersonIdent(jcommitter, new Date(nowTick)));
		c.setMessage("");
		return rw.lookupCommit(ow.writeCommit(c));
	}

	protected RevTag tag(final String name, final RevObject dst)
			throws Exception {
		final Tag t = new Tag(db);
		t.setType(Constants.typeString(dst.getType()));
		t.setObjId(dst.toObjectId());
		t.setTag(name);
		t.setTagger(new PersonIdent(jcommitter, new Date(nowTick)));
		t.setMessage("");
		return (RevTag) rw.lookupAny(ow.writeTag(t), Constants.OBJ_TAG);
	}

	protected <T extends RevObject> T parse(final T t) throws Exception {
		rw.parseBody(t);
		return t;
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
