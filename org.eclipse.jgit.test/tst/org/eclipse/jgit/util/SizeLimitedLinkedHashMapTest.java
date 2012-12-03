/*
 * Copyright (C) 2013, Anders Martinsson
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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class SizeLimitedLinkedHashMapTest {
	private SizeLimitedLinkedHashMap<String, Integer> map = new SizeLimitedLinkedHashMap<String, Integer>();

	@Before
	public void clearMap() {
		map.clear();
	}

	@Test
	public void sizeLimitZero() {
		map.setSizeLimit(0);
		map.put("a", new Integer(1));
		assertEquals(0, map.size());
	}

	@Test
	public void sizeLimitOne() {
		map.setSizeLimit(1);

		map.put("a", new Integer(1));
		assertEquals(1, map.size());
		assertTrue(map.containsKey("a"));

		map.put("b", new Integer(2));
		assertEquals(1, map.size());
		assertTrue(map.containsKey("b"));
	}

	@Test
	public void lowerSizeLimit() {
		map.put("a", new Integer(1));
		map.put("b", new Integer(2));
		map.put("c", new Integer(3));
		map.put("d", new Integer(4));
		map.put("e", new Integer(5));
		assertEquals(5, map.size());

		map.setSizeLimit(4);
		assertEquals(4, map.size());
		assertFalse(map.containsKey("a"));

		map.setSizeLimit(2);
		assertEquals(2, map.size());
		assertTrue(map.containsKey("e"));
		assertTrue(map.containsKey("d"));
	}

	@Test
	public void getSizeLimit() {
		map.setSizeLimit(5);
		assertEquals(5, map.getSizeLimit());
	}

	@Test
	public void negativeSizeLimit() {
		map.setSizeLimit(-10);
		map.put("a", new Integer(1));
		assertEquals(0, map.size());

		map.setSizeLimit(10);
		map.put("a", new Integer(1));
		assertEquals(1, map.size());

		map.setSizeLimit(-10);
		assertEquals(0, map.size());
	}
}
