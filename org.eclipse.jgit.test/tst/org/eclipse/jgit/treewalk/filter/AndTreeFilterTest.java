/*
 * Copyright (C) 2024 Google Inc and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AndTreeFilterTest {
	@Test
	public void getPathsBestEffort_twoPathFilters_throws() {
		TreeFilter[] tf = new TreeFilter[] { PathFilter.create("a/b"),
				PathFilter.create("a") };
		assertThrows(UnsupportedOperationException.class,
				() -> AndTreeFilter.create(tf).getPathsBestEffort());
	}

	@Test
	public void getPathsBestEffort_threePathFilters_throws() {
		TreeFilter[] tf = new TreeFilter[] { PathFilter.create("a/b"),
				PathFilter.create("a"), PathFilter.create("b") };
		assertThrows(UnsupportedOperationException.class,
				() -> AndTreeFilter.create(tf).getPathsBestEffort());
	}

	@Test
	public void getPathsBestEffort_noPathFilters_empty() {
		TreeFilter[] tf = new TreeFilter[] { TreeFilter.ANY_DIFF,
				TreeFilter.ALL };
		assertTrue(AndTreeFilter.create(tf).getPathsBestEffort().isEmpty());
	}

	@Test
	public void getPathsBestEffort_onePathFilter_paths() {
		PathFilter p = PathFilter.create("a");
		byte[] expected = p.getPathsBestEffort().get().iterator().next();
		TreeFilter[] tf = new TreeFilter[] { p, TreeFilter.ANY_DIFF };
		TreeFilter atf = AndTreeFilter.create(tf);

		assertTrue(atf.getPathsBestEffort().isPresent());
		byte[] actual = atf.getPathsBestEffort().get().iterator().next();
		assertEquals(1, actual.length);
		Arrays.equals(expected, actual);
	}
}
