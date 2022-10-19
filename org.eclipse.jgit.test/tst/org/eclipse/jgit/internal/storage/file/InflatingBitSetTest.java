/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.googlecode.javaewah.EWAHCompressedBitmap;

public class InflatingBitSetTest {

	@Test
	void testMaybeContains() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(63);
		ecb.set(64);
		ecb.set(128);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertTrue(ibs.maybeContains(0));
		assertFalse(ibs.contains(0)); // Advance
		assertFalse(ibs.maybeContains(0));
		assertTrue(ibs.maybeContains(63));
		assertTrue(ibs.maybeContains(64));
		assertTrue(ibs.maybeContains(65));
		assertFalse(ibs.maybeContains(129));
	}

	@Test
	void testContainsMany() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(64);
		ecb.set(65);
		ecb.set(1024);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertFalse(ibs.contains(0));
		assertTrue(ibs.contains(64));
		assertTrue(ibs.contains(65));
		assertFalse(ibs.contains(66));
		assertTrue(ibs.contains(1024));
		assertFalse(ibs.contains(1025));
	}

	@Test
	void testContainsOne() {
		EWAHCompressedBitmap ecb = new EWAHCompressedBitmap();
		ecb.set(64);

		InflatingBitSet ibs = new InflatingBitSet(ecb);
		assertTrue(ibs.contains(64));
		assertTrue(ibs.contains(64));
		assertFalse(ibs.contains(65));
		assertFalse(ibs.contains(63));
	}

	@Test
	void testContainsEmpty() {
		InflatingBitSet ibs = new InflatingBitSet(new EWAHCompressedBitmap());
		assertFalse(ibs.maybeContains(0));
		assertFalse(ibs.contains(0));
	}
}
