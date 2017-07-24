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

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter.Stats;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Test;

public class ReftableTest {
	private static final String MASTER = "refs/heads/master";
	private static final String NEXT = "refs/heads/next";
	private static final String V1_0 = "refs/tags/v1.0";

	private Stats stats;

	@Test
	public void emptyTable() throws IOException {
		byte[] table = write();
		assertEquals(92 /* header, footer */, table.length);
		assertEquals('R', table[0]);
		assertEquals('E', table[1]);
		assertEquals('F', table[2]);
		assertEquals('T', table[3]);
		assertEquals(0x01, table[4]);
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 0, 8));
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 24, 92));

		Reftable t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(R_HEADS)) {
			assertFalse(rc.next());
		}
		try (LogCursor rc = t.allLogs()) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void emptyVirtualTableFromRefs() throws IOException {
		Reftable t = Reftable.from(Collections.emptyList());
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (LogCursor rc = t.allLogs()) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void estimateCurrentBytesOneRef() throws IOException {
		Ref exp = ref(MASTER, 1);
		int expBytes = 24 + 4 + 5 + 3 + MASTER.length() + 20 + 68;

		byte[] table;
		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		ReftableWriter writer = new ReftableWriter().setConfig(cfg);
		try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
			writer.begin(buf);
			assertEquals(92, writer.estimateTotalBytes());
			writer.writeRef(exp);
			assertEquals(expBytes, writer.estimateTotalBytes());
			writer.finish();
			table = buf.toByteArray();
		}
		assertEquals(expBytes, table.length);
	}

	@SuppressWarnings("boxing")
	@Test
	public void estimateCurrentBytesWithIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			refs.add(ref(String.format("refs/heads/%04d", i), i));
		}

		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		cfg.setMaxIndexLevels(1);

		int expBytes = 139654;
		byte[] table;
		ReftableWriter writer = new ReftableWriter().setConfig(cfg);
		try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
			writer.begin(buf);
			writer.sortAndWriteRefs(refs);
			assertEquals(expBytes, writer.estimateTotalBytes());
			writer.finish();
			stats = writer.getStats();
			table = buf.toByteArray();
		}
		assertEquals(1, stats.refIndexLevels());
		assertEquals(expBytes, table.length);
	}

	@Test
	public void oneIdRef() throws IOException {
		Ref exp = ref(MASTER, 1);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 3 + MASTER.length() + 20 + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(PACKED, act.getStorage());
			assertTrue(act.isPeeled());
			assertFalse(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertEquals(exp.getObjectId(), act.getObjectId());
			assertNull(act.getPeeledObjectId());
			assertFalse(rc.wasDeleted());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(MASTER)) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(exp.getName(), act.getName());
			assertFalse(rc.next());
		}
	}

	@Test
	public void oneTagRef() throws IOException {
		Ref exp = tag(V1_0, 1, 2);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 2 + V1_0.length() + 40 + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(PACKED, act.getStorage());
			assertTrue(act.isPeeled());
			assertFalse(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertEquals(exp.getObjectId(), act.getObjectId());
			assertEquals(exp.getPeeledObjectId(), act.getPeeledObjectId());
		}
	}

	@Test
	public void oneSymbolicRef() throws IOException {
		Ref exp = sym(HEAD, MASTER);
		byte[] table = write(exp);
		assertEquals(
				24 + 4 + 5 + 2 + HEAD.length() + 1 + MASTER.length() + 68,
				table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertTrue(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertNotNull(act.getLeaf());
			assertEquals(MASTER, act.getTarget().getName());
			assertNull(act.getObjectId());
		}
	}

	@Test
	public void resolveSymbolicRef() throws IOException {
		Reftable t = read(write(
				sym(HEAD, "refs/heads/tmp"),
				sym("refs/heads/tmp", MASTER),
				ref(MASTER, 1)));

		Ref head = t.exactRef(HEAD);
		assertNull(head.getObjectId());
		assertEquals("refs/heads/tmp", head.getTarget().getName());

		head = t.resolve(head);
		assertNotNull(head);
		assertEquals(id(1), head.getObjectId());

		Ref master = t.exactRef(MASTER);
		assertNotNull(master);
		assertSame(master, t.resolve(master));
	}

	@Test
	public void failDeepChainOfSymbolicRef() throws IOException {
		Reftable t = read(write(
				sym(HEAD, "refs/heads/1"),
				sym("refs/heads/1", "refs/heads/2"),
				sym("refs/heads/2", "refs/heads/3"),
				sym("refs/heads/3", "refs/heads/4"),
				sym("refs/heads/4", "refs/heads/5"),
				sym("refs/heads/5", MASTER),
				ref(MASTER, 1)));

		Ref head = t.exactRef(HEAD);
		assertNull(head.getObjectId());
		assertNull(t.resolve(head));
	}

	@Test
	public void oneDeletedRef() throws IOException {
		String name = "refs/heads/gone";
		Ref exp = newRef(name);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 2 + name.length() + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}

		t.setIncludeDeletes(true);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertFalse(act.isSymbolic());
			assertEquals(name, act.getName());
			assertEquals(NEW, act.getStorage());
			assertNull(act.getObjectId());
			assertTrue(rc.wasDeleted());
		}
	}

	@Test
	public void seekNotFound() throws IOException {
		Ref exp = ref(MASTER, 1);
		ReftableReader t = read(write(exp));
		try (RefCursor rc = t.seekRef("refs/heads/a")) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef("refs/heads/n")) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void namespaceNotFound() throws IOException {
		Ref exp = ref(MASTER, 1);
		ReftableReader t = read(write(exp));
		try (RefCursor rc = t.seekRef("refs/changes/")) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef("refs/tags/")) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void namespaceHeads() throws IOException {
		Ref master = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref v1 = tag(V1_0, 3, 4);

		ReftableReader t = read(write(master, next, v1));
		try (RefCursor rc = t.seekRef("refs/tags/")) {
			assertTrue(rc.next());
			assertEquals(V1_0, rc.getRef().getName());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef("refs/heads/")) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());

			assertFalse(rc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void indexScan() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			refs.add(ref(String.format("refs/heads/%04d", i), i));
		}

		byte[] table = write(refs);
		assertTrue(stats.refIndexLevels() > 0);
		assertTrue(stats.refIndexSize() > 0);
		assertScan(refs, read(table));
	}

	@SuppressWarnings("boxing")
	@Test
	public void indexSeek() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			refs.add(ref(String.format("refs/heads/%04d", i), i));
		}

		byte[] table = write(refs);
		assertTrue(stats.refIndexLevels() > 0);
		assertTrue(stats.refIndexSize() > 0);
		assertSeek(refs, read(table));
	}

	@SuppressWarnings("boxing")
	@Test
	public void noIndexScan() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			refs.add(ref(String.format("refs/heads/%03d", i), i));
		}

		byte[] table = write(refs);
		assertEquals(0, stats.refIndexLevels());
		assertEquals(0, stats.refIndexSize());
		assertEquals(table.length, stats.totalBytes());
		assertScan(refs, read(table));
	}

	@SuppressWarnings("boxing")
	@Test
	public void noIndexSeek() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			refs.add(ref(String.format("refs/heads/%03d", i), i));
		}

		byte[] table = write(refs);
		assertEquals(0, stats.refIndexLevels());
		assertSeek(refs, read(table));
	}

	@Test
	public void withReflog() throws IOException {
		Ref master = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter()
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin(buffer);

		writer.writeRef(master);
		writer.writeRef(next);

		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(2), msg);

		writer.finish();
		byte[] table = buffer.toByteArray();
		assertEquals(245, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(id(1), rc.getRef().getObjectId());

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertFalse(rc.next());
		}
		try (LogCursor lc = t.allLogs()) {
			assertTrue(lc.next());
			assertEquals(MASTER, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(1), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertTrue(lc.next());
			assertEquals(NEXT, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(2), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertFalse(lc.next());
		}
	}

	@Test
	public void onlyReflog() throws IOException {
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter()
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin(buffer);
		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(2), msg);
		writer.finish();
		byte[] table = buffer.toByteArray();
		stats = writer.getStats();
		assertEquals(170, table.length);
		assertEquals(0, stats.refCount());
		assertEquals(0, stats.refBytes());
		assertEquals(0, stats.refIndexLevels());

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef("refs/heads/")) {
			assertFalse(rc.next());
		}
		try (LogCursor lc = t.allLogs()) {
			assertTrue(lc.next());
			assertEquals(MASTER, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(1), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertTrue(lc.next());
			assertEquals(NEXT, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(2), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertFalse(lc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void logScan() throws IOException {
		ReftableConfig cfg = new ReftableConfig();
		cfg.setRefBlockSize(256);
		cfg.setLogBlockSize(2048);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(cfg);
		writer.setMinUpdateIndex(1).setMaxUpdateIndex(1).begin(buffer);

		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			Ref ref = ref(String.format("refs/heads/%03d", i), i);
			refs.add(ref);
			writer.writeRef(ref);
		}

		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		for (Ref ref : refs) {
			writer.writeLog(ref.getName(), 1, who,
					ObjectId.zeroId(), ref.getObjectId(),
					"create " + ref.getName());
		}
		writer.finish();
		stats = writer.getStats();
		assertTrue(stats.logBytes() > 4096);
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		try (LogCursor lc = t.allLogs()) {
			for (Ref exp : refs) {
				assertTrue("has " + exp.getName(), lc.next());
				assertEquals(exp.getName(), lc.getRefName());
				ReflogEntry entry = lc.getReflogEntry();
				assertNotNull(entry);
				assertEquals(who, entry.getWho());
				assertEquals(ObjectId.zeroId(), entry.getOldId());
				assertEquals(exp.getObjectId(), entry.getNewId());
				assertEquals("create " + exp.getName(), entry.getComment());
			}
			assertFalse(lc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void byObjectIdOneRefNoIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 200; i++) {
			refs.add(ref(String.format("refs/heads/%02d", i), i));
		}
		refs.add(ref("refs/heads/master", 100));

		ReftableReader t = read(write(refs));
		assertEquals(0, stats.objIndexSize());

		try (RefCursor rc = t.byObjectId(id(42))) {
			assertTrue("has 42", rc.next());
			assertEquals("refs/heads/42", rc.getRef().getName());
			assertEquals(id(42), rc.getRef().getObjectId());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.byObjectId(id(100))) {
			assertTrue("has 100", rc.next());
			assertEquals("refs/heads/100", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertTrue("has master", rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertFalse(rc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void byObjectIdOneRefWithIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5200; i++) {
			refs.add(ref(String.format("refs/heads/%02d", i), i));
		}
		refs.add(ref("refs/heads/master", 100));

		ReftableReader t = read(write(refs));
		assertTrue(stats.objIndexSize() > 0);

		try (RefCursor rc = t.byObjectId(id(42))) {
			assertTrue("has 42", rc.next());
			assertEquals("refs/heads/42", rc.getRef().getName());
			assertEquals(id(42), rc.getRef().getObjectId());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.byObjectId(id(100))) {
			assertTrue("has 100", rc.next());
			assertEquals("refs/heads/100", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertTrue("has master", rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertFalse(rc.next());
		}
	}

	@Test
	public void unpeeledDoesNotWrite() {
		try {
			write(new ObjectIdRef.Unpeeled(PACKED, MASTER, id(1)));
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().peeledRefIsRequired, e.getMessage());
		}
	}

	@Test
	public void nameTooLongDoesNotWrite() throws IOException {
		try {
			ReftableConfig cfg = new ReftableConfig();
			cfg.setRefBlockSize(64);

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			ReftableWriter writer = new ReftableWriter(cfg).begin(buffer);
			writer.writeRef(ref("refs/heads/i-am-not-a-teapot", 1));
			writer.finish();
			fail("expected BlockSizeTooSmallException");
		} catch (BlockSizeTooSmallException e) {
			assertEquals(84, e.getMinimumBlockSize());
		}
	}

	@Test
	public void badCrc32() throws IOException {
		byte[] table = write();
		table[table.length - 1] = 0x42;

		try {
			read(table).seekRef(HEAD);
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().invalidReftableCRC, e.getMessage());
		}
	}


	private static void assertScan(List<Ref> refs, Reftable t)
			throws IOException {
		try (RefCursor rc = t.allRefs()) {
			for (Ref exp : refs) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
			}
			assertFalse(rc.next());
		}
	}

	private static void assertSeek(List<Ref> refs, Reftable t)
			throws IOException {
		for (Ref exp : refs) {
			try (RefCursor rc = t.seekRef(exp.getName())) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertFalse(rc.next());
			}
		}
	}

	private static Ref ref(String name, int id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id(id));
	}

	private static Ref tag(String name, int id1, int id2) {
		return new ObjectIdRef.PeeledTag(PACKED, name, id(id1), id(id2));
	}

	private static Ref sym(String name, String target) {
		return new SymbolicRef(name, newRef(target));
	}

	private static Ref newRef(String name) {
		return new ObjectIdRef.Unpeeled(NEW, name, null);
	}

	private static ObjectId id(int i) {
		byte[] buf = new byte[OBJECT_ID_LENGTH];
		buf[0] = (byte) (i & 0xff);
		buf[1] = (byte) ((i >>> 8) & 0xff);
		buf[2] = (byte) ((i >>> 16) & 0xff);
		buf[3] = (byte) (i >>> 24);
		return ObjectId.fromRaw(buf);
	}

	private static ReftableReader read(byte[] table) {
		return new ReftableReader(BlockSource.from(table));
	}

	private byte[] write(Ref... refs) throws IOException {
		return write(Arrays.asList(refs));
	}

	private byte[] write(Collection<Ref> refs) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		stats = new ReftableWriter()
				.begin(buffer)
				.sortAndWriteRefs(refs)
				.finish()
				.getStats();
		return buffer.toByteArray();
	}
}
