/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.COMPACT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.DEFAULT_COMPARATOR;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_TXN;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.RECEIVE;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.UNREACHABLE_GARBAGE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PackSourceTest {
	@Test
	public void defaultComaprator() throws Exception {
		assertEquals(0, DEFAULT_COMPARATOR.compare(INSERT, INSERT));
		assertEquals(0, DEFAULT_COMPARATOR.compare(RECEIVE, RECEIVE));
		assertEquals(0, DEFAULT_COMPARATOR.compare(COMPACT, COMPACT));
		assertEquals(0, DEFAULT_COMPARATOR.compare(GC, GC));
		assertEquals(0, DEFAULT_COMPARATOR.compare(GC_REST, GC_REST));
		assertEquals(0, DEFAULT_COMPARATOR.compare(GC_TXN, GC_TXN));
		assertEquals(0, DEFAULT_COMPARATOR.compare(UNREACHABLE_GARBAGE, UNREACHABLE_GARBAGE));

		assertEquals(0, DEFAULT_COMPARATOR.compare(INSERT, RECEIVE));
		assertEquals(0, DEFAULT_COMPARATOR.compare(RECEIVE, INSERT));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(INSERT, COMPACT));
		assertEquals(1, DEFAULT_COMPARATOR.compare(COMPACT, INSERT));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(RECEIVE, COMPACT));
		assertEquals(1, DEFAULT_COMPARATOR.compare(COMPACT, RECEIVE));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(COMPACT, GC));
		assertEquals(1, DEFAULT_COMPARATOR.compare(GC, COMPACT));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(GC, GC_REST));
		assertEquals(1, DEFAULT_COMPARATOR.compare(GC_REST, GC));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(GC_REST, GC_TXN));
		assertEquals(1, DEFAULT_COMPARATOR.compare(GC_TXN, GC_REST));

		assertEquals(-1, DEFAULT_COMPARATOR.compare(GC_TXN, UNREACHABLE_GARBAGE));
		assertEquals(1, DEFAULT_COMPARATOR.compare(UNREACHABLE_GARBAGE, GC_TXN));
	}
}
