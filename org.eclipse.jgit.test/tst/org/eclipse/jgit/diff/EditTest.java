/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Johannes E. Schindelin <johannes.schindelin@gmx.de> and others
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

import org.junit.jupiter.api.Test;

public class EditTest {
	@Test
	void testCreate() {
		final Edit e = new Edit(1, 2, 3, 4);
		assertEquals(1, e.getBeginA());
		assertEquals(2, e.getEndA());
		assertEquals(3, e.getBeginB());
		assertEquals(4, e.getEndB());
	}

	@Test
	void testCreateEmpty() {
		final Edit e = new Edit(1, 3);
		assertEquals(1, e.getBeginA());
		assertEquals(1, e.getEndA());
		assertEquals(3, e.getBeginB());
		assertEquals(3, e.getEndB());
		assertTrue(e.isEmpty(), "is empty");
		assertSame(Edit.Type.EMPTY, e.getType());
	}

	@Test
	void testSwap() {
		final Edit e = new Edit(1, 2, 3, 4);
		e.swap();
		assertEquals(3, e.getBeginA());
		assertEquals(4, e.getEndA());
		assertEquals(1, e.getBeginB());
		assertEquals(2, e.getEndB());
	}

	@Test
	void testType_Insert() {
		final Edit e = new Edit(1, 1, 1, 2);
		assertSame(Edit.Type.INSERT, e.getType());
		assertFalse(e.isEmpty(), "not empty");
		assertEquals(0, e.getLengthA());
		assertEquals(1, e.getLengthB());
	}

	@Test
	void testType_Delete() {
		final Edit e = new Edit(1, 2, 1, 1);
		assertSame(Edit.Type.DELETE, e.getType());
		assertFalse(e.isEmpty(), "not empty");
		assertEquals(1, e.getLengthA());
		assertEquals(0, e.getLengthB());
	}

	@Test
	void testType_Replace() {
		final Edit e = new Edit(1, 2, 1, 4);
		assertSame(Edit.Type.REPLACE, e.getType());
		assertFalse(e.isEmpty(), "not empty");
		assertEquals(1, e.getLengthA());
		assertEquals(3, e.getLengthB());
	}

	@Test
	void testType_Empty() {
		final Edit e = new Edit(1, 1, 2, 2);
		assertSame(Edit.Type.EMPTY, e.getType());
		assertSame(Edit.Type.EMPTY, new Edit(1, 2).getType());
		assertTrue(e.isEmpty(), "is empty");
		assertEquals(0, e.getLengthA());
		assertEquals(0, e.getLengthB());
	}

	@Test
	void testToString() {
		final Edit e = new Edit(1, 2, 1, 4);
		assertEquals("REPLACE(1-2,1-4)", e.toString());
	}

	@Test
	void testEquals1() {
		final Edit e1 = new Edit(1, 2, 3, 4);
		final Edit e2 = new Edit(1, 2, 3, 4);

		assertEquals(e1, e1);
		assertEquals(e2, e1);
		assertEquals(e1, e2);
		assertEquals(e1.hashCode(), e2.hashCode());
		assertNotEquals(e1, "");
	}

	@Test
	void testNotEquals1() {
		assertNotEquals(new Edit(1, 2, 3, 4), new Edit(0, 2, 3, 4));
	}

	@Test
	void testNotEquals2() {
		assertNotEquals(new Edit(1, 2, 3, 4), new Edit(1, 0, 3, 4));
	}

	@Test
	void testNotEquals3() {
		assertNotEquals(new Edit(1, 2, 3, 4), new Edit(1, 2, 0, 4));
	}

	@Test
	void testNotEquals4() {
		assertNotEquals(new Edit(1, 2, 3, 4), new Edit(1, 2, 3, 0));
	}

	@Test
	void testExtendA() {
		final Edit e = new Edit(1, 2, 1, 1);

		e.extendA();
		assertEquals(new Edit(1, 3, 1, 1), e);

		e.extendA();
		assertEquals(new Edit(1, 4, 1, 1), e);
	}

	@Test
	void testExtendB() {
		final Edit e = new Edit(1, 2, 1, 1);

		e.extendB();
		assertEquals(new Edit(1, 2, 1, 2), e);

		e.extendB();
		assertEquals(new Edit(1, 2, 1, 3), e);
	}

	@Test
	void testBeforeAfterCuts() {
		final Edit whole = new Edit(1, 8, 2, 9);
		final Edit mid = new Edit(4, 5, 3, 6);

		assertEquals(new Edit(1, 4, 2, 3), whole.before(mid));
		assertEquals(new Edit(5, 8, 6, 9), whole.after(mid));
	}
}
