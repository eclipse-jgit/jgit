/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;

public class ObjectIdSubclassMapTest {
	private MutableObjectId idBuf;

	private SubId id_1, id_2, id_3, id_a31, id_b31;

	@Before
	public void init() {
		idBuf = new MutableObjectId();
		id_1 = new SubId(id(1));
		id_2 = new SubId(id(2));
		id_3 = new SubId(id(3));
		id_a31 = new SubId(id(31));
		id_b31 = new SubId(id((1 << 8) + 31));
	}

	@Test
	public void testEmptyMap() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		assertTrue(m.isEmpty());
		assertEquals(0, m.size());

		Iterator<SubId> i = m.iterator();
		assertNotNull(i);
		assertFalse(i.hasNext());

		assertFalse(m.contains(id(1)));
	}

	@Test
	public void testAddGetAndContains() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		m.add(id_1);
		m.add(id_2);
		m.add(id_3);
		m.add(id_a31);
		m.add(id_b31);
		assertFalse(m.isEmpty());
		assertEquals(5, m.size());

		assertSame(id_1, m.get(id_1));
		assertSame(id_1, m.get(id(1)));
		assertSame(id_1, m.get(id(1).copy()));
		assertSame(id_2, m.get(id(2).copy()));
		assertSame(id_3, m.get(id(3).copy()));
		assertSame(id_a31, m.get(id(31).copy()));
		assertSame(id_b31, m.get(id_b31.copy()));

		assertTrue(m.contains(id_1));
	}

	@Test
	public void testClear() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();

		m.add(id_1);
		assertSame(id_1, m.get(id_1));

		m.clear();
		assertTrue(m.isEmpty());
		assertEquals(0, m.size());

		Iterator<SubId> i = m.iterator();
		assertNotNull(i);
		assertFalse(i.hasNext());

		assertFalse(m.contains(id(1)));
	}

	@Test
	public void testAddIfAbsent() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		m.add(id_1);

		assertSame(id_1, m.addIfAbsent(new SubId(id_1)));
		assertEquals(1, m.size());

		assertSame(id_2, m.addIfAbsent(id_2));
		assertEquals(2, m.size());
		assertSame(id_a31, m.addIfAbsent(id_a31));
		assertSame(id_b31, m.addIfAbsent(id_b31));

		assertSame(id_a31, m.addIfAbsent(new SubId(id_a31)));
		assertSame(id_b31, m.addIfAbsent(new SubId(id_b31)));
		assertEquals(4, m.size());
	}

	@Test
	public void testAddGrowsWithObjects() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		m.add(id_1);
		for (int i = 32; i < 8000; i++)
			m.add(new SubId(id(i)));
		assertEquals(8000 - 32 + 1, m.size());

		assertSame(id_1, m.get(id_1.copy()));
		for (int i = 32; i < 8000; i++)
			assertTrue(m.contains(id(i)));
	}

	@Test
	public void testAddIfAbsentGrowsWithObjects() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		m.add(id_1);
		for (int i = 32; i < 8000; i++)
			m.addIfAbsent(new SubId(id(i)));
		assertEquals(8000 - 32 + 1, m.size());

		assertSame(id_1, m.get(id_1.copy()));
		for (int i = 32; i < 8000; i++)
			assertTrue(m.contains(id(i)));
	}

	@Test
	public void testIterator() {
		ObjectIdSubclassMap<SubId> m = new ObjectIdSubclassMap<>();
		m.add(id_1);
		m.add(id_2);
		m.add(id_3);

		Iterator<SubId> i = m.iterator();
		assertTrue(i.hasNext());
		assertSame(id_1, i.next());
		assertTrue(i.hasNext());
		assertSame(id_2, i.next());
		assertTrue(i.hasNext());
		assertSame(id_3, i.next());

		assertFalse(i.hasNext());
		try {
			i.next();
			fail("did not fail on next with no next");
		} catch (NoSuchElementException expected) {
			// OK
		}

		i = m.iterator();
		assertSame(id_1, i.next());
		try {
			i.remove();
			fail("did not fail on remove");
		} catch (UnsupportedOperationException expected) {
			// OK
		}
	}

	private AnyObjectId id(int val) {
		// Using bytes 2 and 3 positions our value at the low end of idBuf.w1,
		// which is what ObjectIdSubclassMap uses for hashing. This makes
		// collisions likely, making collision testing easier.

		val <<= 1;

		idBuf.setByte(2, (val >>> 8) & 0xff);
		idBuf.setByte(3, val & 0xff);
		return idBuf;
	}

	private static class SubId extends ObjectId {
		SubId(AnyObjectId id) {
			super(id);
		}
	}
}
