/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.util.Stats;
import org.junit.Test;

public class StatsTest {
	@Test
	public void testStatsTrivial() {
		Stats s = new Stats();
		s.add(1);
		s.add(1);
		s.add(1);
		assertEquals(3, s.count());
		assertEquals(1.0, s.min(), 1E-6);
		assertEquals(1.0, s.max(), 1E-6);
		assertEquals(1.0, s.avg(), 1E-6);
		assertEquals(0.0, s.var(), 1E-6);
		assertEquals(0.0, s.stddev(), 1E-6);
	}

	@Test
	public void testStats() {
		Stats s = new Stats();
		s.add(1);
		s.add(2);
		s.add(3);
		s.add(4);
		assertEquals(4, s.count());
		assertEquals(1.0, s.min(), 1E-6);
		assertEquals(4.0, s.max(), 1E-6);
		assertEquals(2.5, s.avg(), 1E-6);
		assertEquals(1.666667, s.var(), 1E-6);
		assertEquals(1.290994, s.stddev(), 1E-6);
	}

	@Test
	/**
	 * see
	 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Example
	 */
	public void testStatsCancellationExample1() {
		Stats s = new Stats();
		s.add(1E8 + 4);
		s.add(1E8 + 7);
		s.add(1E8 + 13);
		s.add(1E8 + 16);
		assertEquals(4, s.count());
		assertEquals(1E8 + 4, s.min(), 1E-6);
		assertEquals(1E8 + 16, s.max(), 1E-6);
		assertEquals(1E8 + 10, s.avg(), 1E-6);
		assertEquals(30, s.var(), 1E-6);
		assertEquals(5.477226, s.stddev(), 1E-6);
	}

	@Test
	/**
	 * see
	 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Example
	 */
	public void testStatsCancellationExample2() {
		Stats s = new Stats();
		s.add(1E9 + 4);
		s.add(1E9 + 7);
		s.add(1E9 + 13);
		s.add(1E9 + 16);
		assertEquals(4, s.count());
		assertEquals(1E9 + 4, s.min(), 1E-6);
		assertEquals(1E9 + 16, s.max(), 1E-6);
		assertEquals(1E9 + 10, s.avg(), 1E-6);
		assertEquals(30, s.var(), 1E-6);
		assertEquals(5.477226, s.stddev(), 1E-6);
	}

	@Test
	public void testNoValues() {
		Stats s = new Stats();
		assertTrue(Double.isNaN(s.var()));
		assertTrue(Double.isNaN(s.stddev()));
		assertTrue(Double.isNaN(s.avg()));
		assertTrue(Double.isNaN(s.min()));
		assertTrue(Double.isNaN(s.max()));
		s.add(42.3);
		assertTrue(Double.isNaN(s.var()));
		assertTrue(Double.isNaN(s.stddev()));
		assertEquals(42.3, s.avg(), 1E-6);
		assertEquals(42.3, s.max(), 1E-6);
		assertEquals(42.3, s.min(), 1E-6);
		s.add(42.3);
		assertEquals(0, s.var(), 1E-6);
		assertEquals(0, s.stddev(), 1E-6);
	}
}
