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

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrTreeFilterTest {
	@Test
	public void getPathsBestEffort_multiplePathFilters_union() {
		PathFilter p1 = PathFilter.create("a");
		PathFilter p2 = PathFilter.create("b");
		PathFilter p3 = PathFilter.create("c");

		TreeFilter[] tf = new TreeFilter[] { p1, p2, p3 };

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isPresent());
		Set<byte[]> actual = otf.getPathsBestEffort().get();
		assertEquals(3, actual.size());

		Set<String> actualPaths = actual.stream().map(String::new)
				.collect(Collectors.toSet());
		assertTrue(actualPaths.contains(p1.pathStr));
		assertTrue(actualPaths.contains(p2.pathStr));
		assertTrue(actualPaths.contains(p3.pathStr));
	}

	@Test
	public void getPathsBestEffort_noPathFilter_empty() {
		TreeFilter[] tf = new TreeFilter[] { TreeFilter.ANY_DIFF,
				TreeFilter.ANY_DIFF };

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isEmpty());
	}

	@Test
	public void getPathsBestEffort_singlePathFilter_path() {
		PathFilter p1 = PathFilter.create("a");

		TreeFilter[] tf = new TreeFilter[] { p1, TreeFilter.ANY_DIFF };

		TreeFilter otf = OrTreeFilter.create(tf);
		assertTrue(otf.getPathsBestEffort().isPresent());
		Set<byte[]> actual = otf.getPathsBestEffort().get();
		assertEquals(1, actual.size());

		Set<String> actualPaths = actual.stream().map(String::new)
				.collect(Collectors.toSet());
		assertTrue(actualPaths.contains(p1.pathStr));
	}
}
