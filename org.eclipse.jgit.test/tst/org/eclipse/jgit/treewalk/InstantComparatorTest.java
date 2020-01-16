/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Test;

public class InstantComparatorTest {

	private final InstantComparator cmp = new InstantComparator();

	@Test
	public void compareNow() {
		Instant now = Instant.now();
		assertEquals(0, cmp.compare(now, now));
		assertEquals(0, cmp.compare(now, now, true));
	}

	@Test
	public void compareSeconds() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond());
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		assertEquals(0, cmp.compare(t, s));
		assertEquals(0, cmp.compare(t, t));
		assertEquals(0, cmp.compare(s, t));
	}

	@Test
	public void compareSecondsOnly() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 987654321);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		assertEquals(0, cmp.compare(t, s, true));
		assertEquals(0, cmp.compare(t, t, true));
		assertEquals(0, cmp.compare(s, t, true));
	}

	@Test
	public void compareSecondsUnequal() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond());
		Instant s = Instant.ofEpochSecond(now.getEpochSecond() - 1L);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
	}

	@Test
	public void compareMillisEqual() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123000000);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, t));
		assertEquals(0, cmp.compare(t, s));
		s = Instant.ofEpochSecond(now.getEpochSecond(), 123456000);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, s));
		s = Instant.ofEpochSecond(now.getEpochSecond(), 123400000);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, s));
	}

	@Test
	public void compareMillisUnequal() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123000000);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 122000000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		t = Instant.ofEpochSecond(now.getEpochSecond(), 130000000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		t = Instant.ofEpochSecond(now.getEpochSecond(), 200000000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		s = Instant.ofEpochSecond(now.getEpochSecond() - 1L, 123000000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
	}

	@Test
	public void compareMicrosEqual() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123456000);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, s));
		s = Instant.ofEpochSecond(now.getEpochSecond(), 123456700);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, s));
	}

	@Test
	public void compareMicrosUnequal() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123456000);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123455000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		t = Instant.ofEpochSecond(now.getEpochSecond(), 123460000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		t = Instant.ofEpochSecond(now.getEpochSecond(), 123500000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		s = Instant.ofEpochSecond(now.getEpochSecond() - 1L, 123456000);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
	}

	@Test
	public void compareNanosEqual() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		assertEquals(0, cmp.compare(s, t));
		assertEquals(0, cmp.compare(t, s));
	}

	@Test
	public void compareNanosUnequal() {
		Instant now = Instant.now();
		Instant t = Instant.ofEpochSecond(now.getEpochSecond(), 123456789);
		Instant s = Instant.ofEpochSecond(now.getEpochSecond(), 123456700);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		t = Instant.ofEpochSecond(now.getEpochSecond(), 123456800);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		s = Instant.ofEpochSecond(now.getEpochSecond() - 1L, 123456789);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
		s = Instant.ofEpochSecond(now.getEpochSecond(), 123456788);
		assertTrue(cmp.compare(s, t) < 0);
		assertTrue(cmp.compare(t, s) > 0);
	}
}
