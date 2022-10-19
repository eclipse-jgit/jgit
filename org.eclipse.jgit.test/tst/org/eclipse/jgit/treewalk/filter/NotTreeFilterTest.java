/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;

public class NotTreeFilterTest extends RepositoryTestCase {
	@Test
	void testWrap() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			final TreeFilter a = TreeFilter.ALL;
			final TreeFilter n = NotTreeFilter.create(a);
			assertNotNull(n);
			assertTrue(a.include(tw));
			assertFalse(n.include(tw));
		}
	}

	@Test
	void testNegateIsUnwrap() throws Exception {
		final TreeFilter a = PathFilter.create("a/b");
		final TreeFilter n = NotTreeFilter.create(a);
		assertSame(a, n.negate());
	}

	@Test
	void testShouldBeRecursive_ALL() throws Exception {
		final TreeFilter a = TreeFilter.ALL;
		final TreeFilter n = NotTreeFilter.create(a);
		assertEquals(a.shouldBeRecursive(), n.shouldBeRecursive());
	}

	@Test
	void testShouldBeRecursive_PathFilter() throws Exception {
		final TreeFilter a = PathFilter.create("a/b");
		assertTrue(a.shouldBeRecursive());
		final TreeFilter n = NotTreeFilter.create(a);
		assertTrue(n.shouldBeRecursive());
	}

	@Test
	void testCloneIsDeepClone() throws Exception {
		final TreeFilter a = new AlwaysCloneTreeFilter();
		assertNotSame(a, a.clone());
		final TreeFilter n = NotTreeFilter.create(a);
		assertNotSame(n, n.clone());
	}

	@Test
	void testCloneIsSparseWhenPossible() throws Exception {
		final TreeFilter a = TreeFilter.ALL;
		assertSame(a, a.clone());
		final TreeFilter n = NotTreeFilter.create(a);
		assertSame(n, n.clone());
	}
}
