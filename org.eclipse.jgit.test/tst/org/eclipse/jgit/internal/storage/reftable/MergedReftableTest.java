/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Test;

public class MergedReftableTest {
	@Test
	public void noTables() throws IOException {
		MergedReftable mr = merge(new byte[0][]);
		try (RefCursor rc = mr.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRefsWithPrefix(R_HEADS)) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void oneEmptyTable() throws IOException {
		MergedReftable mr = merge(write());
		try (RefCursor rc = mr.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRefsWithPrefix(R_HEADS)) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void twoEmptyTables() throws IOException {
		MergedReftable mr = merge(write(), write());
		try (RefCursor rc = mr.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = mr.seekRefsWithPrefix(R_HEADS)) {
			assertFalse(rc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void oneTableScan() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			refs.add(ref(String.format("refs/heads/%03d", i), i));
		}

		MergedReftable mr = merge(write(refs));
		try (RefCursor rc = mr.allRefs()) {
			for (Ref exp : refs) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertEquals(1, act.getUpdateIndex());
			}
			assertFalse(rc.next());
		}
	}

	@Test
	public void deleteIsHidden() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/apple", 1),
				ref("refs/heads/master", 2));
		List<Ref> delta2 = Arrays.asList(delete("refs/heads/apple"));

		MergedReftable mr = merge(write(delta1), write(delta2));
		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
	}

	@Test
	public void twoTableSeek() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/apple", 1),
				ref("refs/heads/master", 2));
		List<Ref> delta2 = Arrays.asList(ref("refs/heads/banana", 3));

		MergedReftable mr = merge(write(delta1), write(delta2));
		try (RefCursor rc = mr.seekRef("refs/heads/master")) {
			assertTrue(rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertFalse(rc.next());
			assertEquals(1, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void twoTableById() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/apple", 1),
				ref("refs/heads/master", 2));
		List<Ref> delta2 = Arrays.asList(ref("refs/heads/banana", 3));

		MergedReftable mr = merge(write(delta1), write(delta2));
		try (RefCursor rc = mr.byObjectId(id(2))) {
			assertTrue(rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
	}

	@Test
	public void tableByIDDeletion() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/apple", 1),
				ref("refs/heads/master", 2));
		List<Ref> delta2 = Arrays.asList(ref("refs/heads/master", 3));

		MergedReftable mr = merge(write(delta1), write(delta2));
		try (RefCursor rc = mr.byObjectId(id(2))) {
			assertFalse(rc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void fourTableScan() throws IOException {
		List<Ref> base = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			base.add(ref(String.format("refs/heads/%03d", i), i));
		}

		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/next", 4),
				ref(String.format("refs/heads/%03d", 55), 4096));
		List<Ref> delta2 = Arrays.asList(
				delete("refs/heads/next"),
				ref(String.format("refs/heads/%03d", 55), 8192));
		List<Ref> delta3 = Arrays.asList(
				ref("refs/heads/master", 4242),
				ref(String.format("refs/heads/%03d", 42), 5120),
				ref(String.format("refs/heads/%03d", 98), 6120));

		List<Ref> expected = merge(base, delta1, delta2, delta3);
		MergedReftable mr = merge(
				write(base),
				write(delta1),
				write(delta2),
				write(delta3));
		try (RefCursor rc = mr.allRefs()) {
			for (Ref exp : expected) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertEquals(1, rc.getRef().getUpdateIndex());
			}
			assertFalse(rc.next());
		}
	}

	@Test
	public void scanIncludeDeletes() throws IOException {
		List<Ref> delta1 = Arrays.asList(ref("refs/heads/next", 4));
		List<Ref> delta2 = Arrays.asList(delete("refs/heads/next"));
		List<Ref> delta3 = Arrays.asList(ref("refs/heads/master", 8));

		MergedReftable mr = merge(write(delta1), write(delta2), write(delta3));
		mr.setIncludeDeletes(true);
		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			Ref r = rc.getRef();
			assertEquals("refs/heads/master", r.getName());
			assertEquals(id(8), r.getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			r = rc.getRef();
			assertEquals("refs/heads/next", r.getName());
			assertEquals(NEW, r.getStorage());
			assertNull(r.getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());

			assertFalse(rc.next());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void oneTableSeek() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			refs.add(ref(String.format("refs/heads/%03d", i), i));
		}

		MergedReftable mr = merge(write(refs));
		for (Ref exp : refs) {
			try (RefCursor rc = mr.seekRef(exp.getName())) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertEquals(1, act.getUpdateIndex());
				assertFalse(rc.next());
			}
		}
	}

	@Test
	public void missedUpdate() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(3)
				.begin();
		writer.writeRef(ref("refs/heads/a", 1), 1);
		writer.writeRef(ref("refs/heads/c", 3), 3);
		writer.finish();
		byte[] base = buf.toByteArray();

		byte[] delta = write(Arrays.asList(
				ref("refs/heads/b", 2),
				ref("refs/heads/c", 4)),
				2);
		MergedReftable mr = merge(base, delta);
		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			assertEquals("refs/heads/a", rc.getRef().getName());
			assertEquals(id(1), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/b", rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(2, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/c", rc.getRef().getName());
			assertEquals(id(3), rc.getRef().getObjectId());
			assertEquals(3, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void nonOverlappedUpdateIndices() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(2)
				.begin();
		writer.writeRef(ref("refs/heads/a", 1), 1);
		writer.writeRef(ref("refs/heads/b", 2), 2);
		writer.finish();
		byte[] base = buf.toByteArray();

		buf = new ByteArrayOutputStream();
		writer = new ReftableWriter(buf)
				.setMinUpdateIndex(3)
				.setMaxUpdateIndex(4)
				.begin();
		writer.writeRef(ref("refs/heads/a", 10), 3);
		writer.writeRef(ref("refs/heads/b", 20), 4);
		writer.finish();
		byte[] delta = buf.toByteArray();

		MergedReftable mr = merge(base, delta);
		assertEquals(1, mr.minUpdateIndex());
		assertEquals(4, mr.maxUpdateIndex());

		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			assertEquals("refs/heads/a", rc.getRef().getName());
			assertEquals(id(10), rc.getRef().getObjectId());
			assertEquals(3, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/b", rc.getRef().getName());
			assertEquals(id(20), rc.getRef().getObjectId());
			assertEquals(4, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void overlappedUpdateIndices() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(3)
				.begin();
		writer.writeRef(ref("refs/heads/a", 1), 1);
		writer.writeRef(ref("refs/heads/b", 2), 3);
		writer.finish();
		byte[] base = buf.toByteArray();

		buf = new ByteArrayOutputStream();
		writer = new ReftableWriter(buf)
				.setMinUpdateIndex(2)
				.setMaxUpdateIndex(4)
				.begin();
		writer.writeRef(ref("refs/heads/a", 10), 2);
		writer.writeRef(ref("refs/heads/b", 20), 4);
		writer.finish();
		byte[] delta = buf.toByteArray();

		MergedReftable mr = merge(base, delta);
		assertEquals(1, mr.minUpdateIndex());
		assertEquals(4, mr.maxUpdateIndex());

		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			assertEquals("refs/heads/a", rc.getRef().getName());
			assertEquals(id(10), rc.getRef().getObjectId());
			assertEquals(2, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/b", rc.getRef().getName());
			assertEquals(id(20), rc.getRef().getObjectId());
			assertEquals(4, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void enclosedUpdateIndices() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(4)
				.begin();
		writer.writeRef(ref("refs/heads/a", 1), 1);
		writer.writeRef(ref("refs/heads/b", 20), 4);
		writer.finish();
		byte[] base = buf.toByteArray();

		buf = new ByteArrayOutputStream();
		writer = new ReftableWriter(buf)
				.setMinUpdateIndex(2)
				.setMaxUpdateIndex(3)
				.begin();
		writer.writeRef(ref("refs/heads/a", 10), 2);
		writer.writeRef(ref("refs/heads/b", 2), 3);
		writer.finish();
		byte[] delta = buf.toByteArray();

		MergedReftable mr = merge(base, delta);
		assertEquals(1, mr.minUpdateIndex());
		assertEquals(4, mr.maxUpdateIndex());

		try (RefCursor rc = mr.allRefs()) {
			assertTrue(rc.next());
			assertEquals("refs/heads/a", rc.getRef().getName());
			assertEquals(id(10), rc.getRef().getObjectId());
			assertEquals(2, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/b", rc.getRef().getName());
			assertEquals(id(20), rc.getRef().getObjectId());
			assertEquals(4, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void compaction() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/next", 4),
				ref("refs/heads/master", 1));
		List<Ref> delta2 = Arrays.asList(delete("refs/heads/next"));
		List<Ref> delta3 = Arrays.asList(ref("refs/heads/master", 8));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ReftableCompactor compactor = new ReftableCompactor(out);
		compactor.addAll(Arrays.asList(
				read(write(delta1)),
				read(write(delta2)),
				read(write(delta3))));
		compactor.compact();
		byte[] table = out.toByteArray();

		ReftableReader reader = read(table);
		try (RefCursor rc = reader.allRefs()) {
			assertTrue(rc.next());
			Ref r = rc.getRef();
			assertEquals("refs/heads/master", r.getName());
			assertEquals(id(8), r.getObjectId());
			assertFalse(rc.next());
		}
	}

	@Test
	public void versioningSymbolicReftargetMoves() throws IOException {
		Ref master = ref(MASTER, 100);

		List<Ref> delta1 = Arrays.asList(master, sym(HEAD, MASTER));
		List<Ref> delta2 = Arrays.asList(ref(MASTER, 200));

		MergedReftable mr = merge(write(delta1, 1), write(delta2, 2));
		Ref head = mr.exactRef(HEAD);
		assertEquals(head.getUpdateIndex(), 1);

		Ref masterRef = mr.exactRef(MASTER);
		assertEquals(masterRef.getUpdateIndex(), 2);
	}

	@Test
	public void versioningSymbolicRefMoves() throws IOException {
		Ref branchX = ref("refs/heads/branchX", 200);

		List<Ref> delta1 = Arrays.asList(ref(MASTER, 100), branchX,
				sym(HEAD, MASTER));
		List<Ref> delta2 = Arrays.asList(sym(HEAD, "refs/heads/branchX"));
		List<Ref> delta3 = Arrays.asList(sym(HEAD, MASTER));

		MergedReftable mr = merge(write(delta1, 1), write(delta2, 2),
				write(delta3, 3));
		Ref head = mr.exactRef(HEAD);
		assertEquals(head.getUpdateIndex(), 3);

		Ref masterRef = mr.exactRef(MASTER);
		assertEquals(masterRef.getUpdateIndex(), 1);

		Ref branchRef = mr.exactRef(MASTER);
		assertEquals(branchRef.getUpdateIndex(), 1);
	}

	@Test
	public void versioningResolveRef() throws IOException {
		List<Ref> delta1 = Arrays.asList(sym(HEAD, "refs/heads/tmp"),
				sym("refs/heads/tmp", MASTER), ref(MASTER, 100));
		List<Ref> delta2 = Arrays.asList(ref(MASTER, 200));
		List<Ref> delta3 = Arrays.asList(ref(MASTER, 300));

		MergedReftable mr = merge(write(delta1, 1), write(delta2, 2),
				write(delta3, 3));
		Ref head = mr.exactRef(HEAD);
		Ref resolvedHead = mr.resolve(head);
		assertEquals(resolvedHead.getObjectId(), id(300));
		assertEquals("HEAD has not moved", resolvedHead.getUpdateIndex(), 1);

		Ref master = mr.exactRef(MASTER);
		Ref resolvedMaster = mr.resolve(master);
		assertEquals(resolvedMaster.getObjectId(), id(300));
		assertEquals("master also has update index",
				resolvedMaster.getUpdateIndex(), 3);
	}

	private static MergedReftable merge(byte[]... table) {
		List<ReftableReader> stack = new ArrayList<>(table.length);
		for (byte[] b : table) {
			stack.add(read(b));
		}
		return new MergedReftable(stack);
	}

	private static ReftableReader read(byte[] table) {
		return new ReftableReader(BlockSource.from(table));
	}

	private static Ref ref(String name, int id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id(id));
	}

	private static Ref sym(String name, String target) {
		return new SymbolicRef(name, newRef(target));
	}

	private static Ref newRef(String name) {
		return new ObjectIdRef.Unpeeled(NEW, name, null);
	}

	private static Ref delete(String name) {
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

	private byte[] write(Ref... refs) throws IOException {
		return write(Arrays.asList(refs));
	}

	private byte[] write(Collection<Ref> refs) throws IOException {
		return write(refs, 1);
	}

	private byte[] write(Collection<Ref> refs, long updateIndex)
			throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		new ReftableWriter(buffer)
				.setMinUpdateIndex(updateIndex)
				.setMaxUpdateIndex(updateIndex)
				.begin()
				.sortAndWriteRefs(refs)
				.finish();
		return buffer.toByteArray();
	}

	@SafeVarargs
	private static List<Ref> merge(List<Ref>... tables) {
		Map<String, Ref> expect = new HashMap<>();
		for (List<Ref> t : tables) {
			for (Ref r : t) {
				if (r.getStorage() == NEW && r.getObjectId() == null) {
					expect.remove(r.getName());
				} else {
					expect.put(r.getName(), r);
				}
			}
		}

		List<Ref> expected = new ArrayList<>(expect.values());
		Collections.sort(expected, RefComparator.INSTANCE);
		return expected;
	}
}
