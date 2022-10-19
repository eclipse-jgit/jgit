/*
 * Copyright (C) 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.LOCK_FAILURE;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.OK;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.internal.storage.file.BatchRefUpdateTest.Result.TRANSACTION_ABORTED;
import static org.eclipse.jgit.lib.ObjectId.zeroId;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.StrictWorkMonitor;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({ "boxing", "hiding", "unused" })
@ExtendWith(CustomParameterResolver.class)
public class BatchRefUpdateTest extends LocalDiskRepositoryTestCase {
	public boolean atomic;

	public boolean useReftable;

	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { { Boolean.FALSE, Boolean.FALSE },
				{ Boolean.TRUE, Boolean.FALSE },
				{ Boolean.FALSE, Boolean.TRUE },
				{ Boolean.TRUE, Boolean.TRUE }, });
	}

	private Repository diskRepo;

	private TestRepository<Repository> repo;

	private RefDirectory refdir;

	private RevCommit A;

	private RevCommit B; // B descends from A.

	/**
	 * When asserting the number of RefsChangedEvents you must account for one
	 * additional event due to the initial ref setup via a number of calls to
	 * {@link #writeLooseRef(String, AnyObjectId)} (will be fired in execute()
	 * when it is detected that the on-disk loose refs have changed), or for one
	 * additional event per {@link #writeRef(String, AnyObjectId)}.
	 */
	private int refsChangedEvents;

	private ListenerHandle handle;

	private RefsChangedListener refsChangedListener = event -> {
		refsChangedEvents++;
	};

	@BeforeEach
	public void setUp(boolean atomic, boolean useReftable) throws Exception {
		super.setUp();
		this.atomic = atomic;
		this.useReftable = useReftable;

		FileRepository fileRepo = createBareRepository();
		if (useReftable) {
			fileRepo.convertToReftable(false, false);
		}

		diskRepo = fileRepo;
		addRepoToClose(diskRepo);
		setLogAllRefUpdates(true);

		if (!useReftable) {
			refdir = (RefDirectory) diskRepo.getRefDatabase();
			refdir.setRetrySleepMs(Arrays.asList(0, 0));
		}

		repo = new TestRepository<>(diskRepo);
		A = repo.commit().create();
		B = repo.commit(repo.getRevWalk().parseCommit(A));
		refsChangedEvents = 0;
		handle = diskRepo.getListenerList()
				.addRefsChangedListener(refsChangedListener);
	}

	@AfterEach
	public void removeListener() {
		handle.remove();
		refsChangedEvents = 0;
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void packedRefsFileIsSorted(boolean atomic, boolean useReftable)
			throws IOException {
		assumeTrue(atomic);
		assumeFalse(useReftable);

		for (int i = 0; i < 2; i++) {
			BatchRefUpdate bu = diskRepo.getRefDatabase().newBatchUpdate();
			String b1 = String.format("refs/heads/a%d", i);
			String b2 = String.format("refs/heads/b%d", i);
			bu.setAtomic(atomic);
			ReceiveCommand c1 = new ReceiveCommand(ObjectId.zeroId(), A, b1);
			ReceiveCommand c2 = new ReceiveCommand(ObjectId.zeroId(), B, b2);
			bu.addCommand(c1, c2);
			try (RevWalk rw = new RevWalk(diskRepo)) {
				bu.execute(rw, NullProgressMonitor.INSTANCE);
			}
			assertEquals(c1.getResult(), ReceiveCommand.Result.OK);
			assertEquals(c2.getResult(), ReceiveCommand.Result.OK);
		}

		File packed = new File(diskRepo.getDirectory(), "packed-refs");
		String packedStr = new String(Files.readAllBytes(packed.toPath()),
				UTF_8);

		int a2 = packedStr.indexOf("refs/heads/a1");
		int b1 = packedStr.indexOf("refs/heads/b0");
		assertTrue(a2 < b1);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void simpleNoForce(boolean atomic, boolean useReftable) throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, REJECTED_NONFASTFORWARD);
			assertRefs("refs/heads/master", A, "refs/heads/masters", B);
		} else {
			assertResults(cmds, OK, REJECTED_NONFASTFORWARD);
			assertRefs("refs/heads/master", B, "refs/heads/masters", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void simpleNoForceRefsChangedEvents(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds));

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void simpleForce(boolean atomic, boolean useReftable) throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/masters", A);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void simpleForceRefsChangedEvents(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/masters",
						UPDATE_NONFASTFORWARD));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void nonFastForwardDoesNotDoExpensiveMergeCheck(boolean atomic,
			boolean useReftable) throws IOException {
		writeLooseRef("refs/heads/master", B);

		List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(B, A,
				"refs/heads/master", UPDATE_NONFASTFORWARD));
		try (RevWalk rw = new RevWalk(diskRepo) {
			@Override
			public boolean isMergedInto(RevCommit base, RevCommit tip) {
				throw new AssertionError("isMergedInto() should not be called");
			}
		}) {
			newBatchUpdate(cmds).setAllowNonFastForwards(true).execute(rw,
					new StrictWorkMonitor());
		}

		assertResults(cmds, OK);
		assertRefs("refs/heads/master", A);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void nonFastForwardDoesNotDoExpensiveMergeCheckRefsChangedEvents(
			boolean atomic, boolean useReftable) throws IOException {
		writeLooseRef("refs/heads/master", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(new ReceiveCommand(B, A,
				"refs/heads/master", UPDATE_NONFASTFORWARD));
		try (RevWalk rw = new RevWalk(diskRepo) {
			@Override
			public boolean isMergedInto(RevCommit base, RevCommit tip) {
				throw new AssertionError("isMergedInto() should not be called");
			}
		}) {
			newBatchUpdate(cmds).setAllowNonFastForwards(true).execute(rw,
					new StrictWorkMonitor());
		}

		assertEquals(initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void fileDirectoryConflict(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x", CREATE),
				new ReceiveCommand(zeroId(), A, "refs/heads", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			// Atomic update sees that master and master/x are conflicting, then
			// marks the first one in the list as LOCK_FAILURE and aborts the
			// rest.
			assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED,
					TRANSACTION_ABORTED);
			assertRefs("refs/heads/master", A, "refs/heads/masters", B);
		} else {
			// Non-atomic updates are applied in order: master succeeds, then
			// master/x fails due to conflict.
			assertResults(cmds, OK, LOCK_FAILURE, LOCK_FAILURE);
			assertRefs("refs/heads/master", B, "refs/heads/masters", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void fileDirectoryConflictRefsChangedEvents(boolean atomic,
			boolean useReftable) throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x", CREATE),
				new ReceiveCommand(zeroId(), A, "refs/heads", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void conflictThanksToDelete(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/masters/x", CREATE),
				new ReceiveCommand(B, zeroId(), "refs/heads/masters", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/masters/x", A);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void conflictThanksToDeleteRefsChangedEvents(boolean atomic,
			boolean useReftable) throws IOException {
		writeLooseRefs("refs/heads/master", A, "refs/heads/masters", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/masters/x", CREATE),
				new ReceiveCommand(B, zeroId(), "refs/heads/masters", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 3, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void updateToMissingObject(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);

		ObjectId bad = ObjectId
				.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, bad, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			assertResults(cmds, REJECTED_MISSING_OBJECT, TRANSACTION_ABORTED);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, REJECTED_MISSING_OBJECT, OK);
			assertRefs("refs/heads/master", A, "refs/heads/foo2", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void updateToMissingObjectRefsChangedEvents(boolean atomic,
			boolean useReftable) throws IOException {
		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		ObjectId bad = ObjectId
				.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, bad, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void addMissingObject(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);

		ObjectId bad = ObjectId
				.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), bad, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, REJECTED_MISSING_OBJECT);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, OK, REJECTED_MISSING_OBJECT);
			assertRefs("refs/heads/master", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void addMissingObjectRefsChangedEvents(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		ObjectId bad = ObjectId
				.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), bad, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true), false);

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void oneNonExistentRef(boolean atomic, boolean useReftable)
			throws IOException {
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/foo1", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
			assertRefs();
			assertEquals(0, refsChangedEvents);
		} else {
			assertResults(cmds, LOCK_FAILURE, OK);
			assertRefs("refs/heads/foo2", B);
			assertEquals(1, refsChangedEvents);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void oneRefWrongOldValue(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(B, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, LOCK_FAILURE, OK);
			assertRefs("refs/heads/master", A, "refs/heads/foo2", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void oneRefWrongOldValueRefsChangedEvents(boolean atomic,
			boolean useReftable) throws IOException {
		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(B, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/foo2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void nonExistentRef(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(A, zeroId(), "refs/heads/foo2", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
			assertRefs("refs/heads/master", A);
		} else {
			assertResults(cmds, OK, LOCK_FAILURE);
			assertRefs("refs/heads/master", B);
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void nonExistentRefRefsChangedEvents(boolean atomic, boolean useReftable)
			throws IOException {
		writeLooseRef("refs/heads/master", A);

		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(A, zeroId(), "refs/heads/foo2", DELETE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertEquals(atomic ? initialRefsChangedEvents
				: initialRefsChangedEvents + 1, refsChangedEvents);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void noRefLog(boolean atomic, boolean useReftable) throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch");
		assertEquals(Collections.singleton("refs/heads/master"),
				oldLogs.keySet());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/branch", B);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogUnchanged(oldLogs, "refs/heads/branch");
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogDefaultIdent(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch2", A);
		int initialRefsChangedEvents = refsChangedEvents;

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch1", "refs/heads/branch2");
		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch1", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true)
				.setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/branch1", B,
				"refs/heads/branch2", A);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertReflogEquals(reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch1"));
		assertReflogUnchanged(oldLogs, "refs/heads/branch2");
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogAppendStatusNoMessage(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch1", B);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(B, A, "refs/heads/branch1",
						UPDATE_NONFASTFORWARD),
				new ReceiveCommand(zeroId(), A, "refs/heads/branch2", CREATE));
		execute(newBatchUpdate(cmds).setAllowNonFastForwards(true)
				.setRefLogMessage(null, true));

		assertResults(cmds, OK, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/branch1", A,
				"refs/heads/branch2", A);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 3, refsChangedEvents);
		assertReflogEquals(
				// Always forced; setAllowNonFastForwards(true) bypasses the
				// check.
				reflog(A, B, new PersonIdent(diskRepo), "forced-update"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(B, A, new PersonIdent(diskRepo), "forced-update"),
				getLastReflog("refs/heads/branch1"));
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo), "created"),
				getLastReflog("refs/heads/branch2"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogAppendStatusFastForward(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays
				.asList(new ReceiveCommand(A, B, "refs/heads/master", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage(null, true));

		assertResults(cmds, OK);
		assertRefs("refs/heads/master", B);
		assertEquals(initialRefsChangedEvents + 1, refsChangedEvents);
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo), "fast-forward"),
				getLastReflog("refs/heads/master"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogAppendStatusWithMessage(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), A, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/branch", A);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertReflogEquals(
				reflog(A, B, new PersonIdent(diskRepo),
						"a reflog: fast-forward"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo),
						"a reflog: created"),
				getLastReflog("refs/heads/branch"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogCustomIdent(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		PersonIdent ident = new PersonIdent("A Reflog User",
				"reflog@example.com");
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false)
				.setRefLogIdent(ident));

		assertResults(cmds, OK, OK);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertRefs("refs/heads/master", B, "refs/heads/branch", B);
		assertReflogEquals(reflog(A, B, ident, "a reflog"),
				getLastReflog("refs/heads/master"), true);
		assertReflogEquals(reflog(zeroId(), B, ident, "a reflog"),
				getLastReflog("refs/heads/branch"), true);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogDelete(boolean atomic, boolean useReftable) throws IOException {
		writeRef("refs/heads/master", A);
		writeRef("refs/heads/branch", A);
		int initialRefsChangedEvents = refsChangedEvents;
		assertEquals(2, getLastReflogs("refs/heads/master", "refs/heads/branch")
				.size());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, zeroId(), "refs/heads/master", DELETE),
				new ReceiveCommand(A, B, "refs/heads/branch", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/branch", B);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		if (useReftable) {
			// reftable retains reflog entries for deleted branches.
			assertReflogEquals(
					reflog(A, zeroId(), new PersonIdent(diskRepo), "a reflog"),
					getLastReflog("refs/heads/master"));
		} else {
			assertNull(getLastReflog("refs/heads/master"));
		}
		assertReflogEquals(reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogFileDirectoryConflict(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, zeroId(), "refs/heads/master", DELETE),
				new ReceiveCommand(zeroId(), A, "refs/heads/master/x", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master/x", A);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		if (!useReftable) {
			// reftable retains reflog entries for deleted branches.
			assertNull(getLastReflog("refs/heads/master"));
		}
		assertReflogEquals(
				reflog(zeroId(), A, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/master/x"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void reflogOnLockFailure(boolean atomic, boolean useReftable)
			throws IOException {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch");

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(A, B, "refs/heads/branch", UPDATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		if (atomic) {
			assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
			assertEquals(initialRefsChangedEvents, refsChangedEvents);
			assertReflogUnchanged(oldLogs, "refs/heads/master");
			assertReflogUnchanged(oldLogs, "refs/heads/branch");
		} else {
			assertResults(cmds, OK, LOCK_FAILURE);
			assertEquals(initialRefsChangedEvents + 1, refsChangedEvents);
			assertReflogEquals(
					reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
					getLastReflog("refs/heads/master"));
			assertReflogUnchanged(oldLogs, "refs/heads/branch");
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void overrideRefLogMessage(boolean atomic, boolean useReftable)
			throws Exception {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		cmds.get(0).setRefLogMessage("custom log", false);
		PersonIdent ident = new PersonIdent(diskRepo);
		execute(newBatchUpdate(cmds).setRefLogIdent(ident)
				.setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertReflogEquals(reflog(A, B, ident, "custom log"),
				getLastReflog("refs/heads/master"), true);
		assertReflogEquals(reflog(zeroId(), B, ident, "a reflog: created"),
				getLastReflog("refs/heads/branch"), true);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void overrideDisableRefLog(boolean atomic, boolean useReftable)
			throws Exception {
		writeRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch");

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		cmds.get(0).disableRefLog();
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", true));

		assertResults(cmds, OK, OK);
		assertEquals(batchesRefUpdates() ? initialRefsChangedEvents + 1
				: initialRefsChangedEvents + 2, refsChangedEvents);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo),
						"a reflog: created"),
				getLastReflog("refs/heads/branch"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void refLogNotWrittenWithoutConfigOption(boolean atomic,
			boolean useReftable) throws Exception {
		assumeFalse(useReftable);

		setLogAllRefUpdates(false);
		writeRef("refs/heads/master", A);

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch");
		assertTrue(oldLogs.isEmpty());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogUnchanged(oldLogs, "refs/heads/branch");
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void forceRefLogInUpdate(boolean atomic, boolean useReftable)
			throws Exception {
		assumeFalse(useReftable);

		setLogAllRefUpdates(false);
		writeRef("refs/heads/master", A);
		assertTrue(getLastReflogs("refs/heads/master", "refs/heads/branch")
				.isEmpty());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false)
				.setForceRefLog(true));

		assertResults(cmds, OK, OK);
		assertReflogEquals(reflog(A, B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/master"));
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void forceRefLogInCommand(boolean atomic, boolean useReftable)
			throws Exception {
		assumeFalse(useReftable);

		setLogAllRefUpdates(false);
		writeRef("refs/heads/master", A);

		Map<String, ReflogEntry> oldLogs = getLastReflogs("refs/heads/master",
				"refs/heads/branch");
		assertTrue(oldLogs.isEmpty());

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));
		cmds.get(1).setForceRefLog(true);
		execute(newBatchUpdate(cmds).setRefLogMessage("a reflog", false));

		assertResults(cmds, OK, OK);
		assertReflogUnchanged(oldLogs, "refs/heads/master");
		assertReflogEquals(
				reflog(zeroId(), B, new PersonIdent(diskRepo), "a reflog"),
				getLastReflog("refs/heads/branch"));
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void packedRefsLockFailure(boolean atomic, boolean useReftable)
			throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(getLockFile("refs/heads/master").exists());
			assertFalse(getLockFile("refs/heads/branch").exists());

			if (atomic) {
				assertResults(cmds, LOCK_FAILURE, TRANSACTION_ABORTED);
				assertRefs("refs/heads/master", A);
			} else {
				// Only operates on loose refs, doesn't care that packed-refs is
				// locked.
				assertResults(cmds, OK, OK);
				assertRefs("refs/heads/master", B, "refs/heads/branch", B);
			}
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void packedRefsLockFailureRefsChangedEvents(boolean atomic,
			boolean useReftable) throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertEquals(atomic ? initialRefsChangedEvents
					: initialRefsChangedEvents + 2, refsChangedEvents);
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void oneRefLockFailure(boolean atomic, boolean useReftable)
			throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE),
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = new LockFile(refdir.fileFor("refs/heads/master"));
		assertTrue(myLock.lock());
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(LockFile.getLockFile(refdir.packedRefsFile).exists());
			assertFalse(getLockFile("refs/heads/branch").exists());

			if (atomic) {
				assertResults(cmds, TRANSACTION_ABORTED, LOCK_FAILURE);
				assertRefs("refs/heads/master", A);
			} else {
				assertResults(cmds, OK, LOCK_FAILURE);
				assertRefs("refs/heads/branch", B, "refs/heads/master", A);
			}
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void oneRefLockFailureRefsChangedEvents(boolean atomic, boolean useReftable)
			throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE),
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = new LockFile(refdir.fileFor("refs/heads/master"));
		assertTrue(myLock.lock());
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertEquals(atomic ? initialRefsChangedEvents
					: initialRefsChangedEvents + 1, refsChangedEvents);
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void singleRefUpdateDoesNotRequirePackedRefsLock(boolean atomic,
			boolean useReftable) throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays
				.asList(new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertFalse(getLockFile("refs/heads/master").exists());
			assertResults(cmds, OK);
			assertRefs("refs/heads/master", B);
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void singleRefUpdateDoesNotRequirePackedRefsLockRefsChangedEvents(
			boolean atomic, boolean useReftable) throws Exception {
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays
				.asList(new ReceiveCommand(A, B, "refs/heads/master", UPDATE));

		LockFile myLock = refdir.lockPackedRefs();
		try {
			execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));

			assertEquals(initialRefsChangedEvents + 1, refsChangedEvents);
		} finally {
			myLock.unlock();
		}
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void atomicUpdateRespectsInProcessLock(boolean atomic, boolean useReftable)
			throws Exception {
		assumeTrue(atomic);
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		Thread t = new Thread(() -> {
			try {
				execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		ReentrantLock l = refdir.inProcessPackedRefsLock;
		l.lock();
		try {
			t.start();
			long timeoutSecs = 10;
			long startNanos = System.nanoTime();

			// Hold onto the lock until we observe the worker thread has
			// attempted to
			// acquire it.
			while (l.getQueueLength() == 0) {
				long elapsedNanos = System.nanoTime() - startNanos;
				assertTrue(NANOSECONDS.toSeconds(elapsedNanos) < timeoutSecs,
						"timed out waiting for work thread to attempt to acquire lock");
				Thread.sleep(3);
			}

			// Once we unlock, the worker thread should finish the update
			// promptly.
			l.unlock();
			t.join(SECONDS.toMillis(timeoutSecs));
			assertFalse(t.isAlive());
		} finally {
			if (l.isHeldByCurrentThread()) {
				l.unlock();
			}
		}

		assertResults(cmds, OK, OK);
		assertRefs("refs/heads/master", B, "refs/heads/branch", B);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "atomic={0}, reftable={1}")
	void atomicUpdateRespectsInProcessLockRefsChangedEvents(boolean atomic,
			boolean useReftable) throws Exception {
		assumeTrue(atomic);
		assumeFalse(useReftable);

		writeLooseRef("refs/heads/master", A);
		int initialRefsChangedEvents = refsChangedEvents;

		List<ReceiveCommand> cmds = Arrays.asList(
				new ReceiveCommand(A, B, "refs/heads/master", UPDATE),
				new ReceiveCommand(zeroId(), B, "refs/heads/branch", CREATE));

		Thread t = new Thread(() -> {
			try {
				execute(newBatchUpdate(cmds).setAllowNonFastForwards(true));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		ReentrantLock l = refdir.inProcessPackedRefsLock;
		l.lock();
		try {
			t.start();
			long timeoutSecs = 10;

			// Hold onto the lock until we observe the worker thread has
			// attempted to
			// acquire it.
			while (l.getQueueLength() == 0) {
				Thread.sleep(3);
			}

			// Once we unlock, the worker thread should finish the update
			// promptly.
			l.unlock();
			t.join(SECONDS.toMillis(timeoutSecs));
		} finally {
			if (l.isHeldByCurrentThread()) {
				l.unlock();
			}
		}

		assertEquals(initialRefsChangedEvents + 1, refsChangedEvents);
	}

	private void setLogAllRefUpdates(boolean enable) throws Exception {
		StoredConfig cfg = diskRepo.getConfig();
		cfg.load();
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, enable);
		cfg.save();
	}

	private void writeLooseRef(String name, AnyObjectId id) throws IOException {
		if (useReftable) {
			writeRef(name, id);
		} else {
			write(new File(diskRepo.getDirectory(), name), id.name() + "\n");
			// force the refs-changed event to be fired for the loose ref that
			// was created. We do this to get the events fired during the test
			// 'setup' out of the way and this allows us to now accurately
			// assert only for the new events fired during the BatchRefUpdate.
			refdir.exactRef(name);
		}
	}

	private void writeLooseRefs(String name1, AnyObjectId id1, String name2,
			AnyObjectId id2) throws IOException {
		if (useReftable) {
			BatchRefUpdate bru = diskRepo.getRefDatabase().newBatchUpdate();

			Ref r1 = diskRepo.exactRef(name1);
			ReceiveCommand c1 = new ReceiveCommand(
					r1 != null ? r1.getObjectId() : ObjectId.zeroId(),
					id1.toObjectId(), name1, r1 == null ? CREATE : UPDATE);

			Ref r2 = diskRepo.exactRef(name2);
			ReceiveCommand c2 = new ReceiveCommand(
					r2 != null ? r2.getObjectId() : ObjectId.zeroId(),
					id2.toObjectId(), name2, r2 == null ? CREATE : UPDATE);

			bru.addCommand(c1, c2);
			try (RevWalk rw = new RevWalk(diskRepo)) {
				bru.execute(rw, NullProgressMonitor.INSTANCE);
			}
			assertEquals(c2.getResult(), ReceiveCommand.Result.OK);
			assertEquals(c1.getResult(), ReceiveCommand.Result.OK);
		} else {
			writeLooseRef(name1, id1);
			writeLooseRef(name2, id2);
		}
	}

	private void writeRef(String name, AnyObjectId id) throws IOException {
		RefUpdate u = diskRepo.updateRef(name);
		u.setRefLogMessage(getClass().getSimpleName(), false);
		u.setForceUpdate(true);
		u.setNewObjectId(id);
		RefUpdate.Result r = u.update();
		switch (r) {
		case NEW:
		case FORCED:
			return;
		default:
			throw new IOException("Got " + r + " while updating " + name);
		}
	}

	private BatchRefUpdate newBatchUpdate(List<ReceiveCommand> cmds) {
		BatchRefUpdate u = diskRepo.getRefDatabase().newBatchUpdate();
		if (atomic) {
			assertTrue(u.isAtomic());
		} else {
			u.setAtomic(false);
		}
		u.addCommand(cmds);
		return u;
	}

	private void execute(BatchRefUpdate u) throws IOException {
		execute(u, false);
	}

	private void execute(BatchRefUpdate u, boolean strictWork)
			throws IOException {
		try (RevWalk rw = new RevWalk(diskRepo)) {
			u.execute(rw, strictWork ? new StrictWorkMonitor()
					: NullProgressMonitor.INSTANCE);
		}
	}

	private void assertRefs(Object... args) throws IOException {
		if (args.length % 2 != 0) {
			throw new IllegalArgumentException(
					"expected even number of args: " + Arrays.toString(args));
		}

		Map<String, AnyObjectId> expected = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i += 2) {
			expected.put((String) args[i], (AnyObjectId) args[i + 1]);
		}

		Map<String, Ref> refs = diskRepo.getRefDatabase()
				.getRefsByPrefix(RefDatabase.ALL).stream()
				.collect(Collectors.toMap(Ref::getName, Function.identity()));
		Ref actualHead = refs.remove(Constants.HEAD);
		if (actualHead != null) {
			String actualLeafName = actualHead.getLeaf().getName();
			assertEquals("refs/heads/master", actualLeafName,
					"expected HEAD to point to refs/heads/master, got: "
							+ actualLeafName);
			AnyObjectId expectedMaster = expected.get("refs/heads/master");
			assertNotNull(expectedMaster,
					"expected master ref since HEAD exists");
			assertEquals(expectedMaster, actualHead.getObjectId());
		}

		Map<String, AnyObjectId> actual = new LinkedHashMap<>();
		refs.forEach((n, r) -> actual.put(n, r.getObjectId()));

		assertEquals(expected.keySet(), actual.keySet());
		actual.forEach((n, a) -> assertEquals(expected.get(n), a, n));
	}

	enum Result {
		OK(ReceiveCommand.Result.OK), LOCK_FAILURE(
				ReceiveCommand.Result.LOCK_FAILURE), REJECTED_NONFASTFORWARD(
						ReceiveCommand.Result.REJECTED_NONFASTFORWARD), REJECTED_MISSING_OBJECT(
								ReceiveCommand.Result.REJECTED_MISSING_OBJECT), TRANSACTION_ABORTED(
										ReceiveCommand::isTransactionAborted);

		@SuppressWarnings("ImmutableEnumChecker")
		final Predicate<? super ReceiveCommand> p;

		private Result(Predicate<? super ReceiveCommand> p) {
			this.p = p;
		}

		private Result(ReceiveCommand.Result result) {
			this(c -> c.getResult() == result);
		}
	}

	private void assertResults(List<ReceiveCommand> cmds, Result... expected) {
		if (expected.length != cmds.size()) {
			throw new IllegalArgumentException(
					"expected " + cmds.size() + " result args");
		}
		for (int i = 0; i < cmds.size(); i++) {
			ReceiveCommand c = cmds.get(i);
			Result r = expected[i];
			assertTrue(r.p.test(c), String.format(
					"result of command (%d) should be %s, got %s %s%s",
					Integer.valueOf(i), r, c, c.getResult(),
					c.getMessage() != null ? " (" + c.getMessage() + ")" : ""));
		}
	}

	private Map<String, ReflogEntry> getLastReflogs(String... names)
			throws IOException {
		Map<String, ReflogEntry> result = new LinkedHashMap<>();
		for (String name : names) {
			ReflogEntry e = getLastReflog(name);
			if (e != null) {
				result.put(name, e);
			}
		}
		return result;
	}

	private ReflogEntry getLastReflog(String name) throws IOException {
		ReflogReader r = diskRepo.getReflogReader(name);
		if (r == null) {
			return null;
		}
		return r.getLastEntry();
	}

	private File getLockFile(String refName) {
		return LockFile.getLockFile(refdir.fileFor(refName));
	}

	private void assertReflogUnchanged(Map<String, ReflogEntry> old,
			String name) throws IOException {
		assertReflogEquals(old.get(name), getLastReflog(name), true);
	}

	private static void assertReflogEquals(ReflogEntry expected,
			ReflogEntry actual) {
		assertReflogEquals(expected, actual, false);
	}

	private static void assertReflogEquals(ReflogEntry expected,
			ReflogEntry actual, boolean strictTime) {
		if (expected == null) {
			assertNull(actual);
			return;
		}
		assertNotNull(actual);
		assertEquals(expected.getOldId(), actual.getOldId());
		assertEquals(expected.getNewId(), actual.getNewId());
		if (strictTime) {
			assertEquals(expected.getWho(), actual.getWho());
		} else {
			assertEquals(expected.getWho().getName(),
					actual.getWho().getName());
			assertEquals(expected.getWho().getEmailAddress(),
					actual.getWho().getEmailAddress());
		}
		assertEquals(expected.getComment(), actual.getComment());
	}

	private static ReflogEntry reflog(ObjectId oldId, ObjectId newId,
			PersonIdent who, String comment) {
		return new ReflogEntry() {
			@Override
			public ObjectId getOldId() {
				return oldId;
			}

			@Override
			public ObjectId getNewId() {
				return newId;
			}

			@Override
			public PersonIdent getWho() {
				return who;
			}

			@Override
			public String getComment() {
				return comment;
			}

			@Override
			public CheckoutEntry parseCheckout() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private boolean batchesRefUpdates() {
		return atomic || useReftable;
	}
}
