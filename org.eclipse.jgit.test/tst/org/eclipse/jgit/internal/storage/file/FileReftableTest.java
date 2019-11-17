/*
 * Copyright (C) 2019, Google Inc.
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
		Ref b = db.exactRef("refs/heads/b");
		bCommit = b.getObjectId().getName();
		db.convertToReftable(false, false);
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
						assertEquals(r, Result.NEW);
					}
				}
			}

			// only the first one succeeds
			assertEquals(retry, 19);
		}
	}

	@Test
	public void additionalRefsAreRemoved() {
	 	assertFalse(new File(db.getDirectory(), Constants.HEAD).exists());
	}

	@Test
	public void testCompactFully() throws Exception {
		ObjectId c1 = db.resolve("master^^");
		ObjectId c2 = db.resolve("master^");
		for (int i = 0; i < 5; i++) {
			RefUpdate u = db.updateRef("refs/heads/master");
			u.setForceUpdate(true);
			u.setNewObjectId((i%2) == 0 ? c1 : c2);
			assertEquals(u.update(), FORCED);
		}

		File tableDir = new File(db.getDirectory(), Constants.REFTABLE);
		assertTrue(tableDir.listFiles().length > 1);
		((FileReftableDatabase)db.getRefDatabase()).compactFully();
		assertEquals(tableDir.listFiles().length,1);
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
	}

	@Test
	public void testConvertToRefdir() throws Exception {
		db.convertToPackedRefs(false);
		assertTrue(db.getRefDatabase() instanceof RefDirectory);
		Ref h = db.exactRef("HEAD");
		assertTrue(h.isSymbolic());
		assertEquals("refs/heads/master", h.getTarget().getName());

		Ref b = db.exactRef("refs/heads/b");
		assertFalse(b.isSymbolic());
		assertTrue(b.isPeeled());
		assertEquals(bCommit, b.getObjectId().name());
	}

	@Test
	public void testBatchrefUpdate() throws Exception {
		ObjectId cur = db.resolve("master");
		ObjectId prev = db.resolve("master^");

		PersonIdent person = new PersonIdent("name", "mail@example.com");
		ReceiveCommand rc1 = new ReceiveCommand(ObjectId.zeroId(), cur, "refs/heads/batch1");
		ReceiveCommand rc2 = new ReceiveCommand(ObjectId.zeroId(), prev, "refs/heads/batch2");
		String msg =  "message";
		try (RevWalk rw = new RevWalk(db)) {
			db.getRefDatabase().newBatchUpdate()
					.addCommand(rc1, rc2)
					.setAtomic(true)
					.setRefLogIdent(person)
					.setRefLogMessage(msg, false)
					.execute(rw, NullProgressMonitor.INSTANCE);
		}

		assertEquals(rc1.getResult(), ReceiveCommand.Result.OK);
		assertEquals(rc2.getResult(), ReceiveCommand.Result.OK);

		ReflogEntry e = db.getReflogReader("refs/heads/batch1").getLastEntry();
		assertEquals(msg, e.getComment());
		assertEquals(person, e.getWho());
		assertEquals(cur, e.getNewId());

		e = db.getReflogReader("refs/heads/batch2").getLastEntry();
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
		assertEquals(res, FORCED);
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
		ReflogReader reflogReader = db.getReflogReader("HEAD");
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
		ReflogReader r = db.getReflogReader("refs/heads/master");

		ReflogEntry e = r.getLastEntry();
		assertEquals(e.getNewId(), pid);
		assertEquals(e.getComment(), "REFLOG!: FORCED");
		assertEquals(e.getWho(), person);
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

		assertEquals(ref.delete(), RefUpdate.Result.NO_CHANGE);

		// Differs from RefupdateTest. Deleting a loose ref leaves reflog trail.
		ReflogReader reader = db.getReflogReader("refs/heads/abc");
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
		List<ReflogEntry> reverseEntries1 = db.getReflogReader("refs/heads/abc")
				.getReverseEntries();
		ReflogEntry entry1 = reverseEntries1.get(0);
		assertEquals(1, reverseEntries1.size());
		assertEquals(ObjectId.zeroId(), entry1.getOldId());
		assertEquals(r.getObjectId(), entry1.getNewId());

		assertEquals(new PersonIdent(db).toString(),
				entry1.getWho().toString());
		assertEquals("", entry1.getComment());
		List<ReflogEntry> reverseEntries2 = db.getReflogReader("HEAD")
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
		assertEquals(head.getTarget().getName(), "refs/heads/unborn");
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
		ReflogReader reflogReader = db.getReflogReader("HEAD");
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
			ReflogReader rd = db.getReflogReader(nm);
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
	public void reftableRefsStorageClass() throws IOException {
		Ref b = db.exactRef("refs/heads/b");
		assertEquals(Ref.Storage.PACKED, b.getStorage());
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
}
