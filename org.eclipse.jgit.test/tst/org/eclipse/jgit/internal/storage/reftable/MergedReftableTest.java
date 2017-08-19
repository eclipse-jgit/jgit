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
		try (RefCursor rc = mr.seekRef(R_HEADS)) {
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
		try (RefCursor rc = mr.seekRef(R_HEADS)) {
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
		try (RefCursor rc = mr.seekRef(R_HEADS)) {
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

			assertTrue(rc.next());
			r = rc.getRef();
			assertEquals("refs/heads/next", r.getName());
			assertEquals(NEW, r.getStorage());
			assertNull(r.getObjectId());

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
				assertFalse(rc.next());
			}
		}
	}

	@Test
	public void missedUpdate() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter()
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(3)
				.begin(buf);
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
			assertEquals(1, rc.getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/b", rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(2, rc.getUpdateIndex());

			assertTrue(rc.next());
			assertEquals("refs/heads/c", rc.getRef().getName());
			assertEquals(id(3), rc.getRef().getObjectId());
			assertEquals(3, rc.getUpdateIndex());
		}
	}

	@Test
	public void compaction() throws IOException {
		List<Ref> delta1 = Arrays.asList(
				ref("refs/heads/next", 4),
				ref("refs/heads/master", 1));
		List<Ref> delta2 = Arrays.asList(delete("refs/heads/next"));
		List<Ref> delta3 = Arrays.asList(ref("refs/heads/master", 8));

		ReftableCompactor compactor = new ReftableCompactor();
		compactor.addAll(Arrays.asList(
				read(write(delta1)),
				read(write(delta2)),
				read(write(delta3))));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		compactor.compact(out);
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

	private static MergedReftable merge(byte[]... table) {
		List<Reftable> stack = new ArrayList<>(table.length);
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
		new ReftableWriter()
				.setMinUpdateIndex(updateIndex)
				.setMaxUpdateIndex(updateIndex)
				.begin(buffer)
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
