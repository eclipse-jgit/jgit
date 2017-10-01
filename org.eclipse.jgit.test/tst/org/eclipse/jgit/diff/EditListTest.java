/*
 * Copyright (C) 2009, Google Inc.
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

import java.util.Iterator;

import org.junit.Test;

public class EditListTest {
	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testEmpty() {
		final EditList l = new EditList();
		assertEquals(0, l.size());
		assertTrue(l.isEmpty());
		assertEquals("EditList[]", l.toString());

		assertEquals(l, l);
		assertEquals(new EditList(), l);
		assertFalse(l.equals(""));
		assertEquals(l.hashCode(), new EditList().hashCode());
	}

	@Test
	public void testAddOne() {
		final Edit e = new Edit(1, 2, 1, 1);
		final EditList l = new EditList();
		l.add(e);
		assertEquals(1, l.size());
		assertFalse(l.isEmpty());
		assertSame(e, l.get(0));
		assertSame(e, l.iterator().next());

		assertEquals(l, l);
		assertFalse(l.equals(new EditList()));

		final EditList l2 = new EditList();
		l2.add(e);
		assertEquals(l2, l);
		assertEquals(l, l2);
		assertEquals(l.hashCode(), l2.hashCode());
	}

	@Test
	public void testAddTwo() {
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
		assertFalse(l.equals(new EditList()));

		final EditList l2 = new EditList();
		l2.add(e1);
		l2.add(e2);
		assertEquals(l2, l);
		assertEquals(l, l2);
		assertEquals(l.hashCode(), l2.hashCode());
	}

	@Test
	public void testSet() {
		final Edit e1 = new Edit(1, 2, 1, 1);
		final Edit e2 = new Edit(3, 4, 3, 3);
		final EditList l = new EditList();
		l.add(e1);
		assertSame(e1, l.get(0));
		assertSame(e1, l.set(0, e2));
		assertSame(e2, l.get(0));
	}

	@Test
	public void testRemove() {
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
