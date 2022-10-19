/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class IntSetTest {
	@Test
	void testAdd() {
		IntSet s = new IntSet();

		assertTrue(s.add(1));
		assertFalse(s.add(1));

		for (int i = 2; i < 64; i++)
			assertTrue(s.add(i));
		for (int i = 2; i < 64; i++)
			assertFalse(s.add(i));

		assertTrue(s.add(-1));
		assertFalse(s.add(-1));

		assertTrue(s.add(-2));
		assertFalse(s.add(-2));

		assertTrue(s.add(128));
		assertFalse(s.add(128));

		assertFalse(s.add(1));
	}
}
