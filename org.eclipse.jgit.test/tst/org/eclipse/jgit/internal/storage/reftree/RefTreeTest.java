/*
 * Copyright (C) 2016, Google Inc.
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
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;

public class RefTreeTest {
	private static final String R_MASTER = R_HEADS + "master";
	private InMemoryRepository repo;
	private TestRepository<InMemoryRepository> git;

	@Before
	public void setUp() throws IOException {
		repo = new InMemoryRepository(new DfsRepositoryDescription("RefTree"));
		git = new TestRepository<>(repo);
	}

	@Test
	public void testEmptyTree() throws IOException {
		RefTree tree = RefTree.newEmptyTree();
		try (ObjectReader reader = repo.newObjectReader()) {
			assertNull(HEAD, tree.exactRef(reader, HEAD));
			assertNull("master", tree.exactRef(reader, R_MASTER));
		}
	}

	@Test
	public void testApplyThenReadMaster() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob id = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, id));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		assertSame(NOT_ATTEMPTED, cmd.getResult());

		try (ObjectReader reader = repo.newObjectReader()) {
			Ref m = tree.exactRef(reader, R_MASTER);
			assertNotNull(R_MASTER, m);
			assertEquals(R_MASTER, m.getName());
			assertEquals(id, m.getObjectId());
			assertTrue("peeled", m.isPeeled());
		}
	}

	@Test
	public void testUpdateMaster() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob id1 = git.blob("A");
		Command cmd1 = new Command(null, ref(R_MASTER, id1));
		assertTrue(tree.apply(Collections.singletonList(cmd1)));
		assertSame(NOT_ATTEMPTED, cmd1.getResult());

		RevBlob id2 = git.blob("B");
		Command cmd2 = new Command(ref(R_MASTER, id1), ref(R_MASTER, id2));
		assertTrue(tree.apply(Collections.singletonList(cmd2)));
		assertSame(NOT_ATTEMPTED, cmd2.getResult());

		try (ObjectReader reader = repo.newObjectReader()) {
			Ref m = tree.exactRef(reader, R_MASTER);
			assertNotNull(R_MASTER, m);
			assertEquals(R_MASTER, m.getName());
			assertEquals(id2, m.getObjectId());
			assertTrue("peeled", m.isPeeled());
		}
	}

	@Test
	public void testHeadSymref() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob id = git.blob("A");
		Command cmd1 = new Command(null, ref(R_MASTER, id));
		Command cmd2 = new Command(null, symref(HEAD, R_MASTER));
		assertTrue(tree.apply(Arrays.asList(new Command[] { cmd1, cmd2 })));
		assertSame(NOT_ATTEMPTED, cmd1.getResult());
		assertSame(NOT_ATTEMPTED, cmd2.getResult());

		try (ObjectReader reader = repo.newObjectReader()) {
			Ref m = tree.exactRef(reader, HEAD);
			assertNotNull(HEAD, m);
			assertEquals(HEAD, m.getName());
			assertTrue("symbolic", m.isSymbolic());
			assertNotNull(m.getTarget());
			assertEquals(R_MASTER, m.getTarget().getName());
			assertEquals(id, m.getTarget().getObjectId());
		}

		// Writing flushes some buffers, re-read from blob.
		ObjectId newId = write(tree);
		try (ObjectReader reader = repo.newObjectReader();
				RevWalk rw = new RevWalk(reader)) {
			tree = RefTree.read(reader, rw.parseTree(newId));
			Ref m = tree.exactRef(reader, HEAD);
			assertEquals(R_MASTER, m.getTarget().getName());
		}
	}

	@Test
	public void testTagIsPeeled() throws Exception {
		String name = "v1.0";
		RefTree tree = RefTree.newEmptyTree();
		RevBlob id = git.blob("A");
		RevTag tag = git.tag(name, id);

		String ref = R_TAGS + name;
		Command cmd = create(ref, tag);
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		assertSame(NOT_ATTEMPTED, cmd.getResult());

		try (ObjectReader reader = repo.newObjectReader()) {
			Ref m = tree.exactRef(reader, ref);
			assertNotNull(ref, m);
			assertEquals(ref, m.getName());
			assertEquals(tag, m.getObjectId());
			assertTrue("peeled", m.isPeeled());
			assertEquals(id, m.getPeeledObjectId());
		}
	}

	@Test
	public void testApplyAlreadyExists() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob a = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, a));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		ObjectId treeId = write(tree);

		RevBlob b = git.blob("B");
		Command cmd1 = create(R_MASTER, b);
		Command cmd2 = create(R_MASTER, b);
		assertFalse(tree.apply(Arrays.asList(new Command[] { cmd1, cmd2 })));
		assertSame(LOCK_FAILURE, cmd1.getResult());
		assertSame(REJECTED_OTHER_REASON, cmd2.getResult());
		assertEquals(JGitText.get().transactionAborted, cmd2.getMessage());
		assertEquals(treeId, write(tree));
	}

	@Test
	public void testApplyWrongOldId() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob a = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, a));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		ObjectId treeId = write(tree);

		RevBlob b = git.blob("B");
		RevBlob c = git.blob("C");
		Command cmd1 = update(R_MASTER, b, c);
		Command cmd2 = create(R_MASTER, b);
		assertFalse(tree.apply(Arrays.asList(new Command[] { cmd1, cmd2 })));
		assertSame(LOCK_FAILURE, cmd1.getResult());
		assertSame(REJECTED_OTHER_REASON, cmd2.getResult());
		assertEquals(JGitText.get().transactionAborted, cmd2.getMessage());
		assertEquals(treeId, write(tree));
	}

	@Test
	public void testApplyWrongOldIdButAlreadyCurrentIsNoOp() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob a = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, a));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		ObjectId treeId = write(tree);

		RevBlob b = git.blob("B");
		cmd = update(R_MASTER, b, a);
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		assertEquals(treeId, write(tree));
	}

	@Test
	public void testApplyCannotCreateSubdirectory() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob a = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, a));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		ObjectId treeId = write(tree);

		RevBlob b = git.blob("B");
		Command cmd1 = create(R_MASTER + "/fail", b);
		assertFalse(tree.apply(Collections.singletonList(cmd1)));
		assertSame(LOCK_FAILURE, cmd1.getResult());
		assertEquals(treeId, write(tree));
	}

	@Test
	public void testApplyCannotCreateParentRef() throws Exception {
		RefTree tree = RefTree.newEmptyTree();
		RevBlob a = git.blob("A");
		Command cmd = new Command(null, ref(R_MASTER, a));
		assertTrue(tree.apply(Collections.singletonList(cmd)));
		ObjectId treeId = write(tree);

		RevBlob b = git.blob("B");
		Command cmd1 = create("refs/heads", b);
		assertFalse(tree.apply(Collections.singletonList(cmd1)));
		assertSame(LOCK_FAILURE, cmd1.getResult());
		assertEquals(treeId, write(tree));
	}

	private static Ref ref(String name, ObjectId id) {
		return new ObjectIdRef.PeeledNonTag(LOOSE, name, id);
	}

	private static Ref symref(String name, String dest) {
		Ref d = new ObjectIdRef.PeeledNonTag(NEW, dest, null);
		return new SymbolicRef(name, d);
	}

	private Command create(String name, ObjectId id)
			throws MissingObjectException, IOException {
		return update(name, ObjectId.zeroId(), id);
	}

	private Command update(String name, ObjectId oldId, ObjectId newId)
			throws MissingObjectException, IOException {
		try (RevWalk rw = new RevWalk(repo)) {
			return new Command(rw, new ReceiveCommand(oldId, newId, name));
		}
	}

	private ObjectId write(RefTree tree) throws IOException {
		try (ObjectInserter ins = repo.newObjectInserter()) {
			ObjectId id = tree.writeTree(ins);
			ins.flush();
			return id;
		}
	}
}
