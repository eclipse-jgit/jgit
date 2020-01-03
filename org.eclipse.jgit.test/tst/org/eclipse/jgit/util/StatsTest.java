/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
