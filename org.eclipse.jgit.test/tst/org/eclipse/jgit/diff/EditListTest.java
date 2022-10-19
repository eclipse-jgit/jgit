/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

public class EditListTest {
	@Test
	void testEmpty() {
		final EditList l = new EditList();
		assertEquals(0, l.size());
		assertTrue(l.isEmpty());
		assertEquals("EditList[]", l.toString());

		assertEquals(l, l);
		assertEquals(new EditList(), l);
		assertNotEquals(l, "");
		assertEquals(l.hashCode(), new EditList().hashCode());
	}

	@Test
	void testAddOne() {
		final Edit e = new Edit(1, 2, 1, 1);
		final EditList l = new EditList();
		l.add(e);
		assertEquals(1, l.size());
		assertFalse(l.isEmpty());
		assertSame(e, l.get(0));
		assertSame(e, l.iterator().next());

		assertEquals(l, l);
		assertNotEquals(l, new EditList());

		final EditList l2 = new EditList();
		l2.add(e);
		assertEquals(l2, l);
		assertEquals(l, l2);
		assertEquals(l.hashCode(), l2.hashCode());
	}

	@Test
	void testAddTwo() {
		final Edit e1 = new Edit(1, 2, 1, 1);
		final Edit e2 = new Edit(8, 8, 8, 12);
		final EditList l = new EditList();
		l.add(e1);
		l.add(e2);
		assertEquals(2, l.size());
		assertSame(e1, l.get(0));
		assertSame(e2, l.get(1));

		final Iterator<Edit> i = l.iterator();
		assertSame(e1, i.next());
		assertSame(e2, i.next());

		assertEquals(l, l);
		assertNotEquals(l, new EditList());

		final EditList l2 = new EditList();
		l2.add(e1);
		l2.add(e2);
		assertEquals(l2, l);
		assertEquals(l, l2);
		assertEquals(l.hashCode(), l2.hashCode());
	}

	@Test
	void testSet() {
		final Edit e1 = new Edit(1, 2, 1, 1);
		final Edit e2 = new Edit(3, 4, 3, 3);
		final EditList l = new EditList();
		l.add(e1);
		assertSame(e1, l.get(0));
		assertSame(e1, l.set(0, e2));
		assertSame(e2, l.get(0));
	}

	@Test
	void testRemove() {
		final Edit e1 = new Edit(1, 2, 1, 1);
		final Edit e2 = new Edit(8, 8, 8, 12);
		final EditList l = new EditList();
		l.add(e1);
		l.add(e2);
		l.remove(e1);
		assertEquals(1, l.size());
		assertSame(e2, l.get(0));
	}
}
