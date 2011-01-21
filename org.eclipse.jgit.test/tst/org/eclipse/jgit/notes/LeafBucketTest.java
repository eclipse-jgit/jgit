/*
 * Copyright (C) 2010, Google Inc.
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
