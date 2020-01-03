/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.junit.Test;

public class LeafBucketTest {
	@Test
	public void testEmpty() {
		LeafBucket b = new LeafBucket(0);
		assertNull(b.getNote(id(0x00), null));
		assertNull(b.getNote(id(0x01), null));
		assertNull(b.getNote(id(0xfe), null));
	}

	@Test
	public void testParseFive() {
		LeafBucket b = new LeafBucket(0);

		b.parseOneEntry(id(0x11), id(0x81));
		b.parseOneEntry(id(0x22), id(0x82));
		b.parseOneEntry(id(0x33), id(0x83));
		b.parseOneEntry(id(0x44), id(0x84));
		b.parseOneEntry(id(0x55), id(0x85));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testSetFive_InOrder() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));
		assertSame(b, b.set(id(0x55), id(0x85), null));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testSetFive_ReverseOrder() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x55), id(0x85), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x11), id(0x81), null));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testSetFive_MixedOrder() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x55), id(0x85), null));

		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testSet_Replace() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());

		assertSame(b, b.set(id(0x11), id(0x01), null));
		assertEquals(id(0x01), b.getNote(id(0x11), null).getData());
	}

	@Test
	public void testRemoveMissingNote() throws IOException {
		LeafBucket b = new LeafBucket(0);
		assertNull(b.getNote(id(0x11), null));
		assertSame(b, b.set(id(0x11), null, null));
		assertNull(b.getNote(id(0x11), null));
	}

	@Test
	public void testRemoveFirst() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));
		assertSame(b, b.set(id(0x55), id(0x85), null));

		assertSame(b, b.set(id(0x11), null, null));

		assertNull(b.getNote(id(0x01), null));
		assertNull(b.getNote(id(0x11), null));
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testRemoveMiddle() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));
		assertSame(b, b.set(id(0x55), id(0x85), null));

		assertSame(b, b.set(id(0x33), null, null));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertNull(b.getNote(id(0x33), null));
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertEquals(id(0x85), b.getNote(id(0x55), null).getData());
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testRemoveLast() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertSame(b, b.set(id(0x22), id(0x82), null));
		assertSame(b, b.set(id(0x33), id(0x83), null));
		assertSame(b, b.set(id(0x44), id(0x84), null));
		assertSame(b, b.set(id(0x55), id(0x85), null));

		assertSame(b, b.set(id(0x55), null, null));

		assertNull(b.getNote(id(0x01), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());
		assertEquals(id(0x82), b.getNote(id(0x22), null).getData());
		assertEquals(id(0x83), b.getNote(id(0x33), null).getData());
		assertEquals(id(0x84), b.getNote(id(0x44), null).getData());
		assertNull(b.getNote(id(0x55), null));
		assertNull(b.getNote(id(0x66), null));
	}

	@Test
	public void testRemoveMakesEmpty() throws IOException {
		LeafBucket b = new LeafBucket(0);

		assertSame(b, b.set(id(0x11), id(0x81), null));
		assertEquals(id(0x81), b.getNote(id(0x11), null).getData());

		assertNull(b.set(id(0x11), null, null));
		assertNull(b.getNote(id(0x11), null));
	}

	private static AnyObjectId id(int first) {
		MutableObjectId id = new MutableObjectId();
		id.setByte(1, first);
		return id;
	}
}
