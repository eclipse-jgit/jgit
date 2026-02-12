/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class MultiPackIndexTest {

	@Test
	public void basic_upstream() throws IOException {
		int knownPackId = 22;
		int knowOffset = 258;
		String knownOid = "3f4ee50f784c1e9550f09a67d2ffc1bc76917bdc";
		String knownPackName = "pack-e4b191e4343f2b7ff851026c2d8595a001077344.idx";
		String[] packNames = {
				"pack-15d67b35f2b6a66ff995e09cedb36b101e0e0262.idx",
				"pack-1a979514a5965e71523187a17806e03af44344ed.idx",
				"pack-1de6731c035633ba8f5b41dacbc680a5a36ddd90.idx",
				"pack-1ee98948e4e362c56f3cdec7f5837d06e152854f.idx",
				"pack-1f6fe52ac3d33f3091d8eb8497474554bfa80bc4.idx",
				"pack-34b1aa6b437a9d968412454204c2676a88dc55fa.idx",
				"pack-3b245f7b4aff32a52d0520608f662bbf403792b9.idx",
				"pack-47901f7f8d1c440492035c4165796a330c7f79e0.idx",
				"pack-4e7f889b79aea8905a0062ce1bd68e5ef3af6a55.idx",
				"pack-71ea652e4aea2cbc609545b4fbc3eda6325d88a1.idx",
				"pack-723b1238411a4257c18167e91fbabed313ba332f.idx",
				"pack-7bd57092a7daa4dc31277e1ec86f3de8d968ae17.idx",
				"pack-883d4f469c5ea0f6d373ee623a758aeaf17715fc.idx",
				"pack-8eadd378a011ddaa5ec751f2a6d9789ef501120f.idx",
				"pack-92221b6f79a211944ccc6740fc22c9553ea1ba22.idx",
				"pack-b139d0cae5f54c70d057a8f4d2cf99f0ae0c326c.idx",
				"pack-b4f5c96d1fa6b1fac17a2a43710693c5514a9224.idx",
				"pack-bed4bc1521f965e55a5a8a58dffaaefc70ea4753.idx",
				"pack-cdc6baa7d90707a3c0dac4c188f797f0f79b97bb.idx",
				"pack-d6d58a58fa24b74c8c082f4f63c4d2ddfb824cc9.idx",
				"pack-daec59ae07f1091f3b81bd8266481bb5db3c868a.idx",
				"pack-e2197d60e09ad9091407eff4e06d39ec940851e1.idx",
				"pack-e4b191e4343f2b7ff851026c2d8595a001077344.idx",
				"pack-eedf783b5da4caa57be33b08990fe57f245a7413.idx",
				"pack-efb23e968801b9050bc70f0115a8a0eec88fb879.idx",
				"pack-f919c0660c207ddf6bb0569a3041d682d19fb4f7.idx" };
		MultiPackIndex midx = MultiPackIndexLoader
				.open(JGitTestUtil.getTestResourceFile("multi-pack-index.v1"));
		assertNotNull(midx);
		assertArrayEquals(packNames, midx.getPackNames());

		MultiPackIndex.PackOffset oo = midx.find(ObjectId.fromString(knownOid));

		assertEquals(knowOffset, oo.getOffset());
		assertEquals(knownPackId, oo.getPackId());
		assertEquals(knownPackName, midx.getPackNames()[oo.getPackId()]);
	}

	@Test
	public void basicMidx() throws IOException {
		MultiPackIndex midx = createMultiPackIndex();
		assertEquals(3, midx.getPackNames().length);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000001", 500);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000005", 12);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000010",
				1500);
		assertInIndex(midx, 1, "0000000000000000000000000000000000000002", 501);
		assertInIndex(midx, 1, "0000000000000000000000000000000000000003", 13);
		assertInIndex(midx, 1, "0000000000000000000000000000000000000015",
				1501);
		assertInIndex(midx, 2, "0000000000000000000000000000000000000004", 502);
		assertInIndex(midx, 2, "0000000000000000000000000000000000000007", 14);
		assertInIndex(midx, 2, "0000000000000000000000000000000000000012",
				1502);

		assertNull(midx.find(ObjectId.zeroId()));
		assertNotNull(midx.getChecksum());
	}

	@Test
	public void jgit_largeOffsetChunk() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", (1L << 34)),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", (1L << 35)),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13)));
		PackIndexMerger packs = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(2, midx.getPackNames().length);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000001",
				(1L << 34));
		assertInIndex(midx, 0, "0000000000000000000000000000000000000005", 12);
		assertInIndex(midx, 1, "0000000000000000000000000000000000000002",
				(1L << 35));
	}

	@Test
	public void jgit_largeOffset_noChunk() throws IOException {
		// All offsets fit in 32 bits, no large offset chunk
		// Most significant bit to 1 is still valid offset
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001",
						0xff00_0000),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13)));
		PackIndexMerger packs = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(2, midx.getPackNames().length);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000001",
				0xff00_0000L);
		assertInIndex(midx, 0, "0000000000000000000000000000000000000005", 12);
	}

	@Test
	public void jgit_resolve() throws IOException {
		AbbreviatedObjectId abbrev = AbbreviatedObjectId
				.fromString("32fe829a1c");

		PackIndex idxOne = indexWith(
				// Noise
				"0000000000000000000000000000000000000001",
				"3000000000000000000000000000000000000005",
				// One before abbrev
				"32fe829a1b000000000000000000000000000001",
				// matches
				"32fe829a1c000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000100",
				// One after abbrev
				"32fe829a1d000000000000000000000000000000");
		PackIndex idxTwo = indexWith(
				// Noise
				"8888880000000000000000000000000000000002",
				"bbbbbb0000000000000000000000000000000003",
				// Match
				"32fe829a1c000000000000000000000000000010");

		PackIndexMerger packs = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		Set<ObjectId> results = new HashSet<>();
		midx.resolve(results, abbrev, 100);

		assertEquals(3, results.size());
		assertTrue(results.contains(ObjectId
				.fromString("32fe829a1c000000000000000000000000000001")));
		assertTrue(results.contains(ObjectId
				.fromString("32fe829a1c000000000000000000000000000010")));
		assertTrue(results.contains(ObjectId
				.fromString("32fe829a1c000000000000000000000000000100")));

	}

	@Test
	public void jgit_resolve_matchLimit() throws IOException {
		AbbreviatedObjectId abbrev = AbbreviatedObjectId
				.fromString("32fe829a1c");

		PackIndex idxOne = indexWith(
				// Noise
				"0000000000000000000000000000000000000001",
				"3000000000000000000000000000000000000005",
				// One before abbrev
				"32fe829a1b000000000000000000000000000001",
				// matches
				"32fe829a1c000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000100",
				// One after abbrev
				"32fe829a1d000000000000000000000000000000");
		PackIndex idxTwo = indexWith(
				// Noise
				"8888880000000000000000000000000000000002",
				"bbbbbb0000000000000000000000000000000003",
				// Match
				"32fe829a1c000000000000000000000000000010");

		PackIndexMerger packs = midxDataFor("r1", idxOne,
				"r2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		Set<ObjectId> results = new HashSet<>();
		midx.resolve(results, abbrev, 2);

		assertEquals(2, results.size());
		assertTrue(results.contains(ObjectId
				.fromString("32fe829a1c000000000000000000000000000001")));
		assertTrue(results.contains(ObjectId
				.fromString("32fe829a1c000000000000000000000000000010")));
	}

	@Test
	public void jgit_resolve_noMatches() throws IOException {
		AbbreviatedObjectId abbrev = AbbreviatedObjectId
				.fromString("4400000000");

		PackIndex idxOne = indexWith("0000000000000000000000000000000000000001",
				"3000000000000000000000000000000000000005",
				"32fe829a1b000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000100",
				"32fe829a1d000000000000000000000000000000");
		PackIndex idxTwo = indexWith(
				// Noise
				"8888880000000000000000000000000000000002",
				"bbbbbb0000000000000000000000000000000003",
				"32fe829a1c000000000000000000000000000010");

		PackIndexMerger packs = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		Set<ObjectId> results = new HashSet<>();
		midx.resolve(results, abbrev, 200);

		assertEquals(0, results.size());
	}

	@Test
	public void jgit_resolve_noMatches_last() throws IOException {
		AbbreviatedObjectId abbrev = AbbreviatedObjectId
				.fromString("dd00000000");

		PackIndex idxOne = indexWith("0000000000000000000000000000000000000001",
				"3000000000000000000000000000000000000005",
				"32fe829a1b000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000001",
				"32fe829a1c000000000000000000000000000100",
				"32fe829a1d000000000000000000000000000000");
		PackIndex idxTwo = indexWith(
				// Noise
				"8888880000000000000000000000000000000002",
				"bbbbbb0000000000000000000000000000000003",
				"32fe829a1c000000000000000000000000000010");

		PackIndexMerger packs = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		Set<ObjectId> results = new HashSet<>();
		midx.resolve(results, abbrev, 200);

		assertEquals(0, results.size());
	}

	@Test
	public void jgit_resolve_empty() throws IOException {
		AbbreviatedObjectId abbrev = AbbreviatedObjectId
				.fromString("4400000000");

		PackIndex idxOne = FakeIndexFactory.indexOf(List.of());
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of());

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		Set<ObjectId> results = new HashSet<>();
		midx.resolve(results, abbrev, 200);

		assertEquals(0, results.size());
	}

	@Test
	public void jgit_findPosition() throws IOException {
		MultiPackIndex midx = createMultiPackIndex();
		assertEquals(3, midx.getPackNames().length);
		assertEquals(0, midx.findPosition(oid("001")));
		assertEquals(oid("001"), midx.getObjectAt(0));
		assertEquals(1, midx.findPosition(oid("002")));
		assertEquals(oid("002"), midx.getObjectAt(1));
		assertEquals(2, midx.findPosition(oid("003")));
		assertEquals(oid("003"), midx.getObjectAt(2));
		assertEquals(3, midx.findPosition(oid("004")));
		assertEquals(oid("004"), midx.getObjectAt(3));
		assertEquals(4, midx.findPosition(oid("005")));
		assertEquals(oid("005"), midx.getObjectAt(4));
		assertEquals(5, midx.findPosition(oid("007")));
		assertEquals(oid("007"), midx.getObjectAt(5));
		assertEquals(6, midx.findPosition(oid("010")));
		assertEquals(oid("010"), midx.getObjectAt(6));
		assertEquals(7, midx.findPosition(oid("012")));
		assertEquals(oid("012"), midx.getObjectAt(7));
		assertEquals(8, midx.findPosition(oid("015")));
		assertEquals(oid("015"), midx.getObjectAt(8));

		assertNull(midx.find(ObjectId.zeroId()));
	}

	private static MultiPackIndex createMultiPackIndex() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", 500),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000010", 1500)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000015", 1501)));
		PackIndex idxThree = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000004", 502),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000007", 14),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000012", 1502)));

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo, "p3", idxThree);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);

		return MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
	}

	@Test
	public void jgit_getObjectCount() throws IOException {
		MultiPackIndex midx = createMultiPackIndex();
		assertEquals(9, midx.getObjectCount());
	}

	@Test
	public void jgit_getObjectCount_emtpy() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of());
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of());

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		assertEquals(0, midx.getObjectCount());
	}

	@Test
	public void jgit_findBitmapPosition() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", 500),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000010", 1500)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000015", 1501)));
		PackIndex idxThree = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000004", 502),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000007", 14),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000012", 1502)));

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo, "p3", idxThree);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		MultiPackIndex.PackOffset packOffset;
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000005"));
		assertEquals(0, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000001"));
		assertEquals(1, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000010"));
		assertEquals(2, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000003"));
		assertEquals(3, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000002"));
		assertEquals(4, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000015"));
		assertEquals(5, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000007"));
		assertEquals(6, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000004"));
		assertEquals(7, midx.findBitmapPosition(packOffset));
		packOffset = midx.find(ObjectId
				.fromString("0000000000000000000000000000000000000012"));
		assertEquals(8, midx.findBitmapPosition(packOffset));
	}

	@Test
	public void jgit_getObjectAtBitmapPosition() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", 500),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000010", 1500)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000015", 1501)));
		PackIndex idxThree = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000004", 502),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000007", 14),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000012", 1502)));

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo, "p3", idxThree);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000005"),
				midx.getObjectAtBitmapPosition(0));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000001"),
				midx.getObjectAtBitmapPosition(1));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000010"),
				midx.getObjectAtBitmapPosition(2));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000003"),
				midx.getObjectAtBitmapPosition(3));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000002"),
				midx.getObjectAtBitmapPosition(4));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000015"),
				midx.getObjectAtBitmapPosition(5));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000007"),
				midx.getObjectAtBitmapPosition(6));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000004"),
				midx.getObjectAtBitmapPosition(7));
		assertEquals(
				ObjectId.fromString("0000000000000000000000000000000000000012"),
				midx.getObjectAtBitmapPosition(8));
	}

	@Test
	public void jgit_iterator() throws IOException {
		MultiPackIndex midx = createMultiPackIndex();
		assertEquals(3, midx.getPackNames().length);
		Iterator<MultiPackIndex.MutableEntry> iterator = midx.iterator();
		assertNextEntry(iterator, "0000000000000000000000000000000000000001", 0,
				500);
		assertNextEntry(iterator, "0000000000000000000000000000000000000002", 1,
				501);
		assertNextEntry(iterator, "0000000000000000000000000000000000000003", 1,
				13);
		assertNextEntry(iterator, "0000000000000000000000000000000000000004", 2,
				502);
		assertNextEntry(iterator, "0000000000000000000000000000000000000005", 0,
				12);
		assertNextEntry(iterator, "0000000000000000000000000000000000000007", 2,
				14);
		assertNextEntry(iterator, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(iterator, "0000000000000000000000000000000000000012", 2,
				1502);
		assertNextEntry(iterator, "0000000000000000000000000000000000000015", 1,
				1501);
		assertFalse(iterator.hasNext());
	}

	@Test
	public void jgit_iterator_emtpy() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of());
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of());

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));

		assertFalse(midx.iterator().hasNext());
	}

	@Test
	public void jgit_iterator_peek() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000001", 500),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000005", 12),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000010", 1500)));
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of(
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000002", 501),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000003", 13),
				new FakeIndexFactory.IndexObject(
						"0000000000000000000000000000000000000015", 1501)));

		PackIndexMerger data = midxDataFor("p1", idxOne,
				"p2", idxTwo);
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);

		MultiPackIndex midx = MultiPackIndexLoader
				.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(2, midx.getPackNames().length);
		MultiPackIndex.MidxIterator iterator = midx.iterator();
		assertNextEntry(iterator, "0000000000000000000000000000000000000001", 0,
				500);
		assertNextEntry(iterator, "0000000000000000000000000000000000000002", 1,
				501);
		assertTrue(iterator.hasNext());
		assertEntry(iterator.peek(), "0000000000000000000000000000000000000003",
				1, 13);
		assertEntry(iterator.peek(), "0000000000000000000000000000000000000003",
				1, 13);
		assertNextEntry(iterator, "0000000000000000000000000000000000000003", 1,
				13);
		assertNextEntry(iterator, "0000000000000000000000000000000000000005", 0,
				12);
		assertNextEntry(iterator, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(iterator, "0000000000000000000000000000000000000015", 1,
				1501);
		assertFalse(iterator.hasNext());
	}

	@Test
	public void jgit_iterator_getPackNames() throws IOException {
		MultiPackIndex midx = createMultiPackIndex();
		assertEquals(3, midx.iterator().getPackNames().size());
	}

	private static PackIndex indexWith(String... oids) {
		List<FakeIndexFactory.IndexObject> idxObjs = new ArrayList<>(
				oids.length);
		int offset = 12;
		for (String oid : oids) {
			idxObjs.add(new FakeIndexFactory.IndexObject(oid, offset));
			offset += 10;
		}
		return FakeIndexFactory.indexOf(idxObjs);
	}

	private static void assertInIndex(MultiPackIndex midx, int expectedPackId,
			String oid, long expectedOffset) {
		MultiPackIndex.PackOffset packOffset = midx
				.find(ObjectId.fromString(oid));
		assertNotNull(packOffset);
		assertEquals("Wrong packId for " + oid, expectedPackId,
				packOffset.getPackId());
		assertEquals(expectedOffset, packOffset.getOffset());
	}

	private static void assertNextEntry(
			Iterator<MultiPackIndex.MutableEntry> it, String oid,
			int expectedPackId, long expectedOffset) {
		assertTrue(it.hasNext());
		assertEntry(it.next(), oid, expectedPackId, expectedOffset);
	}

	private static void assertEntry(MultiPackIndex.MutableEntry e, String oid,
			int expectedPackId, long expectedOffset) {
		assertEquals(oid, e.oid.name());
		assertEquals(expectedPackId, e.packOffset.getPackId());
		assertEquals(expectedOffset, e.packOffset.getOffset());
	}

	private static PackIndexMerger midxDataFor(String s1, PackIndex pi1,
			String s2, PackIndex pi2) {
		return PackIndexMerger.builder().addPack(s1, pi1).addPack(s2, pi2)
				.build();
	}

	private static PackIndexMerger midxDataFor(String s1,
			PackIndex pi1, String s2, PackIndex pi2, String s3, PackIndex pi3) {
		return PackIndexMerger.builder().addPack(s1, pi1).addPack(s2, pi2)
				.addPack(s3, pi3).build();
	}

	private static ObjectId oid(String last3chars) {
		assertEquals(3, last3chars.length());
		return ObjectId.fromString(
				"0000000000000000000000000000000000000" + last3chars);
	}
}
