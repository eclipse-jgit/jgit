/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Assert;
import org.junit.Test;

public class CompactInt3ArrayTest {

	private static final byte[] DATA = { 0x00, 0x00, 0x00, // 0
			0x00, 0x00, 0x05, // 5
			0x00, 0x00, 0x0a, // 10
			0x00, 0x00, 0x0f, // 15
			0x00, 0x00, 0x14, // 20
			0x00, 0x00, 0x19, // 25
			(byte) 0xff, 0x00, 0x00, // Uint with MSB=1
			(byte) 0xff, (byte) 0xff, (byte) 0xff, // MAX
	};

	private static final CompactInt3Array three = new CompactInt3Array(DATA);

	@Test
	public void threeBytes_size() {
		assertEquals(8, three.size());
	}

	@Test
	public void threeBytes_get() {
		assertEquals(0, three.get(0));
		assertEquals(5, three.get(1));
		assertEquals(10, three.get(2));
		assertEquals(15, three.get(3));
		assertEquals(20, three.get(4));
		assertEquals(25, three.get(5));
		assertEquals(0xff0000, three.get(6));
		assertEquals(0xffffff, three.get(7));
		assertThrows(IndexOutOfBoundsException.class, () -> three.get(9));
	}

	@Test
	public void threeBytes_getLastValue() {
		assertEquals(0xffffff, three.getLastValue());
	}

	@Test
	public void threeBytes_find() {
		assertEquals(0, three.find(0));
		assertEquals(1, three.find(5));
		assertEquals(2, three.find(10));
		assertEquals(3, three.find(15));
		assertEquals(4, three.find(20));
		assertEquals(5, three.find(25));
		assertEquals(6, three.find(0xff0000));
		assertEquals(7, three.find(0xffffff));
		assertThrows(IllegalArgumentException.class,
				() -> three.find(Integer.MAX_VALUE));
	}

	@Test
	public void empty() {
		Assert.assertTrue(CompactInt3Array.EMPTY.isEmpty());
		assertEquals(0, CompactInt3Array.EMPTY.size());
		assertThrows(IndexOutOfBoundsException.class,
				() -> CompactInt3Array.EMPTY.getLastValue());
		assertThrows(IndexOutOfBoundsException.class,
				() -> CompactInt3Array.EMPTY.get(0));
	}
}
