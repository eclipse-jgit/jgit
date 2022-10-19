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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;

public class TreeFilterTest extends RepositoryTestCase {
	@Test
	void testALL_IncludesAnything() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.addTree(new EmptyTreeIterator());
			assertTrue(TreeFilter.ALL.include(tw));
		}
	}

	@Test
	void testALL_ShouldNotBeRecursive() throws Exception {
		assertFalse(TreeFilter.ALL.shouldBeRecursive());
	}

	@Test
	void testALL_IdentityClone() throws Exception {
		assertSame(TreeFilter.ALL, TreeFilter.ALL.clone());
	}

	@Test
	void testNotALL_IncludesNothing() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.addTree(new EmptyTreeIterator());
			assertFalse(TreeFilter.ALL.negate().include(tw));
		}
	}

	@Test
	void testANY_DIFF_IncludesSingleTreeCase() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.addTree(new EmptyTreeIterator());
			assertTrue(TreeFilter.ANY_DIFF.include(tw));
		}
	}

	@Test
	void testANY_DIFF_ShouldNotBeRecursive() throws Exception {
		assertFalse(TreeFilter.ANY_DIFF.shouldBeRecursive());
	}

	@Test
	void testANY_DIFF_IdentityClone() throws Exception {
		assertSame(TreeFilter.ANY_DIFF, TreeFilter.ANY_DIFF.clone());
	}
}
