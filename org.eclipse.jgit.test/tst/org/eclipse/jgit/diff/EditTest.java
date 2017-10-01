/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EditTest {
	@Test
	public void testCreate() {
		final Edit e = new Edit(1, 2, 3, 4);
		assertEquals(1, e.getBeginA());
		assertEquals(2, e.getEndA());
		assertEquals(3, e.getBeginB());
		assertEquals(4, e.getEndB());
	}

	@Test
	public void testCreateEmpty() {
		final Edit e = new Edit(1, 3);
		assertEquals(1, e.getBeginA());
		assertEquals(1, e.getEndA());
		assertEquals(3, e.getBeginB());
		assertEquals(3, e.getEndB());
		assertTrue("is empty", e.isEmpty());
		assertSame(Edit.Type.EMPTY, e.getType());
	}

	@Test
	public void testSwap() {
		final Edit e = new Edit(1, 2, 3, 4);
		e.swap();
		assertEquals(3, e.getBeginA());
		assertEquals(4, e.getEndA());
		assertEquals(1, e.getBeginB());
		assertEquals(2, e.getEndB());
	}

	@Test
	public void testType_Insert() {
		final Edit e = new Edit(1, 1, 1, 2);
		assertSame(Edit.Type.INSERT, e.getType());
		assertFalse("not empty", e.isEmpty());
		assertEquals(0, e.getLengthA());
		assertEquals(1, e.getLengthB());
	}

	@Test
	public void testType_Delete() {
		final Edit e = new Edit(1, 2, 1, 1);
		assertSame(Edit.Type.DELETE, e.getType());
		assertFalse("not empty", e.isEmpty());
		assertEquals(1, e.getLengthA());
		assertEquals(0, e.getLengthB());
	}

	@Test
	public void testType_Replace() {
		final Edit e = new Edit(1, 2, 1, 4);
		assertSame(Edit.Type.REPLACE, e.getType());
		assertFalse("not empty", e.isEmpty());
		assertEquals(1, e.getLengthA());
		assertEquals(3, e.getLengthB());
	}

	@Test
	public void testType_Empty() {
		final Edit e = new Edit(1, 1, 2, 2);
		assertSame(Edit.Type.EMPTY, e.getType());
		assertSame(Edit.Type.EMPTY, new Edit(1, 2).getType());
		assertTrue("is empty", e.isEmpty());
		assertEquals(0, e.getLengthA());
		assertEquals(0, e.getLengthB());
	}

	@Test
	public void testToString() {
		final Edit e = new Edit(1, 2, 1, 4);
		assertEquals("REPLACE(1-2,1-4)", e.toString());
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testEquals1() {
		final Edit e1 = new Edit(1, 2, 3, 4);
		final Edit e2 = new Edit(1, 2, 3, 4);

		assertEquals(e1, e1);
		assertEquals(e2, e1);
		assertEquals(e1, e2);
		assertEquals(e1.hashCode(), e2.hashCode());
		assertFalse(e1.equals(""));
	}

	@Test
	public void testNotEquals1() {
		assertFalse(new Edit(1, 2, 3, 4).equals(new Edit(0, 2, 3, 4)));
	}

	@Test
	public void testNotEquals2() {
		assertFalse(new Edit(1, 2, 3, 4).equals(new Edit(1, 0, 3, 4)));
	}

	@Test
	public void testNotEquals3() {
		assertFalse(new Edit(1, 2, 3, 4).equals(new Edit(1, 2, 0, 4)));
	}

	@Test
	public void testNotEquals4() {
		assertFalse(new Edit(1, 2, 3, 4).equals(new Edit(1, 2, 3, 0)));
	}

	@Test
	public void testExtendA() {
		final Edit e = new Edit(1, 2, 1, 1);

		e.extendA();
		assertEquals(new Edit(1, 3, 1, 1), e);

		e.extendA();
		assertEquals(new Edit(1, 4, 1, 1), e);
	}

	@Test
	public void testExtendB() {
		final Edit e = new Edit(1, 2, 1, 1);

		e.extendB();
		assertEquals(new Edit(1, 2, 1, 2), e);

		e.extendB();
		assertEquals(new Edit(1, 2, 1, 3), e);
	}

	@Test
	public void testBeforeAfterCuts() {
		final Edit whole = new Edit(1, 8, 2, 9);
		final Edit mid = new Edit(4, 5, 3, 6);

		assertEquals(new Edit(1, 4, 2, 3), whole.before(mid));
		assertEquals(new Edit(5, 8, 6, 9), whole.after(mid));
	}
}
