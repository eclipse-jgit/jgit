/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.junit.JGitTestUtil.concat;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.eclipse.jgit.internal.fsck.FsckError;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptObject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectChecker.ErrorType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class DfsFsckTest {
	private TestRepository<InMemoryRepository> git;

	private InMemoryRepository repo;

	private ObjectInserter ins;

	@Before
	public void setUp() throws IOException {
		DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
		git = new TestRepository<>(new InMemoryRepository(desc));
		repo = git.getRepository();
		ins = repo.newObjectInserter();
	}

	@Test
	public void testHealthyRepo() throws Exception {
		RevCommit commit0 = git.commit().message("0").create();
		RevCommit commit1 = git.commit().message("1").parent(commit0).create();
		git.update("master", commit1);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 0);
		assertEquals(errors.getMissingObjects().size(), 0);
		assertEquals(errors.getCorruptIndices().size(), 0);
	}

	@Test
	public void testCommitWithCorruptAuthor() throws Exception {
		StringBuilder b = new StringBuilder();
		b.append("tree be9bfa841874ccc9f2ef7c48d0c76226f89b7189\n");
		b.append("author b <b@c> <b@c> 0 +0000\n");
		b.append("committer <> 0 +0000\n");
		byte[] data = encodeASCII(b.toString());
		ObjectId id = ins.insert(Constants.OBJ_COMMIT, data);
		ins.flush();

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 1);
		CorruptObject o = errors.getCorruptObjects().iterator().next();
		assertTrue(o.getId().equals(id));
		assertEquals(o.getErrorType(), ErrorType.BAD_DATE);
	}

	@Test
	public void testCommitWithoutTree() throws Exception {
		StringBuilder b = new StringBuilder();
		b.append("parent ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		byte[] data = encodeASCII(b.toString());
		ObjectId id = ins.insert(Constants.OBJ_COMMIT, data);
		ins.flush();

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 1);
		CorruptObject o = errors.getCorruptObjects().iterator().next();
		assertTrue(o.getId().equals(id));
		assertEquals(o.getErrorType(), ErrorType.MISSING_TREE);
	}

	@Test
	public void testTagWithoutObject() throws Exception {
		StringBuilder b = new StringBuilder();
		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");
		byte[] data = encodeASCII(b.toString());
		ObjectId id = ins.insert(Constants.OBJ_TAG, data);
		ins.flush();

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 1);
		CorruptObject o = errors.getCorruptObjects().iterator().next();
		assertTrue(o.getId().equals(id));
		assertEquals(o.getErrorType(), ErrorType.MISSING_OBJECT);
	}

	@Test
	public void testTreeWithNullSha() throws Exception {
		byte[] data = concat(encodeASCII("100644 A"), new byte[] { '\0' },
				new byte[OBJECT_ID_LENGTH]);
		ObjectId id = ins.insert(Constants.OBJ_TREE, data);
		ins.flush();

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 1);
		CorruptObject o = errors.getCorruptObjects().iterator().next();
		assertTrue(o.getId().equals(id));
		assertEquals(o.getErrorType(), ErrorType.NULL_SHA1);
	}

	@Test
	public void testMultipleInvalidObjects() throws Exception {
		StringBuilder b = new StringBuilder();
		b.append("tree ");
		b.append("be9bfa841874ccc9f2ef7c48d0c76226f89b7189");
		b.append('\n');
		b.append("parent ");
		b.append("\n");
		byte[] data = encodeASCII(b.toString());
		ObjectId id1 = ins.insert(Constants.OBJ_COMMIT, data);

		b = new StringBuilder();
		b.append("100644");
		data = encodeASCII(b.toString());
		ObjectId id2 = ins.insert(Constants.OBJ_TREE, data);

		ins.flush();

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);

		assertEquals(errors.getCorruptObjects().size(), 2);
		for (CorruptObject o : errors.getCorruptObjects()) {
			if (o.getId().equals(id1)) {
				assertEquals(o.getErrorType(), ErrorType.BAD_PARENT_SHA1);
			} else if (o.getId().equals(id2)) {
				assertNull(o.getErrorType());
			} else {
				fail();
			}
		}
	}
}
