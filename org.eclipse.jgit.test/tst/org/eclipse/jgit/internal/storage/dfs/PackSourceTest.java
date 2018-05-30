/*
 * Copyright (C) 2018, Google LLC.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
