/*
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Before;
import org.junit.Test;

public class PackReverseIndexComputedTest extends RepositoryTestCase {

	private PackIndex idx;

	private PackReverseIndex reverseIdx;

	/**
	 * Set up tested class instance, test constructor by the way.
	 */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		// index with both small (< 2^31) and big offsets
		idx = PackIndex.open(JGitTestUtil.getTestResourceFile("pack-huge.idx"));
		reverseIdx = PackReverseIndexFactory.computeFromIndex(idx);
	}

	/**
	 * Test findObject() for all index entries.
	 */
	@Test
	public void testFindObject() {
		for (MutableEntry me : idx)
			assertEquals(me.toObjectId(), reverseIdx.findObject(me.getOffset()));
	}

	/**
	 * Test findObject() with illegal argument.
	 */
	@Test
	public void testFindObjectWrongOffset() {
		assertNull(reverseIdx.findObject(0));
	}

	/**
	 * Test findNextOffset() for all index entries.
	 *
	 * @throws CorruptObjectException
	 */
	@Test
	public void testFindNextOffset() throws CorruptObjectException {
		long offset = findFirstOffset();
		assertTrue(offset > 0);
		for (int i = 0; i < idx.getObjectCount(); i++) {
			long newOffset = reverseIdx.findNextOffset(offset, Long.MAX_VALUE);
			assertTrue(newOffset > offset);
			if (i == idx.getObjectCount() - 1)
				assertEquals(newOffset, Long.MAX_VALUE);
			else
				assertEquals(newOffset, idx.findOffset(reverseIdx
						.findObject(newOffset)));
			offset = newOffset;
		}
	}

	/**
	 * Test findNextOffset() with wrong illegal argument as offset.
	 */
	@Test
	public void testFindNextOffsetWrongOffset() {
		try {
			reverseIdx.findNextOffset(0, Long.MAX_VALUE);
			fail("findNextOffset() should throw exception");
		} catch (CorruptObjectException x) {
			// expected
		}
	}

	@Test
	public void testVerifyChecksum() throws PackMismatchException {
		// ComputedReverseIndex doesn't have a file containing a checksum.
		reverseIdx.verifyPackChecksum(null);
	}

	@Test
	public void testNonUniformBucketOffsets() throws Exception {
		List<PackedObjectInfo> objs = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			byte[] raw = new byte[20];
			raw[0] = (byte) (i * 37);
			raw[1] = (byte) i;
			objs.add(new PackedObjectInfo(ObjectId.fromRaw(raw)));
		}
		Collections.sort(objs);
		// Force 99 small offsets into bucket 0 in scrambled order relative to
		// SHA-1 order, plus one huge offset in the last bucket.
		for (int i = 0; i < 99; i++) {
			objs.get(i).setOffset(((i * 53) % 99 + 1) * 100L);
		}
		objs.get(99).setOffset(1L << 33);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BasePackIndexWriter.createVersion(out, 2).write(objs, new byte[20]);
		PackIndex skewedIdx = PackIndex
				.read(new ByteArrayInputStream(out.toByteArray()));
		PackReverseIndex skewedRev = PackReverseIndexFactory
				.computeFromIndex(skewedIdx);

		List<PackedObjectInfo> byOffset = new ArrayList<>(objs);
		byOffset.sort(Comparator.comparingLong(PackedObjectInfo::getOffset));
		long maxOffset = (1L << 33) + 100;
		for (int pos = 0; pos < byOffset.size(); pos++) {
			PackedObjectInfo expected = byOffset.get(pos);
			long offset = expected.getOffset();
			assertEquals(expected, skewedRev.findObject(offset));
			assertEquals(pos, skewedRev.findPosition(offset));
			assertEquals(expected, skewedRev.findObjectByPosition(pos));
			long nextExpected = pos + 1 < byOffset.size()
					? byOffset.get(pos + 1).getOffset()
					: maxOffset;
			assertEquals(nextExpected,
					skewedRev.findNextOffset(offset, maxOffset));
		}
	}

	private long findFirstOffset() {
		long min = Long.MAX_VALUE;
		for (MutableEntry me : idx)
			min = Math.min(min, me.getOffset());
		return min;
	}
}
