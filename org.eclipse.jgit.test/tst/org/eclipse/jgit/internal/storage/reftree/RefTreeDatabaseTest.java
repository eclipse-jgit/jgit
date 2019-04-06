/*
 * Copyright (C) 2010, 2013, 2016 Google Inc.
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
import static org.eclipse.jgit.lib.Constants.ORIG_HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;

public class RefTreeDatabaseTest {
	private InMemRefTreeRepo repo;
	private RefTreeDatabase refdb;
	private RefDatabase bootstrap;

	private TestRepository<InMemRefTreeRepo> testRepo;
	private RevCommit A;
	private RevCommit B;
	private RevTag v1_0;

	@Before
	public void setUp() throws Exception {
		repo = new InMemRefTreeRepo(new DfsRepositoryDescription("test"));
		bootstrap = refdb.getBootstrap();

		testRepo = new TestRepository<>(repo);
		A = testRepo.commit().create();
		B = testRepo.commit(testRepo.getRevWalk().parseCommit(A));
		v1_0 = testRepo.tag("v1_0", B);
		testRepo.getRevWalk().parseBody(v1_0);
	}

	@Test
	public void testSupportsAtomic() {
		assertTrue(refdb.performsAtomicTransactions());
	}

	@Test
	public void testGetRefs_EmptyDatabase() throws IOException {
		assertTrue("no references", refdb.getRefs(ALL).isEmpty());
		assertTrue("no references", refdb.getRefs(R_HEADS).isEmpty());
		assertTrue("no references", refdb.getRefs(R_TAGS).isEmpty());
		assertTrue("no references", refdb.getAdditionalRefs().isEmpty());
	}

	@Test
	public void testGetAdditionalRefs() throws IOException {
		update("refs/heads/master", A);

		List<Ref> addl = refdb.getAdditionalRefs();
		assertEquals(1, addl.size());
		assertEquals("refs/txn/committed", addl.get(0).getName());
		assertEquals(getTxnCommitted(), addl.get(0).getObjectId());
	}

	@Test
	public void testGetRefs_HeadOnOneBranch() throws IOException {
		symref(HEAD, "refs/heads/master");
		update("refs/heads/master", A);

		Map<String, Ref> all = refdb.getRefs(ALL);
		assertEquals(2, all.size());
		assertTrue("has HEAD", all.containsKey(HEAD));
		assertTrue("has master", all.containsKey("refs/heads/master"));

		Ref head = all.get(HEAD);
		Ref master = all.get("refs/heads/master");

		assertEquals(HEAD, head.getName());
		assertTrue(head.isSymbolic());
		assertSame(LOOSE, head.getStorage());
		assertSame("uses same ref as target", master, head.getTarget());

		assertEquals("refs/heads/master", master.getName());
		assertFalse(master.isSymbolic());
		assertSame(PACKED, master.getStorage());
		assertEquals(A, master.getObjectId());
	}

	@Test
	public void testGetRefs_DetachedHead() throws IOException {
		update(HEAD, A);

		Map<String, Ref> all = refdb.getRefs(ALL);
		assertEquals(1, all.size());
		assertTrue("has HEAD", all.containsKey(HEAD));

		Ref head = all.get(HEAD);
		assertEquals(HEAD, head.getName());
		assertFalse(head.isSymbolic());
		assertSame(PACKED, head.getStorage());
		assertEquals(A, head.getObjectId());
	}

	@Test
	public void testGetRefs_DeeplyNestedBranch() throws IOException {
		String name = "refs/heads/a/b/c/d/e/f/g/h/i/j/k";
		update(name, A);

		Map<String, Ref> all = refdb.getRefs(ALL);
		assertEquals(1, all.size());

		Ref r = all.get(name);
		assertEquals(name, r.getName());
		assertFalse(r.isSymbolic());
		assertSame(PACKED, r.getStorage());
		assertEquals(A, r.getObjectId());
	}

	@Test
	public void testGetRefs_HeadBranchNotBorn() throws IOException {
		update("refs/heads/A", A);
		update("refs/heads/B", B);

		Map<String, Ref> all = refdb.getRefs(ALL);
		assertEquals(2, all.size());
		assertFalse("no HEAD", all.containsKey(HEAD));

		Ref a = all.get("refs/heads/A");
		Ref b = all.get("refs/heads/B");

		assertEquals(A, a.getObjectId());
		assertEquals(B, b.getObjectId());

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/heads/B", b.getName());
	}

	@Test
	public void testGetRefs_HeadsOnly() throws IOException {
		update("refs/heads/A", A);
		update("refs/heads/B", B);
		update("refs/tags/v1.0", v1_0);

		Map<String, Ref> heads = refdb.getRefs(R_HEADS);
		assertEquals(2, heads.size());

		Ref a = heads.get("A");
		Ref b = heads.get("B");

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/heads/B", b.getName());

		assertEquals(A, a.getObjectId());
		assertEquals(B, b.getObjectId());
	}

	@Test
	public void testGetRefs_TagsOnly() throws IOException {
		update("refs/heads/A", A);
		update("refs/heads/B", B);
		update("refs/tags/v1.0", v1_0);

		Map<String, Ref> tags = refdb.getRefs(R_TAGS);
		assertEquals(1, tags.size());

		Ref a = tags.get("v1.0");
		assertEquals("refs/tags/v1.0", a.getName());
		assertEquals(v1_0, a.getObjectId());
		assertTrue(a.isPeeled());
		assertEquals(v1_0.getObject(), a.getPeeledObjectId());
	}

	@Test
	public void testGetRefs_HeadsSymref() throws IOException {
		symref("refs/heads/other", "refs/heads/master");
		update("refs/heads/master", A);

		Map<String, Ref> heads = refdb.getRefs(R_HEADS);
		assertEquals(2, heads.size());

		Ref master = heads.get("master");
		Ref other = heads.get("other");

		assertEquals("refs/heads/master", master.getName());
		assertEquals(A, master.getObjectId());

		assertEquals("refs/heads/other", other.getName());
		assertEquals(A, other.getObjectId());
		assertSame(master, other.getTarget());
	}

	@Test
	public void testGetRefs_InvalidPrefixes() throws IOException {
		update("refs/heads/A", A);

		assertTrue("empty refs/heads", refdb.getRefs("refs/heads").isEmpty());
		assertTrue("empty objects", refdb.getRefs("objects").isEmpty());
		assertTrue("empty objects/", refdb.getRefs("objects/").isEmpty());
	}

	@Test
	public void testGetRefs_DiscoversNew() throws IOException {
		update("refs/heads/master", A);
		Map<String, Ref> orig = refdb.getRefs(ALL);

		update("refs/heads/next", B);
		Map<String, Ref> next = refdb.getRefs(ALL);

		assertEquals(1, orig.size());
		assertEquals(2, next.size());

		assertFalse(orig.containsKey("refs/heads/next"));
		assertTrue(next.containsKey("refs/heads/next"));

		assertEquals(A, next.get("refs/heads/master").getObjectId());
		assertEquals(B, next.get("refs/heads/next").getObjectId());
	}

	@Test
	public void testGetRefs_DiscoversModified() throws IOException {
		symref(HEAD, "refs/heads/master");
		update("refs/heads/master", A);

		Map<String, Ref> all = refdb.getRefs(ALL);
		assertEquals(A, all.get(HEAD).getObjectId());

		update("refs/heads/master", B);
		all = refdb.getRefs(ALL);
		assertEquals(B, all.get(HEAD).getObjectId());
		assertEquals(B, refdb.exactRef(HEAD).getObjectId());
	}

	@Test
	public void testGetRefs_CycleInSymbolicRef() throws IOException {
		symref("refs/1", "refs/2");
		symref("refs/2", "refs/3");
		symref("refs/3", "refs/4");
		symref("refs/4", "refs/5");
		symref("refs/5", "refs/end");
		update("refs/end", A);

		Map<String, Ref> all = refdb.getRefs(ALL);
		Ref r = all.get("refs/1");
		assertNotNull("has 1", r);

		assertEquals("refs/1", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/2", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/3", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/4", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/5", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/end", r.getName());
		assertEquals(A, r.getObjectId());
		assertFalse(r.isSymbolic());

		symref("refs/5", "refs/6");
		symref("refs/6", "refs/end");
		all = refdb.getRefs(ALL);
		assertNull("mising 1 due to cycle", all.get("refs/1"));
		assertEquals(A, all.get("refs/2").getObjectId());
		assertEquals(A, all.get("refs/3").getObjectId());
		assertEquals(A, all.get("refs/4").getObjectId());
		assertEquals(A, all.get("refs/5").getObjectId());
		assertEquals(A, all.get("refs/6").getObjectId());
		assertEquals(A, all.get("refs/end").getObjectId());
	}

	@Test
	public void testGetRef_NonExistingBranchConfig() throws IOException {
		assertNull("find branch config", refdb.findRef("config"));
		assertNull("find branch config", refdb.findRef("refs/heads/config"));
	}

	@Test
	public void testGetRef_FindBranchConfig() throws IOException {
		update("refs/heads/config", A);

		for (String t : new String[] { "config", "refs/heads/config" }) {
			Ref r = refdb.findRef(t);
			assertNotNull("find branch config (" + t + ")", r);
			assertEquals("for " + t, "refs/heads/config", r.getName());
			assertEquals("for " + t, A, r.getObjectId());
		}
	}

	@Test
	public void testFirstExactRef() throws IOException {
		update("refs/heads/A", A);
		update("refs/tags/v1.0", v1_0);

		Ref a = refdb.firstExactRef("refs/heads/A", "refs/tags/v1.0");
		Ref one = refdb.firstExactRef("refs/tags/v1.0", "refs/heads/A");

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/tags/v1.0", one.getName());

		assertEquals(A, a.getObjectId());
		assertEquals(v1_0, one.getObjectId());
	}

	@Test
	public void testExactRef_DiscoversModified() throws IOException {
		symref(HEAD, "refs/heads/master");
		update("refs/heads/master", A);
		assertEquals(A, refdb.exactRef(HEAD).getObjectId());

		update("refs/heads/master", B);
		assertEquals(B, refdb.exactRef(HEAD).getObjectId());
	}

	@Test
	public void testIsNameConflicting() throws IOException {
		update("refs/heads/a/b", A);
		update("refs/heads/q", B);

		// new references cannot replace an existing container
		assertTrue(refdb.isNameConflicting("refs"));
		assertTrue(refdb.isNameConflicting("refs/heads"));
		assertTrue(refdb.isNameConflicting("refs/heads/a"));

		// existing reference is not conflicting
		assertFalse(refdb.isNameConflicting("refs/heads/a/b"));

		// new references are not conflicting
		assertFalse(refdb.isNameConflicting("refs/heads/a/d"));
		assertFalse(refdb.isNameConflicting("refs/heads/master"));

		// existing reference must not be used as a container
		assertTrue(refdb.isNameConflicting("refs/heads/a/b/c"));
		assertTrue(refdb.isNameConflicting("refs/heads/q/master"));

		// refs/txn/ names always conflict.
		assertTrue(refdb.isNameConflicting(refdb.getTxnCommitted()));
		assertTrue(refdb.isNameConflicting("refs/txn/foo"));
	}

	@Test
	public void testUpdate_RefusesRefsTxnNamespace() throws IOException {
		ObjectId txnId = getTxnCommitted();

		RefUpdate u = refdb.newUpdate("refs/txn/tmp", false);
		u.setNewObjectId(B);
		assertEquals(RefUpdate.Result.LOCK_FAILURE, u.update());
		assertEquals(txnId, getTxnCommitted());

		ReceiveCommand cmd = command(null, B, "refs/txn/tmp");
		BatchRefUpdate batch = refdb.newBatchUpdate();
		batch.addCommand(cmd);
		batch.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);

		assertEquals(REJECTED_OTHER_REASON, cmd.getResult());
		assertEquals(MessageFormat.format(JGitText.get().invalidRefName,
				"refs/txn/tmp"), cmd.getMessage());
		assertEquals(txnId, getTxnCommitted());
	}

	@Test
	public void testUpdate_RefusesDotLockInRefName() throws IOException {
		ObjectId txnId = getTxnCommitted();

		RefUpdate u = refdb.newUpdate("refs/heads/pu.lock", false);
		u.setNewObjectId(B);
		assertEquals(RefUpdate.Result.REJECTED, u.update());
		assertEquals(txnId, getTxnCommitted());

		ReceiveCommand cmd = command(null, B, "refs/heads/pu.lock");
		BatchRefUpdate batch = refdb.newBatchUpdate();
		batch.addCommand(cmd);
		batch.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);

		assertEquals(REJECTED_OTHER_REASON, cmd.getResult());
		assertEquals(JGitText.get().funnyRefname, cmd.getMessage());
		assertEquals(txnId, getTxnCommitted());
	}

	@Test
	public void testUpdate_RefusesOrigHeadOnBare() throws IOException {
		assertTrue(refdb.getRepository().isBare());
		ObjectId txnId = getTxnCommitted();

		RefUpdate orig = refdb.newUpdate(ORIG_HEAD, true);
		orig.setNewObjectId(B);
		assertEquals(RefUpdate.Result.LOCK_FAILURE, orig.update());
		assertEquals(txnId, getTxnCommitted());

		ReceiveCommand cmd = command(null, B, ORIG_HEAD);
		BatchRefUpdate batch = refdb.newBatchUpdate();
		batch.addCommand(cmd);
		batch.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);
		assertEquals(REJECTED_OTHER_REASON, cmd.getResult());
		assertEquals(
				MessageFormat.format(JGitText.get().invalidRefName, ORIG_HEAD),
				cmd.getMessage());
		assertEquals(txnId, getTxnCommitted());
	}

	@Test
	public void testBatchRefUpdate_NonFastForwardAborts() throws IOException {
		update("refs/heads/master", A);
		update("refs/heads/masters", B);
		ObjectId txnId = getTxnCommitted();

		List<ReceiveCommand> commands = Arrays.asList(
				command(A, B, "refs/heads/master"),
				command(B, A, "refs/heads/masters"));
		BatchRefUpdate batchUpdate = refdb.newBatchUpdate();
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);
		assertEquals(txnId, getTxnCommitted());

		assertEquals(REJECTED_NONFASTFORWARD,
				commands.get(1).getResult());
		assertEquals(REJECTED_OTHER_REASON,
				commands.get(0).getResult());
		assertEquals(JGitText.get().transactionAborted,
				commands.get(0).getMessage());
	}

	@Test
	public void testBatchRefUpdate_ForceUpdate() throws IOException {
		update("refs/heads/master", A);
		update("refs/heads/masters", B);
		ObjectId txnId = getTxnCommitted();

		List<ReceiveCommand> commands = Arrays.asList(
				command(A, B, "refs/heads/master"),
				command(B, A, "refs/heads/masters"));
		BatchRefUpdate batchUpdate = refdb.newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);
		assertNotEquals(txnId, getTxnCommitted());

		Map<String, Ref> refs = refdb.getRefs(ALL);
		assertEquals(OK, commands.get(0).getResult());
		assertEquals(OK, commands.get(1).getResult());
		assertEquals(
				"[refs/heads/master, refs/heads/masters]",
				refs.keySet().toString());
		assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
		assertEquals(A.getId(), refs.get("refs/heads/masters").getObjectId());
	}

	@Test
	public void testBatchRefUpdate_NonFastForwardDoesNotDoExpensiveMergeCheck()
			throws IOException {
		update("refs/heads/master", B);
		ObjectId txnId = getTxnCommitted();

		List<ReceiveCommand> commands = Arrays.asList(
				command(B, A, "refs/heads/master"));
		BatchRefUpdate batchUpdate = refdb.newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(repo) {
			@Override
			public boolean isMergedInto(RevCommit base, RevCommit tip) {
				fail("isMergedInto() should not be called");
				return false;
			}
		}, NullProgressMonitor.INSTANCE);
		assertNotEquals(txnId, getTxnCommitted());

		Map<String, Ref> refs = refdb.getRefs(ALL);
		assertEquals(OK, commands.get(0).getResult());
		assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
	}

	@Test
	public void testBatchRefUpdate_ConflictCausesAbort() throws IOException {
		update("refs/heads/master", A);
		update("refs/heads/masters", B);
		ObjectId txnId = getTxnCommitted();

		List<ReceiveCommand> commands = Arrays.asList(
				command(A, B, "refs/heads/master"),
				command(null, A, "refs/heads/master/x"),
				command(null, A, "refs/heads"));
		BatchRefUpdate batchUpdate = refdb.newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);
		assertEquals(txnId, getTxnCommitted());

		assertEquals(LOCK_FAILURE, commands.get(0).getResult());

		assertEquals(REJECTED_OTHER_REASON, commands.get(1).getResult());
		assertEquals(JGitText.get().transactionAborted,
				commands.get(1).getMessage());

		assertEquals(REJECTED_OTHER_REASON, commands.get(2).getResult());
		assertEquals(JGitText.get().transactionAborted,
				commands.get(2).getMessage());
	}

	@Test
	public void testBatchRefUpdate_NoConflictIfDeleted() throws IOException {
		update("refs/heads/master", A);
		update("refs/heads/masters", B);
		ObjectId txnId = getTxnCommitted();

		List<ReceiveCommand> commands = Arrays.asList(
				command(A, B, "refs/heads/master"),
				command(null, A, "refs/heads/masters/x"),
				command(B, null, "refs/heads/masters"));
		BatchRefUpdate batchUpdate = refdb.newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(repo), NullProgressMonitor.INSTANCE);
		assertNotEquals(txnId, getTxnCommitted());

		assertEquals(OK, commands.get(0).getResult());
		assertEquals(OK, commands.get(1).getResult());
		assertEquals(OK, commands.get(2).getResult());

		Map<String, Ref> refs = refdb.getRefs(ALL);
		assertEquals(
				"[refs/heads/master, refs/heads/masters/x]",
				refs.keySet().toString());
		assertEquals(A.getId(), refs.get("refs/heads/masters/x").getObjectId());
	}

	private ObjectId getTxnCommitted() throws IOException {
		Ref r = bootstrap.exactRef(refdb.getTxnCommitted());
		if (r != null && r.getObjectId() != null) {
			return r.getObjectId();
		}
		return ObjectId.zeroId();
	}

	private static ReceiveCommand command(AnyObjectId a, AnyObjectId b,
			String name) {
		return new ReceiveCommand(
				a != null ? a.copy() : ObjectId.zeroId(),
				b != null ? b.copy() : ObjectId.zeroId(),
				name);
	}

	private void symref(String name, String dst)
			throws IOException {
		commit((ObjectReader reader, RefTree tree) -> {
			Ref old = tree.exactRef(reader, name);
			Command n = new Command(old, new SymbolicRef(name,
					new ObjectIdRef.Unpeeled(Ref.Storage.NEW, dst, null)));
			return tree.apply(Collections.singleton(n));
		});
	}

	private void update(String name, ObjectId id)
			throws IOException {
		commit((ObjectReader reader, RefTree tree) -> {
			Ref old = tree.exactRef(reader, name);
			Command n;
			try (RevWalk rw = new RevWalk(repo)) {
				n = new Command(old, Command.toRef(rw, id, null, name, true));
			}
			return tree.apply(Collections.singleton(n));
		});
	}

	interface Function {
		boolean apply(ObjectReader reader, RefTree tree) throws IOException;
	}

	private void commit(Function fun) throws IOException {
		try (ObjectReader reader = repo.newObjectReader();
				ObjectInserter inserter = repo.newObjectInserter();
				RevWalk rw = new RevWalk(reader)) {
			RefUpdate u = bootstrap.newUpdate(refdb.getTxnCommitted(), false);
			CommitBuilder cb = new CommitBuilder();
			testRepo.setAuthorAndCommitter(cb);

			Ref ref = bootstrap.exactRef(refdb.getTxnCommitted());
			RefTree tree;
			if (ref != null && ref.getObjectId() != null) {
				tree = RefTree.read(reader, rw.parseTree(ref.getObjectId()));
				cb.setParentId(ref.getObjectId());
				u.setExpectedOldObjectId(ref.getObjectId());
			} else {
				tree = RefTree.newEmptyTree();
				u.setExpectedOldObjectId(ObjectId.zeroId());
			}

			assertTrue(fun.apply(reader, tree));
			cb.setTreeId(tree.writeTree(inserter));
			u.setNewObjectId(inserter.insert(cb));
			inserter.flush();
			switch (u.update(rw)) {
			case NEW:
			case FAST_FORWARD:
				break;
			default:
				fail("Expected " + u.getName() + " to update");
			}
		}
	}

	private class InMemRefTreeRepo extends InMemoryRepository {
		private final RefTreeDatabase refs;

		InMemRefTreeRepo(DfsRepositoryDescription repoDesc) {
			super(repoDesc);
			refs = new RefTreeDatabase(this, super.getRefDatabase(),
					"refs/txn/committed");
			RefTreeDatabaseTest.this.refdb = refs;
		}

		@Override
		public RefDatabase getRefDatabase() {
			return refs;
		}
	}
}
