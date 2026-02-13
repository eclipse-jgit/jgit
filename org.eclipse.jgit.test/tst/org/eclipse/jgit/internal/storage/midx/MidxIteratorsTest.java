/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MidxIterator;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class MidxIteratorsTest {
	private final static String OID_PREFIX = "0000000000000000000000000000000000";

	@Test
	public void fromPackIndexIterator_basicIteration() {
		PackIndex index1 = indexOf(object("000001", 500),
				object("000003", 3000), object("000005", 1500));

		MidxIterator it = MidxIterators.fromPackIndexIterator("index1", index1);
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000003", 0, 3000);
		assertNextEntry(it, "000005", 0, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void fromPackIndexIterator_peek() {
		PackIndex index1 = indexOf(object("000001", 500),
				object("000003", 3000), object("000005", 1500));

		MidxIterator it = MidxIterators.fromPackIndexIterator("index1", index1);
		assertPeekEntry(it, "000001", 0, 500);
		assertPeekEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000001", 0, 500);

		assertPeekEntry(it, "000003", 0, 3000);
		assertPeekEntry(it, "000003", 0, 3000);
		assertNextEntry(it, "000003", 0, 3000);

		assertPeekEntry(it, "000005", 0, 1500);
		assertPeekEntry(it, "000005", 0, 1500);
		assertNextEntry(it, "000005", 0, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void fromPackIndexIterator_reset() {
		PackIndex index1 = indexOf(object("000001", 500),
				object("000003", 3000), object("000005", 1500));

		MidxIterator it = MidxIterators.fromPackIndexIterator("index1", index1);
		while (it.hasNext()) {
			it.next();
		}
		it.reset();
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000003", 0, 3000);

		it.reset();
		assertPeekEntry(it, "000001", 0, 500);
	}

	@Test
	public void fromPackIndexIterator_getPackNames() {
		PackIndex index1 = indexOf(object("000001", 500),
				object("000003", 1500), object("000005", 3000));
		MidxIterator it = MidxIterators.fromPackIndexIterator("index1", index1);
		assertEquals(List.of("index1"), it.getPackNames());
	}

	@Test
	public void fromPackIndexIterator_empty() {
		MidxIterator it = MidxIterators.fromPackIndexIterator("index1",
				indexOf());
		assertFalse(it.hasNext());
	}

	@Test
	public void join_basicIteration() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000002", 0, 500),
						new IndexEntry("000004", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000002", 2, 500);
		assertNextEntry(it, "000003", 1, 1500);
		assertNextEntry(it, "000004", 3, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void join_basicIteration_packIndexIterators() {
		PackIndex idxOne = indexOf(object("000001", 500),
				object("000003", 1500), object("000005", 3000));

		PackIndex idxTwo = indexOf(object("000002", 500),
				object("000003", 1500), object("000004", 3000));

		List<MidxIterator> packIts = List.of(
				MidxIterators.fromPackIndexIterator("index1", idxOne),
				MidxIterators.fromPackIndexIterator("index2", idxTwo));
		MidxIterator it = MidxIterators.join(packIts);
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000002", 1, 500);
		assertNextEntry(it, "000003", 0, 1500);
		assertNextEntry(it, "000003", 1, 1500);
		assertNextEntry(it, "000004", 1, 3000);
		assertNextEntry(it, "000005", 0, 3000);
		assertFalse(it.hasNext());
	}

	@Test
	public void join_duplicates_inPackIdOrder() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 501),
						new IndexEntry("000003", 1, 1501)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertNextEntry(it, "000001", 0, 501);
		assertNextEntry(it, "000001", 2, 500);
		assertNextEntry(it, "000003", 1, 1501);
		assertNextEntry(it, "000003", 3, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void join_duplicates_inPackIdOrder_shift() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 5,
				List.of(new IndexEntry("000001", 4, 501),
						new IndexEntry("000003", 2, 1501)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertNextEntry(it, "000001", 4, 501);
		assertNextEntry(it, "000001", 5, 500);
		assertNextEntry(it, "000003", 2, 1501);
		assertNextEntry(it, "000003", 6, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void join_peek() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000002", 0, 500),
						new IndexEntry("000004", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertPeekEntry(it, "000001", 0, 500);
		assertPeekEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000001", 0, 500);

		assertPeekEntry(it, "000002", 2, 500);
		assertPeekEntry(it, "000002", 2, 500);
		assertNextEntry(it, "000002", 2, 500);

		assertPeekEntry(it, "000003", 1, 1500);
		assertPeekEntry(it, "000003", 1, 1500);
		assertNextEntry(it, "000003", 1, 1500);

		assertPeekEntry(it, "000004", 3, 1500);
		assertPeekEntry(it, "000004", 3, 1500);
		assertNextEntry(it, "000004", 3, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void join_reset() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000002", 0, 500),
						new IndexEntry("000004", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		while (it.hasNext()) {
			it.next();
		}

		it.reset();
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000002", 2, 500);

		it.reset();
		assertPeekEntry(it, "000001", 0, 500);
	}

	@Test
	public void join_getPackNames() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000003", 1, 1500)));

		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000002", 0, 500),
						new IndexEntry("000004", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertEquals(List.of("itOne0", "itOne1", "itTwo0", "itTwo1"),
				it.getPackNames());
	}

	@Test
	public void join_empty_totallyEmpty() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2, List.of());
		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2, List.of());

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		assertFalse(it.hasNext());
	}

	@Test
	public void join_empty_oneSideEmpty() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2, List.of());
		FakeMidxIterator itTwo = FakeMidxIterator.from("itTwo", 2,
				List.of(new IndexEntry("000002", 0, 500),
						new IndexEntry("000004", 1, 1500)));

		MidxIterator it = MidxIterators.join(List.of(itOne, itTwo));
		// Even when empty, the first iterator occupies the packIds
		assertNextEntry(it, "000002", 2, 500);
		assertNextEntry(it, "000004", 3, 1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void dedup_basicIteration() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000001", 1, 600),
						new IndexEntry("000003", 0, 1500),
						new IndexEntry("000003", 1, 1501)));
		MidxIterator dedup = MidxIterators.dedup(itOne);
		assertNextEntry(dedup, "000001", 0, 500);
		assertNextEntry(dedup, "000003", 0, 1500);
		assertFalse(dedup.hasNext());
	}

	@Test
	public void dedup_peek() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000001", 1, 600),
						new IndexEntry("000003", 0, 1500),
						new IndexEntry("000003", 1, 1501)));

		MidxIterator it = MidxIterators.dedup(itOne);
		assertPeekEntry(it, "000001", 0, 500);
		assertPeekEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000001", 0, 500);

		assertPeekEntry(it, "000003", 0, 1500);
		assertPeekEntry(it, "000003", 0, 1500);
		assertNextEntry(it, "000003", 0, 1500);

		assertFalse(it.hasNext());
	}

	@Test
	public void dedup_reset() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000001", 1, 600),
						new IndexEntry("000003", 0, 1500),
						new IndexEntry("000003", 1, 1501),
						new IndexEntry("000005", 0, 200),
						new IndexEntry("000005", 1, 201)));

		MidxIterator it = MidxIterators.dedup(itOne);
		while (it.hasNext()) {
			it.next();
		}
		it.reset();
		assertNextEntry(it, "000001", 0, 500);
		assertNextEntry(it, "000003", 0, 1500);

		it.reset();
		assertPeekEntry(it, "000001", 0, 500);
	}

	@Test
	public void dedup_reset_sameElement() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 2,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000001", 1, 600),
						new IndexEntry("000001", 2, 1500),
						new IndexEntry("000001", 3, 1501),
						new IndexEntry("000001", 4, 200),
						new IndexEntry("000001", 5, 201)));

		MidxIterator it = MidxIterators.dedup(itOne);
		while (it.hasNext()) {
			it.next();
		}
		it.reset();
		assertNextEntry(it, "000001", 0, 500);

		it.reset();
		assertPeekEntry(it, "000001", 0, 500);
	}

	@Test
	public void dedup_getPackNames() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 4,
				List.of(new IndexEntry("000001", 0, 500),
						new IndexEntry("000001", 1, 600),
						new IndexEntry("000003", 2, 1500),
						new IndexEntry("000003", 3, 1501)));
		MidxIterator dedup = MidxIterators.dedup(itOne);
		assertEquals(List.of("itOne0", "itOne1", "itOne2", "itOne3"),
				dedup.getPackNames());
	}

	@Test
	public void dedup_getEmpty() {
		FakeMidxIterator itOne = FakeMidxIterator.from("itOne", 4, List.of());
		MidxIterator dedup = MidxIterators.dedup(itOne);
		assertFalse(dedup.hasNext());
	}

	private static void assertNextEntry(MidxIterator it, String shortOid,
			int packId, int offset) {
		assertTrue("expected to have more items", it.hasNext());
		MultiPackIndex.MutableEntry e = it.next();
		assertEquals(OID_PREFIX + shortOid, e.getObjectId().name());
		assertEquals(packId, e.getPackId());
		assertEquals(offset, e.getOffset());
	}

	private static void assertPeekEntry(MidxIterator it, String shortOid,
			int packId, int offset) {
		assertTrue(it.hasNext());
		MultiPackIndex.MutableEntry e = it.peek();
		assertEquals(OID_PREFIX + shortOid, e.getObjectId().name());
		assertEquals(packId, e.getPackId());
		assertEquals(offset, e.getOffset());
	}

	private static PackIndex indexOf(FakeIndexFactory.IndexObject... objs) {
		return FakeIndexFactory.indexOf(Arrays.asList(objs));
	}

	private static FakeIndexFactory.IndexObject object(String name,
			long offset) {
		return new FakeIndexFactory.IndexObject(OID_PREFIX + name, offset);
	}

	private static class FakeMidxIterator implements MidxIterator {
		private final List<String> packNames;

		private final List<IndexEntry> entries;

		private int position;

		static FakeMidxIterator from(String packNameBase, int packCount,
				List<IndexEntry> entries) {
			List<String> packNames = IntStream.range(0, packCount)
					.mapToObj(i -> packNameBase + i).toList();
			return new FakeMidxIterator(packNames, entries);
		}

		private FakeMidxIterator(List<String> packNames,
				List<IndexEntry> entries) {
			this.entries = entries;
			this.packNames = packNames;
		}

		@Override
		public MultiPackIndex.MutableEntry peek() {
			return entries.get(position).asMutableEntry();
		}

		@Override
		public List<String> getPackNames() {
			return packNames;
		}

		@Override
		public boolean hasNext() {
			return position < entries.size();
		}

		@Override
		public MultiPackIndex.MutableEntry next() {
			return entries.get(position++).asMutableEntry();
		}

		@Override
		public void reset() {
			position = 0;
		}
	}

	record IndexEntry(String shortOid, int packId, int offset) {
		MultiPackIndex.MutableEntry asMutableEntry() {
			MultiPackIndex.MutableEntry entry = new MultiPackIndex.MutableEntry();
			entry.oid.fromObjectId(ObjectId.fromString(OID_PREFIX + shortOid));
			entry.packOffset.setValues(packId, offset);
			return entry;
		}
	}
}
