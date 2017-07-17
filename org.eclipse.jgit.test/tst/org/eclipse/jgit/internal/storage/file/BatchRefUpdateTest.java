/*
 * Copyright (C) 2017 Google Inc.
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

import static org.eclipse.jgit.lib.ObjectId.zeroId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.StrictWorkMonitor;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BatchRefUpdateTest extends LocalDiskRepositoryTestCase {
	@Parameter
	public boolean atomic;

	@Parameters(name = "atomic={0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{ {Boolean.FALSE}, {Boolean.TRUE} });
	}

	private Repository diskRepo;
	private TestRepository<Repository> repo;
	private RefDirectory refdir;
	private RevCommit A;
	private RevCommit B;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		diskRepo = createBareRepository();
		refdir = (RefDirectory) diskRepo.getRefDatabase();

		repo = new TestRepository<>(diskRepo);
		A = repo.commit().create();
		B = repo.commit(repo.getRevWalk().parseCommit(A));
	}

	private BatchRefUpdate newBatchUpdate() {
		BatchRefUpdate u = refdir.newBatchUpdate();
		if (atomic) {
			assertTrue(u.isAtomic());
		} else {
			u.setAtomic(false);
		}
		return u;
	}

	@Test
	public void simpleNoForce() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						ReceiveCommand.Type.UPDATE_NONFASTFORWARD));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.REJECTED_NONFASTFORWARD, commands
				.get(1).getResult());
		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(0)));
			assertEquals("[HEAD, refs/heads/master, refs/heads/masters]", refs
					.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/masters").getObjectId());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
			assertEquals("[HEAD, refs/heads/master, refs/heads/masters]", refs
					.keySet().toString());
			assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/masters").getObjectId());
		}
	}

	@Test
	public void simpleForce() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						ReceiveCommand.Type.UPDATE_NONFASTFORWARD));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
		assertEquals(ReceiveCommand.Result.OK, commands.get(1).getResult());
		assertEquals("[HEAD, refs/heads/master, refs/heads/masters]", refs
				.keySet().toString());
		assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
		assertEquals(A.getId(), refs.get("refs/heads/masters").getObjectId());
	}

	@Test
	public void nonFastForwardDoesNotDoExpensiveMergeCheck() throws IOException {
		writeLooseRef("refs/heads/master", B);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(B, A, "refs/heads/master",
						ReceiveCommand.Type.UPDATE_NONFASTFORWARD));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo) {
			@Override
			public boolean isMergedInto(RevCommit base, RevCommit tip) {
				throw new AssertionError("isMergedInto() should not be called");
			}
		}, new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
		assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
	}

	@Test
	public void fileDirectoryConflict() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x",
						ReceiveCommand.Type.CREATE),
				new ReceiveCommand(zeroId(), A, "refs/heads",
						ReceiveCommand.Type.CREATE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate
				.execute(new RevWalk(diskRepo), NullProgressMonitor.INSTANCE);
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);

		if (atomic) {
			// Atomic update sees that master and master/x are conflicting, then marks
			// the first one in the list as LOCK_FAILURE and aborts the rest.
			assertEquals(ReceiveCommand.Result.LOCK_FAILURE,
					commands.get(0).getResult());
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(1)));
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(2)));
			assertEquals("[HEAD, refs/heads/master, refs/heads/masters]", refs
					.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/masters").getObjectId());
		} else {
			// Non-atomic updates are applied in order: master succeeds, then master/x
			// fails due to conflict.
			assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
			assertEquals(ReceiveCommand.Result.LOCK_FAILURE, commands.get(1)
					.getResult());
			assertEquals(ReceiveCommand.Result.LOCK_FAILURE, commands.get(2)
					.getResult());
			assertEquals("[HEAD, refs/heads/master, refs/heads/masters]", refs
					.keySet().toString());
			assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/masters").getObjectId());
		}
	}

	@Test
	public void conflictThanksToDelete() throws IOException {
		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/masters", B);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/masters/x",
						ReceiveCommand.Type.CREATE),
				new ReceiveCommand(B, zeroId(), "refs/heads/masters",
						ReceiveCommand.Type.DELETE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
		assertEquals(ReceiveCommand.Result.OK, commands.get(1).getResult());
		assertEquals(ReceiveCommand.Result.OK, commands.get(2).getResult());
		assertEquals("[HEAD, refs/heads/master, refs/heads/masters/x]", refs
				.keySet().toString());
		assertEquals(A.getId(), refs.get("refs/heads/masters/x").getObjectId());
	}

	@Test
	public void updateToMissingObject() throws IOException {
		writeLooseRef("refs/heads/master", A);
		ObjectId bad =
				ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, bad, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2",
						ReceiveCommand.Type.CREATE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), NullProgressMonitor.INSTANCE);
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.REJECTED_MISSING_OBJECT,
				commands.get(0).getResult());

		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(1)));
			assertEquals("[HEAD, refs/heads/master]", refs.keySet()
					.toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(1).getResult());
			assertEquals("[HEAD, refs/heads/foo2, refs/heads/master]", refs.keySet()
					.toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/foo2").getObjectId());
		}
	}

	@Test
	public void addMissingObject() throws IOException {
		writeLooseRef("refs/heads/master", A);
		ObjectId bad =
				ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), bad, "refs/heads/foo2",
						ReceiveCommand.Type.CREATE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), NullProgressMonitor.INSTANCE);
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.REJECTED_MISSING_OBJECT,
				commands.get(1).getResult());

		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(0)));
			assertEquals("[HEAD, refs/heads/master]", refs.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
			assertEquals("[HEAD, refs/heads/master]", refs.keySet()
					.toString());
			assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
		}
	}

	@Test
	public void oneNonExistentRef() throws IOException {
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/foo1",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2",
						ReceiveCommand.Type.CREATE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.LOCK_FAILURE,
				commands.get(0).getResult());

		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(1)));
			assertEquals("[]", refs.keySet().toString());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(1).getResult());
			assertEquals("[refs/heads/foo2]", refs.keySet().toString());
			assertEquals(B.getId(), refs.get("refs/heads/foo2").getObjectId());
		}
	}

	@Test
	public void oneRefWrongOldValue() throws IOException {
		writeLooseRef("refs/heads/master", A);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(B, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2",
						ReceiveCommand.Type.CREATE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.LOCK_FAILURE,
				commands.get(0).getResult());

		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(1)));
			assertEquals("[HEAD, refs/heads/master]", refs.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(1).getResult());
			assertEquals("[HEAD, refs/heads/foo2, refs/heads/master]", refs
					.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
			assertEquals(B.getId(), refs.get("refs/heads/foo2").getObjectId());
		}
	}

	@Test
	public void nonExistentRef() throws IOException {
		writeLooseRef("refs/heads/master", A);
		List<ReceiveCommand> commands = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master",
						ReceiveCommand.Type.UPDATE),
				new ReceiveCommand(A, zeroId(), "refs/heads/foo2",
						ReceiveCommand.Type.DELETE));
		BatchRefUpdate batchUpdate = newBatchUpdate();
		batchUpdate.setAllowNonFastForwards(true);
		batchUpdate.addCommand(commands);
		batchUpdate.execute(new RevWalk(diskRepo), new StrictWorkMonitor());
		Map<String, Ref> refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(ReceiveCommand.Result.LOCK_FAILURE,
				commands.get(1).getResult());

		if (atomic) {
			assertTrue(ReceiveCommand.isTransactionAborted(commands.get(0)));
			assertEquals("[HEAD, refs/heads/master]", refs.keySet().toString());
			assertEquals(A.getId(), refs.get("refs/heads/master").getObjectId());
		} else {
			assertEquals(ReceiveCommand.Result.OK, commands.get(0).getResult());
			assertEquals("[HEAD, refs/heads/master]", refs.keySet().toString());
			assertEquals(B.getId(), refs.get("refs/heads/master").getObjectId());
		}
	}

	private void writeLooseRef(String name, AnyObjectId id) throws IOException {
		write(new File(diskRepo.getDirectory(), name), id.name() + "\n");
	}
}
