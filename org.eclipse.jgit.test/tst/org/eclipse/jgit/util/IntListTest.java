/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class IntListTest {
	@Test
	public void testEmpty_DefaultCapacity() {
		final IntList i = new IntList();
		assertEquals(0, i.size());
		try {
			i.get(0);
			fail("Accepted 0 index on empty list");
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testEmpty_SpecificCapacity() {
		final IntList i = new IntList(5);
		assertEquals(0, i.size());
		try {
			i.get(0);
			fail("Accepted 0 index on empty list");
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testAdd_SmallGroup() {
		final IntList i = new IntList();
		final int n = 5;
		for (int v = 0; v < n; v++)
			i.add(10 + v);
		assertEquals(n, i.size());

		for (int v = 0; v < n; v++)
			assertEquals(10 + v, i.get(v));
		try {
			i.get(n);
			fail("Accepted out of bound index on list");
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testAdd_ZeroCapacity() {
		final IntList i = new IntList(0);
		assertEquals(0, i.size());
		i.add(1);
		assertEquals(1, i.get(0));
	}

	@Test
	public void testAdd_LargeGroup() {
		final IntList i = new IntList();
		final int n = 500;
		for (int v = 0; v < n; v++)
			i.add(10 + v);
		assertEquals(n, i.size());

		for (int v = 0; v < n; v++)
			assertEquals(10 + v, i.get(v));
		try {
			i.get(n);
			fail("Accepted out of bound index on list");
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testFillTo0() {
		final IntList i = new IntList();
		i.fillTo(0, Integer.MIN_VALUE);
		assertEquals(0, i.size());
	}

	@Test
	public void testFillTo1() {
		final IntList i = new IntList();
		i.fillTo(1, Integer.MIN_VALUE);
		assertEquals(1, i.size());
		i.add(0);
		assertEquals(Integer.MIN_VALUE, i.get(0));
		assertEquals(0, i.get(1));
	}

	@Test
	public void testFillTo100() {
		final IntList i = new IntList();
		i.fillTo(100, Integer.MIN_VALUE);
		assertEquals(100, i.size());
		i.add(3);
		assertEquals(Integer.MIN_VALUE, i.get(99));
		assertEquals(3, i.get(100));
	}

	@Test
	public void testClear() {
		final IntList i = new IntList();
		final int n = 5;
		for (int v = 0; v < n; v++)
			i.add(10 + v);
		assertEquals(n, i.size());

		i.clear();
		assertEquals(0, i.size());
		try {
			i.get(0);
			fail("Accepted 0 index on empty list");
		} catch (ArrayIndexOutOfBoundsException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testSet() {
		final IntList i = new IntList();
		i.add(1);
		assertEquals(1, i.size());
		assertEquals(1, i.get(0));

		i.set(0, 5);
		assertEquals(5, i.get(0));

		try {
			i.set(5, 5);
			fail("accepted set of 5 beyond end of list");
		} catch (ArrayIndexOutOfBoundsException e){
			assertTrue(true);
		}

		i.set(1, 2);
		assertEquals(2, i.size());
		assertEquals(2, i.get(1));
	}

	@Test
	public void testContains() {
		IntList i = new IntList();
		i.add(1);
		i.add(4);
		assertTrue(i.contains(1));
		assertTrue(i.contains(4));
		assertFalse(i.contains(2));
	}

	@Test
	public void testToString() {
		final IntList i = new IntList();
		i.add(1);
		assertEquals("[1]", i.toString());
		i.add(13);
		i.add(5);
		assertEquals("[1, 13, 5]", i.toString());
	}

}
