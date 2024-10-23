/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the set of serial number ranges.
 */
public class SerialRangeSetTest {

	private SerialRangeSet ranges = new SerialRangeSet();

	@Test
	public void testInsertSimple() {
		ranges.add(1);
		ranges.add(3);
		ranges.add(5);
		assertEquals(3, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertFalse(ranges.contains(2));
		assertTrue(ranges.contains(3));
		assertFalse(ranges.contains(4));
		assertTrue(ranges.contains(5));
		assertFalse(ranges.contains(6));
	}

	@Test
	public void testInsertSimpleRanges() {
		ranges.add(1, 2);
		ranges.add(4, 5);
		ranges.add(7, 8);
		assertEquals(3, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertTrue(ranges.contains(2));
		assertFalse(ranges.contains(3));
		assertTrue(ranges.contains(4));
		assertTrue(ranges.contains(5));
		assertFalse(ranges.contains(6));
		assertTrue(ranges.contains(7));
		assertTrue(ranges.contains(8));
		assertFalse(ranges.contains(9));
	}

	@Test
	public void testInsertCoalesce() {
		ranges.add(5);
		ranges.add(1);
		ranges.add(2);
		ranges.add(4);
		ranges.add(7);
		ranges.add(3);
		assertEquals(2, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertTrue(ranges.contains(2));
		assertTrue(ranges.contains(3));
		assertTrue(ranges.contains(4));
		assertTrue(ranges.contains(5));
		assertFalse(ranges.contains(6));
		assertTrue(ranges.contains(7));
		assertFalse(ranges.contains(8));
	}

	@Test
	public void testInsertOverlap() {
		ranges.add(1, 3);
		ranges.add(6);
		ranges.add(2, 5);
		assertEquals(1, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertTrue(ranges.contains(2));
		assertTrue(ranges.contains(3));
		assertTrue(ranges.contains(4));
		assertTrue(ranges.contains(5));
		assertTrue(ranges.contains(6));
		assertFalse(ranges.contains(7));
	}

	@Test
	public void testInsertOverlapMultiple() {
		ranges.add(1, 3);
		ranges.add(5, 6);
		ranges.add(8);
		ranges.add(2, 5);
		assertEquals(2, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertTrue(ranges.contains(2));
		assertTrue(ranges.contains(3));
		assertTrue(ranges.contains(4));
		assertTrue(ranges.contains(5));
		assertTrue(ranges.contains(6));
		assertFalse(ranges.contains(7));
		assertTrue(ranges.contains(8));
		assertFalse(ranges.contains(9));
	}

	@Test
	public void testInsertOverlapTotal() {
		ranges.add(1, 3);
		ranges.add(2, 3);
		assertEquals(1, ranges.size());
		assertFalse(ranges.contains(0));
		assertTrue(ranges.contains(1));
		assertTrue(ranges.contains(2));
		assertTrue(ranges.contains(3));
		assertFalse(ranges.contains(4));
	}
}
