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

public class UInt24ArrayTest {

	private static final byte[] DATA = { 0x00, 0x00, 0x00, // 0
			0x00, 0x00, 0x05, // 5
			0x00, 0x00, 0x0a, // 10
			0x00, 0x00, 0x0f, // 15
			0x00, 0x00, 0x14, // 20
			0x00, 0x00, 0x19, // 25
			(byte) 0xff, 0x00, 0x00, // Uint with MSB=1
			(byte) 0xff, (byte) 0xff, (byte) 0xff, // MAX
	};

	private static final UInt24Array asArray = new UInt24Array(DATA);

	@Test
	public void uInt24Array_size() {
		assertEquals(8, asArray.size());
	}

	@Test
	public void uInt24Array_get() {
		assertEquals(0, asArray.get(0));
		assertEquals(5, asArray.get(1));
		assertEquals(10, asArray.get(2));
		assertEquals(15, asArray.get(3));
		assertEquals(20, asArray.get(4));
		assertEquals(25, asArray.get(5));
		assertEquals(0xff0000, asArray.get(6));
		assertEquals(0xffffff, asArray.get(7));
		assertThrows(IndexOutOfBoundsException.class, () -> asArray.get(9));
	}

	@Test
	public void uInt24Array_getLastValue() {
		assertEquals(0xffffff, asArray.getLastValue());
	}

	@Test
	public void uInt24Array_find() {
		assertEquals(0, asArray.binarySearch(0));
		assertEquals(1, asArray.binarySearch(5));
		assertEquals(2, asArray.binarySearch(10));
		assertEquals(3, asArray.binarySearch(15));
		assertEquals(4, asArray.binarySearch(20));
		assertEquals(5, asArray.binarySearch(25));
		assertEquals(6, asArray.binarySearch(0xff0000));
		assertEquals(7, asArray.binarySearch(0xffffff));
		assertThrows(IllegalArgumentException.class,
				() -> asArray.binarySearch(Integer.MAX_VALUE));
	}

	@Test
	public void uInt24Array_empty() {
		Assert.assertTrue(UInt24Array.EMPTY.isEmpty());
		assertEquals(0, UInt24Array.EMPTY.size());
		assertEquals(-1, UInt24Array.EMPTY.binarySearch(1));
		assertThrows(IndexOutOfBoundsException.class,
				() -> UInt24Array.EMPTY.getLastValue());
		assertThrows(IndexOutOfBoundsException.class,
				() -> UInt24Array.EMPTY.get(0));
	}
}
