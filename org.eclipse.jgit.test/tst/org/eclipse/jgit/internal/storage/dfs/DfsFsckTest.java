/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	@Test
	public void testValidConnectivity() throws Exception {
		ObjectId blobId = ins
				.insert(Constants.OBJ_BLOB, Constants.encode("foo"));

		byte[] blobIdBytes = new byte[OBJECT_ID_LENGTH];
		blobId.copyRawTo(blobIdBytes, 0);
		byte[] data = concat(encodeASCII("100644 regular-file\0"), blobIdBytes);
		ObjectId treeId = ins.insert(Constants.OBJ_TREE, data);
		ins.flush();

		RevCommit commit = git.commit().message("0").setTopLevelTree(treeId)
				.create();

		git.update("master", commit);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);
		assertEquals(errors.getMissingObjects().size(), 0);
	}

	@Test
	public void testMissingObject() throws Exception {
		ObjectId blobId = ObjectId
				.fromString("19102815663d23f8b75a47e7a01965dcdc96468c");
		byte[] blobIdBytes = new byte[OBJECT_ID_LENGTH];
		blobId.copyRawTo(blobIdBytes, 0);
		byte[] data = concat(encodeASCII("100644 regular-file\0"), blobIdBytes);
		ObjectId treeId = ins.insert(Constants.OBJ_TREE, data);
		ins.flush();

		RevCommit commit = git.commit().message("0").setTopLevelTree(treeId)
				.create();

		git.update("master", commit);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);
		assertEquals(errors.getMissingObjects().size(), 1);
		assertEquals(errors.getMissingObjects().iterator().next(), blobId);
	}

	@Test
	public void testNonCommitHead() throws Exception {
		RevCommit commit0 = git.commit().message("0").create();
		StringBuilder b = new StringBuilder();
		b.append("object ");
		b.append(commit0.getName());
		b.append('\n');
		b.append("type commit\n");
		b.append("tag test-tag\n");
		b.append("tagger A. U. Thor <author@localhost> 1 +0000\n");

		byte[] data = encodeASCII(b.toString());
		ObjectId tagId = ins.insert(Constants.OBJ_TAG, data);
		ins.flush();

		git.update("master", tagId);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);
		assertEquals(errors.getCorruptObjects().size(), 0);
		assertEquals(errors.getNonCommitHeads().size(), 1);
		assertEquals(errors.getNonCommitHeads().iterator().next(),
				"refs/heads/master");
	}

	private ObjectId insertGitModules(String contents) throws IOException {
		ObjectId blobId = ins.insert(Constants.OBJ_BLOB,
				Constants.encode(contents));

		byte[] blobIdBytes = new byte[OBJECT_ID_LENGTH];
		blobId.copyRawTo(blobIdBytes, 0);
		byte[] data = concat(encodeASCII("100644 .gitmodules\0"), blobIdBytes);
		ins.insert(Constants.OBJ_TREE, data);
		ins.flush();

		return blobId;
	}

	@Test
	public void testInvalidGitModules() throws Exception {
		String fakeGitmodules = new StringBuilder()
				.append("[submodule \"test\"]\n")
				.append("    path = xlib\n")
				.append("    url = https://example.com/repo/xlib.git\n\n")
				.append("[submodule \"test2\"]\n")
				.append("    path = zlib\n")
				.append("    url = -upayload.sh\n")
				.toString();

		ObjectId blobId = insertGitModules(fakeGitmodules);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);
		assertEquals(errors.getCorruptObjects().size(), 1);

		CorruptObject error = errors.getCorruptObjects().iterator().next();
		assertEquals(error.getId(), blobId);
		assertEquals(error.getType(), Constants.OBJ_BLOB);
		assertEquals(error.getErrorType(), ErrorType.GITMODULES_URL);
	}


	@Test
	public void testValidGitModules() throws Exception {
		String fakeGitmodules = new StringBuilder()
				.append("[submodule \"test\"]\n")
				.append("    path = xlib\n")
				.append("    url = https://example.com/repo/xlib.git\n\n")
				.append("[submodule \"test2\"]\n")
				.append("    path = zlib\n")
				.append("    url = ok/path\n")
				.toString();

		insertGitModules(fakeGitmodules);

		DfsFsck fsck = new DfsFsck(repo);
		FsckError errors = fsck.check(null);
		assertEquals(errors.getCorruptObjects().size(), 0);
	}

}
