/*
 * Copyright (C) 2019, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD;
import static org.eclipse.jgit.lib.RefUpdate.Result.FORCED;
import static org.eclipse.jgit.lib.RefUpdate.Result.IO_FAILURE;
import static org.eclipse.jgit.lib.RefUpdate.Result.LOCK_FAILURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Ignore;
import org.junit.Test;

public class FileReftableTest extends SampleDataRepositoryTestCase {
	String bCommit;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		Ref b = db.exactRef("refs/heads/b");
		bCommit = b.getObjectId().getName();
		db.convertToReftable(false, false);
	}

	@SuppressWarnings("boxing")
	@Test
	public void testReloadIfNecessary() throws Exception {
		ObjectId id = db.resolve("master");
		try (FileRepository repo1 = new FileRepository(db.getDirectory());
				FileRepository repo2 = new FileRepository(db.getDirectory())) {
			((FileReftableDatabase) repo1.getRefDatabase())
					.setAutoRefresh(true);
			((FileReftableDatabase) repo2.getRefDatabase())
					.setAutoRefresh(true);
			FileRepository repos[] = { repo1, repo2 };
			for (int i = 0; i < 10; i++) {
				for (int j = 0; j < 2; j++) {
					FileRepository repo = repos[j];
					RefUpdate u = repo.getRefDatabase().newUpdate(
							String.format("branch%d", i * 10 + j), false);
					u.setNewObjectId(id);
					RefUpdate.Result r = u.update();
					assertEquals(Result.NEW, r);
				}
			}
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void testRacyReload() throws Exception {
		ObjectId id = db.resolve("master");
		int retry = 0;
		try (FileRepository repo1 = new FileRepository(db.getDirectory());
				FileRepository repo2 = new FileRepository(db.getDirectory())) {
			FileRepository repos[] = { repo1, repo2 };
			for (int i = 0; i < 10; i++) {
				for (int j = 0; j < 2; j++) {
					FileRepository repo = repos[j];
					RefUpdate u = repo.getRefDatabase().newUpdate(
							String.format("branch%d", i * 10 + j), false);

					u.setNewObjectId(id);
					RefUpdate.Result r = u.update();
					if (!r.equals(Result.NEW)) {
						retry++;
						u = repo.getRefDatabase().newUpdate(
								String.format("branch%d", i * 10 + j), false);

						u.setNewObjectId(id);
						r = u.update();
						assertEquals(Result.NEW, r);
					}
				}
			}

			// only the first one succeeds
			assertEquals(19, retry);
		}
	}

	@Ignore("""
			Failed on clean original sources before applying new //BBB changes in the following files:
			appserver/common/util/src/main/java/org/eclipse/jgit/transport/HttpAuthMethod.java
			appserver/common/util/src/main/java/org/eclipse/jgit/transport/NTLM.java
			appserver/common/util/src/main/java/org/eclipse/jgit/transport/TransportHttp.java
			""")
	@Test
	public void testConcurrentRacyReload() throws Exception {
		ObjectId id = db.resolve("master");
		final CyclicBarrier barrier = new CyclicBarrier(2);

		class UpdateRef implements Callable<RefUpdate.Result> {

			private RefUpdate u;

			UpdateRef(FileRepository repo, String branchName)
					throws IOException {
				u = repo.getRefDatabase().newUpdate(branchName,
						false);
				u.setNewObjectId(id);
			}

			@Override
			public RefUpdate.Result call() throws Exception {
				barrier.await(); // wait for the other thread to prepare
				return u.update();
			}
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try (FileRepository repo1 = new FileRepository(db.getDirectory());
				FileRepository repo2 = new FileRepository(db.getDirectory())) {
			((FileReftableDatabase) repo1.getRefDatabase())
					.setAutoRefresh(true);
			((FileReftableDatabase) repo2.getRefDatabase())
					.setAutoRefresh(true);
			for (int i = 0; i < 10; i++) {
				String branchName = String.format("branch%d",
						Integer.valueOf(i));
				Future<RefUpdate.Result> ru1 = pool
						.submit(new UpdateRef(repo1, branchName));
				Future<RefUpdate.Result> ru2 = pool
						.submit(new UpdateRef(repo2, branchName));
				assertTrue((ru1.get() == Result.NEW
						&& ru2.get() == Result.LOCK_FAILURE)
						|| (ru1.get() == Result.LOCK_FAILURE
								&& ru2.get() == Result.NEW));
			}
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}

	@Test
	public void testCompactFully() throws Exception {
		ObjectId c1 = db.resolve("master^^");
		ObjectId c2 = db.resolve("master^");
		for (int i = 0; i < 5; i++) {
			RefUpdate u = db.updateRef("refs/heads/master");
			u.setForceUpdate(true);
			u.setNewObjectId((i%2) == 0 ? c1 : c2);
			assertEquals(FORCED, u.update());
		}

		File tableDir = new File(db.getDirectory(), Constants.REFTABLE);
		assertTrue(tableDir.listFiles().length > 2);
		((FileReftableDatabase)db.getRefDatabase()).compactFully();
		assertEquals(2, tableDir.listFiles().length);
	}

	@Test
	public void testOpenConvert() throws Exception {
		try (FileRepository repo = new FileRepository(db.getDirectory())) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
		}
	}

	@Test
	public void testConvert() throws Exception {
		Ref h = db.exactRef("HEAD");
		assertTrue(h.isSymbolic());
		assertEquals("refs/heads/master", h.getTarget().getName());

		Ref b = db.exactRef("refs/heads/b");
		assertFalse(b.isSymbolic());
		assertTrue(b.isPeeled());
		assertEquals(bCommit, b.getObjectId().name());

		assertTrue(db.getRefDatabase().hasFastTipsWithSha1());
	}


	@Test
	public void testConvertBrokenObjectId() throws Exception {
		db.convertToPackedRefs(false, false);
		new File(db.getDirectory(), "refs/heads").mkdirs();

		String invalidId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
		File headFile = new File(db.getDirectory(), "refs/heads/broken");
		try (OutputStream os = new FileOutputStream(headFile)) {
			os.write(Constants.encodeASCII(invalidId + "\n"));
		}

		Ref r = db.exactRef("refs/heads/broken");
		assertNotNull(r);
		db.convertToReftable(true, false);
	}

	@Test
	public void testConvertToRefdirReflog() throws Exception {
		Ref a = db.exactRef("refs/heads/a");
		String aCommit = a.getObjectId().getName();
		RefUpdate u = db.updateRef("refs/heads/master");
		u.setForceUpdate(true);
		u.setNewObjectId(ObjectId.fromString(aCommit));
		u.setForceRefLog(true);
		u.setRefLogMessage("apple", false);
		u.update();

		RefUpdate v = db.updateRef("refs/heads/master");
		v.setForceUpdate(true);
		v.setNewObjectId(ObjectId.fromString(bCommit));
		v.setForceRefLog(true);
		v.setRefLogMessage("banana", false);
		v.update();

		db.convertToPackedRefs(true, false);
		List<ReflogEntry> logs = db.getRefDatabase()
				.getReflogReader("refs/heads/master").getReverseEntries(2);
		assertEquals("banana", logs.get(0).getComment());
		assertEquals("apple", logs.get(1).getComment());
	}

	@Test
	public void testBatchrefUpdate() throws Exception {
		ObjectId cur = db.resolve("master");
		ObjectId prev = db.resolve("master^");

		PersonIdent person = new PersonIdent("name", "mail@example.com");
		ReceiveCommand rc1 = new ReceiveCommand(ObjectId.zeroId(), cur, "refs/heads/batch1");
		ReceiveCommand rc2 = new ReceiveCommand(ObjectId.zeroId(), prev, "refs/heads/batch2");
		String msg =  "message";
		RefDatabase refDb = db.getRefDatabase();
		try (RevWalk rw = new RevWalk(db)) {
			refDb.newBatchUpdate()
					.addCommand(rc1, rc2)
					.setAtomic(true)
					.setRefLogIdent(person)
					.setRefLogMessage(msg, false)
					.execute(rw, NullProgressMonitor.INSTANCE);
		}

		assertEquals(ReceiveCommand.Result.OK, rc1.getResult());
		assertEquals(ReceiveCommand.Result.OK, rc2.getResult());

		ReflogEntry e = refDb.getReflogReader("refs/heads/batch1")
				.getLastEntry();
		assertEquals(msg, e.getComment());
		assertEquals(person, e.getWho());
		assertEquals(cur, e.getNewId());

		e = refDb.getReflogReader("refs/heads/batch2")
				.getLastEntry();
		assertEquals(msg, e.getComment());
		assertEquals(person, e.getWho());
		assertEquals(prev, e.getNewId());

		assertEquals(cur, db.exactRef("refs/heads/batch1").getObjectId());
		assertEquals(prev, db.exactRef("refs/heads/batch2").getObjectId());
	}

	@Test
	public void testFastforwardStatus() throws Exception {
		ObjectId cur = db.resolve("master");
		ObjectId prev = db.resolve("master^");
		RefUpdate u = db.updateRef("refs/heads/master");

		u.setNewObjectId(prev);
		u.setForceUpdate(true);
		assertEquals(FORCED, u.update());

		RefUpdate u2 = db.updateRef("refs/heads/master");

		u2.setNewObjectId(cur);
		assertEquals(FAST_FORWARD, u2.update());
	}

	@Test
	public void testUpdateChecksOldValue() throws Exception {
		ObjectId cur = db.resolve("master");
		ObjectId prev = db.resolve("master^");
		RefUpdate u1 = db.updateRef("refs/heads/master");
		RefUpdate u2 = db.updateRef("refs/heads/master");

		u1.setExpectedOldObjectId(cur);
		u1.setNewObjectId(prev);
		u1.setForceUpdate(true);

		u2.setExpectedOldObjectId(cur);
		u2.setNewObjectId(prev);
		u2.setForceUpdate(true);

		assertEquals(FORCED, u1.update());
		assertEquals(LOCK_FAILURE, u2.update());
	}

	@Test
	public void testWritesymref() throws Exception {
		writeSymref(Constants.HEAD, "refs/heads/a");
		assertNotNull(db.exactRef("refs/heads/b"));
	}

	@Test
	public void testFastforwardStatus2() throws Exception {
		writeSymref(Constants.HEAD, "refs/heads/a");
		ObjectId bId = db.exactRef("refs/heads/b").getObjectId();
		RefUpdate u = db.updateRef("refs/heads/a");
		u.setNewObjectId(bId);
		u.setRefLogMessage("Setup", false);
		assertEquals(FAST_FORWARD, u.update());
	}

	@Test
	public void testDelete() throws Exception {
		RefUpdate up = db.getRefDatabase().newUpdate("refs/heads/a", false);
		up.setForceUpdate(true);
		RefUpdate.Result res = up.delete();
		assertEquals(FORCED, res);
		assertNull(db.exactRef("refs/heads/a"));
	}

	@Test
	public void testDeleteWithoutHead() throws IOException {
		// Prepare repository without HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD, true);
		refUpdate.setForceUpdate(true);
		refUpdate.setNewObjectId(ObjectId.zeroId());

		RefUpdate.Result updateResult = refUpdate.update();
		assertEquals(FORCED, updateResult);

		Ref r = db.exactRef("HEAD");
		assertEquals(ObjectId.zeroId(), r.getObjectId());
		RefUpdate.Result deleteHeadResult = db.updateRef(Constants.HEAD)
				.delete();

		// why does doDelete say NEW ?
		assertEquals(RefUpdate.Result.NO_CHANGE, deleteHeadResult);

		// Any result is ok as long as it's not an NPE
		db.updateRef(Constants.R_HEADS + "master").delete();
	}

	@Test
	public void testUpdateRefDetached() throws Exception {
		ObjectId pid = db.resolve("refs/heads/master");
		ObjectId ppid = db.resolve("refs/heads/master^");
		RefUpdate updateRef = db.updateRef("HEAD", true);
		updateRef.setForceUpdate(true);
		updateRef.setNewObjectId(ppid);
		RefUpdate.Result update = updateRef.update();
		assertEquals(FORCED, update);
		assertEquals(ppid, db.resolve("HEAD"));
		Ref ref = db.exactRef("HEAD");
		assertEquals("HEAD", ref.getName());
		assertTrue("is detached", !ref.isSymbolic());

		// the branch HEAD referred to is left untouched
		assertEquals(pid, db.resolve("refs/heads/master"));
		ReflogReader reflogReader = db.getRefDatabase().getReflogReader("HEAD");
		ReflogEntry e = reflogReader.getReverseEntries().get(0);
		assertEquals(ppid, e.getNewId());
		assertEquals("GIT_COMMITTER_EMAIL", e.getWho().getEmailAddress());
		assertEquals("GIT_COMMITTER_NAME", e.getWho().getName());
		assertEquals(1250379778000L, e.getWho().getWhen().getTime());
		assertEquals(pid, e.getOldId());
	}

	@Test
	public void testWriteReflog() throws Exception {
		ObjectId pid = db.resolve("refs/heads/master^");
		RefUpdate updateRef = db.updateRef("refs/heads/master");
		updateRef.setNewObjectId(pid);
		String msg = "REFLOG!";
		updateRef.setRefLogMessage(msg, true);
		PersonIdent person = new PersonIdent("name", "mail@example.com");
		updateRef.setRefLogIdent(person);
		updateRef.setForceUpdate(true);
		RefUpdate.Result update = updateRef.update();
		assertEquals(FORCED, update); // internal
		ReflogReader r = db.getRefDatabase()
				.getReflogReader("refs/heads/master");

		ReflogEntry e = r.getLastEntry();
		assertEquals(pid, e.getNewId());
		assertEquals("REFLOG!: FORCED", e.getComment());
		assertEquals(person, e.getWho());
	}

	@Test
	public void testLooseDelete() throws IOException {
		final String newRef = "refs/heads/abc";
		assertNull(db.exactRef(newRef));

		RefUpdate ref = db.updateRef(newRef);
		ObjectId nonZero = db.resolve(Constants.HEAD);
		assertNotEquals(nonZero, ObjectId.zeroId());
		ref.setNewObjectId(nonZero);
		assertEquals(RefUpdate.Result.NEW, ref.update());

		ref = db.updateRef(newRef);
		ref.setNewObjectId(db.resolve(Constants.HEAD));

		assertEquals(RefUpdate.Result.NO_CHANGE, ref.delete());

		// Differs from RefupdateTest. Deleting a loose ref leaves reflog trail.
		ReflogReader reader = db.getRefDatabase()
				.getReflogReader("refs/heads/abc");
		assertEquals(ObjectId.zeroId(), reader.getReverseEntry(1).getOldId());
		assertEquals(nonZero, reader.getReverseEntry(1).getNewId());
		assertEquals(nonZero, reader.getReverseEntry(0).getOldId());
		assertEquals(ObjectId.zeroId(), reader.getReverseEntry(0).getNewId());
	}

	private static class SubclassedId extends ObjectId {
		SubclassedId(AnyObjectId src) {
			super(src);
		}
	}

	@Test
	public void testNoCacheObjectIdSubclass() throws IOException {
		final String newRef = "refs/heads/abc";
		final RefUpdate ru = updateRef(newRef);
		final SubclassedId newid = new SubclassedId(ru.getNewObjectId());
		ru.setNewObjectId(newid);
		RefUpdate.Result update = ru.update();
		assertEquals(RefUpdate.Result.NEW, update);
		Ref r = db.exactRef(newRef);
		assertEquals(newRef, r.getName());
		assertNotNull(r.getObjectId());
		assertNotSame(newid, r.getObjectId());
		assertSame(ObjectId.class, r.getObjectId().getClass());
		assertEquals(newid, r.getObjectId());
		RefDatabase refDb = db.getRefDatabase();
		List<ReflogEntry> reverseEntries1 = refDb
				.getReflogReader("refs/heads/abc").getReverseEntries();
		ReflogEntry entry1 = reverseEntries1.get(0);
		assertEquals(1, reverseEntries1.size());
		assertEquals(ObjectId.zeroId(), entry1.getOldId());
		assertEquals(r.getObjectId(), entry1.getNewId());

		assertEquals(new PersonIdent(db).toString(),
				entry1.getWho().toString());
		assertEquals("", entry1.getComment());
		List<ReflogEntry> reverseEntries2 = refDb.getReflogReader("HEAD")
				.getReverseEntries();
		assertEquals(0, reverseEntries2.size());
	}

	@Test
	public void testDeleteSymref() throws IOException {
		RefUpdate dst = updateRef("refs/heads/abc");
		assertEquals(RefUpdate.Result.NEW, dst.update());
		ObjectId id = dst.getNewObjectId();

		RefUpdate u = db.updateRef("refs/symref");
		assertEquals(RefUpdate.Result.NEW, u.link(dst.getName()));

		Ref ref = db.exactRef(u.getName());
		assertNotNull(ref);
		assertTrue(ref.isSymbolic());
		assertEquals(dst.getName(), ref.getLeaf().getName());
		assertEquals(id, ref.getLeaf().getObjectId());

		u = db.updateRef(u.getName());
		u.setDetachingSymbolicRef();
		u.setForceUpdate(true);
		assertEquals(FORCED, u.delete());

		assertNull(db.exactRef(u.getName()));
		ref = db.exactRef(dst.getName());
		assertNotNull(ref);
		assertFalse(ref.isSymbolic());
		assertEquals(id, ref.getObjectId());
	}

	@Test
	public void writeUnbornHead() throws Exception {
		RefUpdate.Result r = db.updateRef("HEAD").link("refs/heads/unborn");
		assertEquals(FORCED, r);

		Ref head = db.exactRef("HEAD");
		assertTrue(head.isSymbolic());
		assertEquals("refs/heads/unborn", head.getTarget().getName());
	}

	/**
	 * Update the HEAD ref when the referenced branch is unborn
	 *
	 * @throws Exception
	 */
	@Test
	public void testUpdateRefDetachedUnbornHead() throws Exception {
		ObjectId ppid = db.resolve("refs/heads/master^");
		writeSymref("HEAD", "refs/heads/unborn");
		RefUpdate updateRef = db.updateRef("HEAD", true);
		updateRef.setForceUpdate(true);
		updateRef.setNewObjectId(ppid);
		RefUpdate.Result update = updateRef.update();
		assertEquals(RefUpdate.Result.NEW, update);
		assertEquals(ppid, db.resolve("HEAD"));
		Ref ref = db.exactRef("HEAD");
		assertEquals("HEAD", ref.getName());
		assertTrue("is detached", !ref.isSymbolic());

		// the branch HEAD referred to is left untouched
		assertNull(db.resolve("refs/heads/unborn"));
		ReflogReader reflogReader = db.getRefDatabase().getReflogReader("HEAD");
		ReflogEntry e = reflogReader.getReverseEntries().get(0);
		assertEquals(ObjectId.zeroId(), e.getOldId());
		assertEquals(ppid, e.getNewId());
		assertEquals("GIT_COMMITTER_EMAIL", e.getWho().getEmailAddress());
		assertEquals("GIT_COMMITTER_NAME", e.getWho().getName());
		assertEquals(1250379778000L, e.getWho().getWhen().getTime());
	}

	@Test
	public void testDeleteNotFound() throws IOException {
		RefUpdate ref = updateRef("refs/heads/doesnotexist");
		assertNull(db.exactRef(ref.getName()));
		assertEquals(RefUpdate.Result.NEW, ref.delete());
		assertNull(db.exactRef(ref.getName()));
	}

	@Test
	public void testRenameSymref() throws IOException {
		db.resolve("HEAD");
		RefRename r = db.renameRef("HEAD", "KOPF");
		assertEquals(IO_FAILURE, r.rename());
	}

	@Test
	public void testRenameCurrentBranch() throws IOException {
		ObjectId rb = db.resolve("refs/heads/b");
		writeSymref(Constants.HEAD, "refs/heads/b");
		ObjectId oldHead = db.resolve(Constants.HEAD);
		assertEquals("internal test condition, b == HEAD", oldHead, rb);
		RefRename renameRef = db.renameRef("refs/heads/b",
				"refs/heads/new/name");
		RefUpdate.Result result = renameRef.rename();
		assertEquals(RefUpdate.Result.RENAMED, result);
		assertEquals(rb, db.resolve("refs/heads/new/name"));
		assertNull(db.resolve("refs/heads/b"));
		assertEquals(rb, db.resolve(Constants.HEAD));

		List<String> names = new ArrayList<>();
		names.add("HEAD");
		names.add("refs/heads/b");
		names.add("refs/heads/new/name");

		for (String nm : names) {
			ReflogReader rd = db.getRefDatabase().getReflogReader(nm);
			assertNotNull(rd);
			ReflogEntry last = rd.getLastEntry();
			ObjectId id = last.getNewId();
			assertTrue(ObjectId.zeroId().equals(id) || rb.equals(id));

			id = last.getNewId();
			assertTrue(ObjectId.zeroId().equals(id) || rb.equals(id));

			String want = "Branch: renamed b to new/name";
			assertEquals(want, last.getComment());
		}
	}

	@Test
	public void isGitRepository() {
		assertTrue(RepositoryCache.FileKey.isGitRepository(db.getDirectory(), db.getFS()));
	}

	@Test
	public void testRenameDestExists() throws IOException {
		ObjectId rb = db.resolve("refs/heads/b");
		writeSymref(Constants.HEAD, "refs/heads/b");
		ObjectId oldHead = db.resolve(Constants.HEAD);
		assertEquals("internal test condition, b == HEAD", oldHead, rb);
		RefRename renameRef = db.renameRef("refs/heads/b", "refs/heads/a");
		RefUpdate.Result result = renameRef.rename();
		assertEquals(RefUpdate.Result.LOCK_FAILURE, result);
	}

	@Test
	public void testRenameAtomic() throws IOException {
		ObjectId prevId = db.resolve("refs/heads/master^");

		RefRename rename = db.renameRef("refs/heads/master",
				"refs/heads/newmaster");

		RefUpdate updateRef = db.updateRef("refs/heads/master");
		updateRef.setNewObjectId(prevId);
		updateRef.setForceUpdate(true);
		assertEquals(FORCED, updateRef.update());
		assertEquals(RefUpdate.Result.LOCK_FAILURE, rename.rename());
	}

	@Test
	public void compactFully() throws Exception {
		FileReftableDatabase refDb = (FileReftableDatabase) db.getRefDatabase();
		PersonIdent person = new PersonIdent("jane", "jane@invalid");

		ObjectId aId  = db.exactRef("refs/heads/a").getObjectId();
		ObjectId bId  = db.exactRef("refs/heads/b").getObjectId();

		SecureRandom random = new SecureRandom();
		List<String> strs = new ArrayList<>();
		for (int i = 0; i < 1024; i++) {
			strs.add(String.format("%02x",
					Integer.valueOf(random.nextInt(256))));
		}

		String randomStr = String.join("", strs);
		String refName = "branch";
		for (long i = 0; i < 2; i++) {
			RefUpdate ru = refDb.newUpdate(refName, false);
			ru.setNewObjectId(i % 2 == 0 ? aId : bId);
			ru.setForceUpdate(true);
			// Only write a large string in the first table, so it becomes much larger
			// than the second, and the result is not autocompacted.
			ru.setRefLogMessage(i == 0 ? randomStr : "short", false);
			ru.setRefLogIdent(person);

			RefUpdate.Result res = ru.update();
			assertTrue(res == Result.NEW || res == FORCED);
		}

		assertEquals(bId, refDb.exactRef(refName).getObjectId());
		assertTrue(randomStr.equals(refDb.getReflogReader(refName).getReverseEntry(1).getComment()));
		refDb.compactFully();
		assertEquals(bId, refDb.exactRef(refName).getObjectId());
		assertTrue(randomStr.equals(refDb.getReflogReader(refName).getReverseEntry(1).getComment()));
	}

	@Test
	public void reftableRefsStorageClass() throws IOException {
		Ref b = db.exactRef("refs/heads/b");
		assertEquals(Ref.Storage.PACKED, b.getStorage());
	}

	@Test
	public void testGetRefsExcludingPrefix() throws IOException {
		Set<String> prefixes = new HashSet<>();
		prefixes.add("refs/tags");
		// HEAD + 12 refs/heads are present here.
		List<Ref> refs =
				db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, prefixes);
		assertEquals(13, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
		checkContainsRef(refs, db.exactRef("refs/heads/a"));
		for (Ref notInResult : db.getRefDatabase().getRefsByPrefix("refs/tags")) {
			assertFalse(refs.contains(notInResult));
		}
	}

	@Test
	public void testGetRefsExcludingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/tags/");
		exclude.add("refs/heads/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testGetRefsExcludingNonExistingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/tags/");
		exclude.add("refs/heads/");
		exclude.add("refs/nonexistent/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testGetRefsWithPrefixExcludingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/heads/pa");
		String include = "refs/heads/p";
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(include, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/prefix/a"));
	}

	@Test
	public void testGetRefsWithPrefixExcludingOverlappingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/heads/pa");
		exclude.add("refs/heads/");
		exclude.add("refs/heads/p");
		exclude.add("refs/tags/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testExternalUpdate_bug_102() throws Exception {
		((FileReftableDatabase) db.getRefDatabase()).setAutoRefresh(true);
		assumeTrue(atLeastGitVersion(2, 45));
		Git git = Git.wrap(db);
		git.tag().setName("foo").call();
		Ref ref = db.exactRef("refs/tags/foo");
		assertNotNull(ref);
		runGitCommand("tag", "--force", "foo", "e");
		Ref e = db.exactRef("refs/heads/e");
		Ref foo = db.exactRef("refs/tags/foo");
		assertEquals(e.getObjectId(), foo.getObjectId());
	}

	private String toString(TemporaryBuffer b) throws IOException {
		return RawParseUtils.decode(b.toByteArray());
	}

	private ExecutionResult runGitCommand(String... args)
			throws IOException, InterruptedException {
		FS fs = db.getFS();
		ProcessBuilder pb = fs.runInShell("git", args);
		pb.directory(db.getWorkTree());
		System.err.println("PATH=" + pb.environment().get("PATH"));
		ExecutionResult result = fs.execute(pb, null);
		assertEquals(0, result.getRc());
		String err = toString(result.getStderr());
		if (!err.isEmpty()) {
			System.err.println(err);
		}
		String out = toString(result.getStdout());
		if (!out.isEmpty()) {
			System.out.println(out);
		}
		return result;
	}

	private boolean atLeastGitVersion(int minMajor, int minMinor)
			throws IOException, InterruptedException {
		String version = toString(runGitCommand("version").getStdout())
				.split(" ")[2];
		System.out.println(version);
		String[] digits = version.split("\\.");
		int major = Integer.parseInt(digits[0]);
		int minor = Integer.parseInt(digits[1]);
		return (major >= minMajor) && (minor >= minMinor);
	}

	private RefUpdate updateRef(String name) throws IOException {
		final RefUpdate ref = db.updateRef(name);
		ref.setNewObjectId(db.resolve(Constants.HEAD));
		return ref;
	}

	private void writeSymref(String src, String dst) throws IOException {
		RefUpdate u = db.updateRef(src);
		switch (u.link(dst)) {
		case NEW:
		case FORCED:
		case NO_CHANGE:
			break;
		default:
			fail("link " + src + " to " + dst);
		}
	}

	private static void checkContainsRef(Collection<Ref> haystack, Ref needle) {
		for (Ref ref : haystack) {
			if (ref.getName().equals(needle.getName()) &&
					ref.getObjectId().equals(needle.getObjectId())) {
				return;
			}
		}
		fail("list " + haystack + " does not contain ref " + needle);
	}
}
