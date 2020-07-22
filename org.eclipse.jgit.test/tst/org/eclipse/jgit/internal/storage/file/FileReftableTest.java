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

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

public class FileReftableTest extends SampleDataRepositoryTestCase {
	String bCommit;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		Ref b = repository.exactRef("refs/heads/b");
		bCommit = b.getObjectId().getName();
		repository.convertToReftable(false, false);
	}

	@SuppressWarnings("boxing")
	@Test
	public void testRacyReload() throws Exception {
		ObjectId id = repository.resolve("master");
		int retry = 0;
		try (FileRepository repo1 = new FileRepository(repository.getDirectory());
				FileRepository repo2 = new FileRepository(repository.getDirectory())) {
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
						assertEquals(r, Result.NEW);
					}
				}
			}

			// only the first one succeeds
			assertEquals(retry, 19);
		}
	}

	@Test
	public void testCompactFully() throws Exception {
		ObjectId c1 = repository.resolve("master^^");
		ObjectId c2 = repository.resolve("master^");
		for (int i = 0; i < 5; i++) {
			RefUpdate u = repository.updateRef("refs/heads/master");
			u.setForceUpdate(true);
			u.setNewObjectId((i%2) == 0 ? c1 : c2);
			assertEquals(u.update(), FORCED);
		}

		File tableDir = new File(repository.getDirectory(), Constants.REFTABLE);
		assertTrue(tableDir.listFiles().length > 2);
		((FileReftableDatabase)repository.getRefDatabase()).compactFully();
		assertEquals(tableDir.listFiles().length,2);
	}

	@Test
	public void testOpenConvert() throws Exception {
		try (FileRepository repo = new FileRepository(repository.getDirectory())) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
		}
	}

	@Test
	public void testConvert() throws Exception {
		Ref h = repository.exactRef("HEAD");
		assertTrue(h.isSymbolic());
		assertEquals("refs/heads/master", h.getTarget().getName());

		Ref b = repository.exactRef("refs/heads/b");
		assertFalse(b.isSymbolic());
		assertTrue(b.isPeeled());
		assertEquals(bCommit, b.getObjectId().name());

		assertTrue(repository.getRefDatabase().hasFastTipsWithSha1());
	}

	@Test
	public void testConvertToRefdir() throws Exception {
		repository.convertToPackedRefs(false, false);
		assertTrue(repository.getRefDatabase() instanceof RefDirectory);
		Ref h = repository.exactRef("HEAD");
		assertTrue(h.isSymbolic());
		assertEquals("refs/heads/master", h.getTarget().getName());

		Ref b = repository.exactRef("refs/heads/b");
		assertFalse(b.isSymbolic());
		assertTrue(b.isPeeled());
		assertEquals(bCommit, b.getObjectId().name());

		assertFalse(repository.getRefDatabase().hasFastTipsWithSha1());
	}

	@Test
	public void testConvertToRefdirReflog() throws Exception {
		Ref a = repository.exactRef("refs/heads/a");
		String aCommit = a.getObjectId().getName();
		RefUpdate u = repository.updateRef("refs/heads/master");
		u.setForceUpdate(true);
		u.setNewObjectId(ObjectId.fromString(aCommit));
		u.setForceRefLog(true);
		u.setRefLogMessage("apple", false);
		u.update();

		RefUpdate v = repository.updateRef("refs/heads/master");
		v.setForceUpdate(true);
		v.setNewObjectId(ObjectId.fromString(bCommit));
		v.setForceRefLog(true);
		v.setRefLogMessage("banana", false);
		v.update();

		repository.convertToPackedRefs(true, false);
		List<ReflogEntry> logs = repository.getReflogReader("refs/heads/master").getReverseEntries(2);
		assertEquals(logs.get(0).getComment(), "banana");
		assertEquals(logs.get(1).getComment(), "apple");
	}

	@Test
	public void testBatchrefUpdate() throws Exception {
		ObjectId cur = repository.resolve("master");
		ObjectId prev = repository.resolve("master^");

		PersonIdent person = new PersonIdent("name", "mail@example.com");
		ReceiveCommand rc1 = new ReceiveCommand(ObjectId.zeroId(), cur, "refs/heads/batch1");
		ReceiveCommand rc2 = new ReceiveCommand(ObjectId.zeroId(), prev, "refs/heads/batch2");
		String msg =  "message";
		try (RevWalk rw = new RevWalk(repository)) {
			repository.getRefDatabase().newBatchUpdate()
					.addCommand(rc1, rc2)
					.setAtomic(true)
					.setRefLogIdent(person)
					.setRefLogMessage(msg, false)
					.execute(rw, NullProgressMonitor.INSTANCE);
		}

		assertEquals(rc1.getResult(), ReceiveCommand.Result.OK);
		assertEquals(rc2.getResult(), ReceiveCommand.Result.OK);

		ReflogEntry e = repository.getReflogReader("refs/heads/batch1").getLastEntry();
		assertEquals(msg, e.getComment());
		assertEquals(person, e.getWho());
		assertEquals(cur, e.getNewId());

		e = repository.getReflogReader("refs/heads/batch2").getLastEntry();
		assertEquals(msg, e.getComment());
		assertEquals(person, e.getWho());
		assertEquals(prev, e.getNewId());

		assertEquals(cur, repository.exactRef("refs/heads/batch1").getObjectId());
		assertEquals(prev, repository.exactRef("refs/heads/batch2").getObjectId());
	}

	@Test
	public void testFastforwardStatus() throws Exception {
		ObjectId cur = repository.resolve("master");
		ObjectId prev = repository.resolve("master^");
		RefUpdate u = repository.updateRef("refs/heads/master");

		u.setNewObjectId(prev);
		u.setForceUpdate(true);
		assertEquals(FORCED, u.update());

		RefUpdate u2 = repository.updateRef("refs/heads/master");

		u2.setNewObjectId(cur);
		assertEquals(FAST_FORWARD, u2.update());
	}

	@Test
	public void testUpdateChecksOldValue() throws Exception {
		ObjectId cur = repository.resolve("master");
		ObjectId prev = repository.resolve("master^");
		RefUpdate u1 = repository.updateRef("refs/heads/master");
		RefUpdate u2 = repository.updateRef("refs/heads/master");

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
		assertNotNull(repository.exactRef("refs/heads/b"));
	}

	@Test
	public void testFastforwardStatus2() throws Exception {
		writeSymref(Constants.HEAD, "refs/heads/a");
		ObjectId bId = repository.exactRef("refs/heads/b").getObjectId();
		RefUpdate u = repository.updateRef("refs/heads/a");
		u.setNewObjectId(bId);
		u.setRefLogMessage("Setup", false);
		assertEquals(FAST_FORWARD, u.update());
	}

	@Test
	public void testDelete() throws Exception {
		RefUpdate up = repository.getRefDatabase().newUpdate("refs/heads/a", false);
		up.setForceUpdate(true);
		RefUpdate.Result res = up.delete();
		assertEquals(res, FORCED);
		assertNull(repository.exactRef("refs/heads/a"));
	}

	@Test
	public void testDeleteWithoutHead() throws IOException {
		// Prepare repository without HEAD
		RefUpdate refUpdate = repository.updateRef(Constants.HEAD, true);
		refUpdate.setForceUpdate(true);
		refUpdate.setNewObjectId(ObjectId.zeroId());

		RefUpdate.Result updateResult = refUpdate.update();
		assertEquals(FORCED, updateResult);

		Ref r = repository.exactRef("HEAD");
		assertEquals(ObjectId.zeroId(), r.getObjectId());
		RefUpdate.Result deleteHeadResult = repository.updateRef(Constants.HEAD)
				.delete();

		// why does doDelete say NEW ?
		assertEquals(RefUpdate.Result.NO_CHANGE, deleteHeadResult);

		// Any result is ok as long as it's not an NPE
		repository.updateRef(Constants.R_HEADS + "master").delete();
	}

	@Test
	public void testUpdateRefDetached() throws Exception {
		ObjectId pid = repository.resolve("refs/heads/master");
		ObjectId ppid = repository.resolve("refs/heads/master^");
		RefUpdate updateRef = repository.updateRef("HEAD", true);
		updateRef.setForceUpdate(true);
		updateRef.setNewObjectId(ppid);
		RefUpdate.Result update = updateRef.update();
		assertEquals(FORCED, update);
		assertEquals(ppid, repository.resolve("HEAD"));
		Ref ref = repository.exactRef("HEAD");
		assertEquals("HEAD", ref.getName());
		assertTrue("is detached", !ref.isSymbolic());

		// the branch HEAD referred to is left untouched
		assertEquals(pid, repository.resolve("refs/heads/master"));
		ReflogReader reflogReader = repository.getReflogReader("HEAD");
		ReflogEntry e = reflogReader.getReverseEntries().get(0);
		assertEquals(ppid, e.getNewId());
		assertEquals("GIT_COMMITTER_EMAIL", e.getWho().getEmailAddress());
		assertEquals("GIT_COMMITTER_NAME", e.getWho().getName());
		assertEquals(1250379778000L, e.getWho().getWhen().getTime());
		assertEquals(pid, e.getOldId());
	}

	@Test
	public void testWriteReflog() throws Exception {
		ObjectId pid = repository.resolve("refs/heads/master^");
		RefUpdate updateRef = repository.updateRef("refs/heads/master");
		updateRef.setNewObjectId(pid);
		String msg = "REFLOG!";
		updateRef.setRefLogMessage(msg, true);
		PersonIdent person = new PersonIdent("name", "mail@example.com");
		updateRef.setRefLogIdent(person);
		updateRef.setForceUpdate(true);
		RefUpdate.Result update = updateRef.update();
		assertEquals(FORCED, update); // internal
		ReflogReader r = repository.getReflogReader("refs/heads/master");

		ReflogEntry e = r.getLastEntry();
		assertEquals(e.getNewId(), pid);
		assertEquals(e.getComment(), "REFLOG!: FORCED");
		assertEquals(e.getWho(), person);
	}

	@Test
	public void testLooseDelete() throws IOException {
		final String newRef = "refs/heads/abc";
		assertNull(repository.exactRef(newRef));

		RefUpdate ref = repository.updateRef(newRef);
		ObjectId nonZero = repository.resolve(Constants.HEAD);
		assertNotEquals(nonZero, ObjectId.zeroId());
		ref.setNewObjectId(nonZero);
		assertEquals(RefUpdate.Result.NEW, ref.update());

		ref = repository.updateRef(newRef);
		ref.setNewObjectId(repository.resolve(Constants.HEAD));

		assertEquals(ref.delete(), RefUpdate.Result.NO_CHANGE);

		// Differs from RefupdateTest. Deleting a loose ref leaves reflog trail.
		ReflogReader reader = repository.getReflogReader("refs/heads/abc");
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
		Ref r = repository.exactRef(newRef);
		assertEquals(newRef, r.getName());
		assertNotNull(r.getObjectId());
		assertNotSame(newid, r.getObjectId());
		assertSame(ObjectId.class, r.getObjectId().getClass());
		assertEquals(newid, r.getObjectId());
		List<ReflogEntry> reverseEntries1 = repository.getReflogReader("refs/heads/abc")
				.getReverseEntries();
		ReflogEntry entry1 = reverseEntries1.get(0);
		assertEquals(1, reverseEntries1.size());
		assertEquals(ObjectId.zeroId(), entry1.getOldId());
		assertEquals(r.getObjectId(), entry1.getNewId());

		assertEquals(new PersonIdent(repository).toString(),
				entry1.getWho().toString());
		assertEquals("", entry1.getComment());
		List<ReflogEntry> reverseEntries2 = repository.getReflogReader("HEAD")
				.getReverseEntries();
		assertEquals(0, reverseEntries2.size());
	}

	@Test
	public void testDeleteSymref() throws IOException {
		RefUpdate dst = updateRef("refs/heads/abc");
		assertEquals(RefUpdate.Result.NEW, dst.update());
		ObjectId id = dst.getNewObjectId();

		RefUpdate u = repository.updateRef("refs/symref");
		assertEquals(RefUpdate.Result.NEW, u.link(dst.getName()));

		Ref ref = repository.exactRef(u.getName());
		assertNotNull(ref);
		assertTrue(ref.isSymbolic());
		assertEquals(dst.getName(), ref.getLeaf().getName());
		assertEquals(id, ref.getLeaf().getObjectId());

		u = repository.updateRef(u.getName());
		u.setDetachingSymbolicRef();
		u.setForceUpdate(true);
		assertEquals(FORCED, u.delete());

		assertNull(repository.exactRef(u.getName()));
		ref = repository.exactRef(dst.getName());
		assertNotNull(ref);
		assertFalse(ref.isSymbolic());
		assertEquals(id, ref.getObjectId());
	}

	@Test
	public void writeUnbornHead() throws Exception {
		RefUpdate.Result r = repository.updateRef("HEAD").link("refs/heads/unborn");
		assertEquals(FORCED, r);

		Ref head = repository.exactRef("HEAD");
		assertTrue(head.isSymbolic());
		assertEquals(head.getTarget().getName(), "refs/heads/unborn");
	}

	/**
	 * Update the HEAD ref when the referenced branch is unborn
	 *
	 * @throws Exception
	 */
	@Test
	public void testUpdateRefDetachedUnbornHead() throws Exception {
		ObjectId ppid = repository.resolve("refs/heads/master^");
		writeSymref("HEAD", "refs/heads/unborn");
		RefUpdate updateRef = repository.updateRef("HEAD", true);
		updateRef.setForceUpdate(true);
		updateRef.setNewObjectId(ppid);
		RefUpdate.Result update = updateRef.update();
		assertEquals(RefUpdate.Result.NEW, update);
		assertEquals(ppid, repository.resolve("HEAD"));
		Ref ref = repository.exactRef("HEAD");
		assertEquals("HEAD", ref.getName());
		assertTrue("is detached", !ref.isSymbolic());

		// the branch HEAD referred to is left untouched
		assertNull(repository.resolve("refs/heads/unborn"));
		ReflogReader reflogReader = repository.getReflogReader("HEAD");
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
		assertNull(repository.exactRef(ref.getName()));
		assertEquals(RefUpdate.Result.NEW, ref.delete());
		assertNull(repository.exactRef(ref.getName()));
	}

	@Test
	public void testRenameSymref() throws IOException {
		repository.resolve("HEAD");
		RefRename r = repository.renameRef("HEAD", "KOPF");
		assertEquals(IO_FAILURE, r.rename());
	}

	@Test
	public void testRenameCurrentBranch() throws IOException {
		ObjectId rb = repository.resolve("refs/heads/b");
		writeSymref(Constants.HEAD, "refs/heads/b");
		ObjectId oldHead = repository.resolve(Constants.HEAD);
		assertEquals("internal test condition, b == HEAD", oldHead, rb);
		RefRename renameRef = repository.renameRef("refs/heads/b",
				"refs/heads/new/name");
		RefUpdate.Result result = renameRef.rename();
		assertEquals(RefUpdate.Result.RENAMED, result);
		assertEquals(rb, repository.resolve("refs/heads/new/name"));
		assertNull(repository.resolve("refs/heads/b"));
		assertEquals(rb, repository.resolve(Constants.HEAD));

		List<String> names = new ArrayList<>();
		names.add("HEAD");
		names.add("refs/heads/b");
		names.add("refs/heads/new/name");

		for (String nm : names) {
			ReflogReader rd = repository.getReflogReader(nm);
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
		assertTrue(RepositoryCache.FileKey.isGitRepository(repository.getDirectory(), repository.getFS()));
	}

	@Test
	public void testRenameDestExists() throws IOException {
		ObjectId rb = repository.resolve("refs/heads/b");
		writeSymref(Constants.HEAD, "refs/heads/b");
		ObjectId oldHead = repository.resolve(Constants.HEAD);
		assertEquals("internal test condition, b == HEAD", oldHead, rb);
		RefRename renameRef = repository.renameRef("refs/heads/b", "refs/heads/a");
		RefUpdate.Result result = renameRef.rename();
		assertEquals(RefUpdate.Result.LOCK_FAILURE, result);
	}

	@Test
	public void testRenameAtomic() throws IOException {
		ObjectId prevId = repository.resolve("refs/heads/master^");

		RefRename rename = repository.renameRef("refs/heads/master",
				"refs/heads/newmaster");

		RefUpdate updateRef = repository.updateRef("refs/heads/master");
		updateRef.setNewObjectId(prevId);
		updateRef.setForceUpdate(true);
		assertEquals(FORCED, updateRef.update());
		assertEquals(RefUpdate.Result.LOCK_FAILURE, rename.rename());
	}

	@Test
	public void compactFully() throws Exception {
		FileReftableDatabase refDb = (FileReftableDatabase) repository.getRefDatabase();
		PersonIdent person = new PersonIdent("jane", "jane@invalid");

		ObjectId aId  = repository.exactRef("refs/heads/a").getObjectId();
		ObjectId bId  = repository.exactRef("refs/heads/b").getObjectId();

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

		assertEquals(refDb.exactRef(refName).getObjectId(), bId);
		assertTrue(randomStr.equals(refDb.getReflogReader(refName).getReverseEntry(1).getComment()));
		refDb.compactFully();
		assertEquals(refDb.exactRef(refName).getObjectId(), bId);
		assertTrue(randomStr.equals(refDb.getReflogReader(refName).getReverseEntry(1).getComment()));
	}

	@Test
	public void reftableRefsStorageClass() throws IOException {
		Ref b = repository.exactRef("refs/heads/b");
		assertEquals(Ref.Storage.PACKED, b.getStorage());
	}

	private RefUpdate updateRef(String name) throws IOException {
		final RefUpdate ref = repository.updateRef(name);
		ref.setNewObjectId(repository.resolve(Constants.HEAD));
		return ref;
	}

	private void writeSymref(String src, String dst) throws IOException {
		RefUpdate u = repository.updateRef(src);
		switch (u.link(dst)) {
		case NEW:
		case FORCED:
		case NO_CHANGE:
			break;
		default:
			fail("link " + src + " to " + dst);
		}
	}
}
