/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class RevObjectTest extends RevWalkTestCase {
	@Test
	public void testId() throws Exception {
		final RevCommit a = commit();
		assertSame(a, a.getId());
	}

	@Test
	public void testEquals() throws Exception {
		final RevCommit a1 = commit();
		final RevCommit b1 = commit();

		assertTrue(a1.equals(a1));
		assertTrue(a1.equals((Object) a1));
		assertFalse(a1.equals(b1));

		assertTrue(a1.equals(a1));
		assertTrue(a1.equals((Object) a1));
		assertFalse(a1.equals(""));

		final RevWalk rw2 = new RevWalk(db);
		final RevCommit a2 = rw2.parseCommit(a1);
		final RevCommit b2 = rw2.parseCommit(b1);
		assertNotSame(a1, a2);
		assertNotSame(b1, b2);

		assertTrue(a1.equals(a2));
		assertTrue(b1.equals(b2));

		assertEquals(a1.hashCode(), a2.hashCode());
		assertEquals(b1.hashCode(), b2.hashCode());

		assertTrue(AnyObjectId.equals(a1, a2));
		assertTrue(AnyObjectId.equals(b1, b2));
	}

	@Test
	public void testRevObjectTypes() throws Exception {
		assertEquals(Constants.OBJ_TREE, tree().getType());
		assertEquals(Constants.OBJ_COMMIT, commit().getType());
		assertEquals(Constants.OBJ_BLOB, blob("").getType());
		assertEquals(Constants.OBJ_TAG, tag("emptyTree", tree()).getType());
	}

	@Test
	public void testHasRevFlag() throws Exception {
		final RevCommit a = commit();
		assertFalse(a.has(RevFlag.UNINTERESTING));
		a.flags |= RevWalk.UNINTERESTING;
		assertTrue(a.has(RevFlag.UNINTERESTING));
	}

	@Test
	public void testHasAnyFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);

		assertFalse(a.hasAny(s));
		a.flags |= flag1.mask;
		assertTrue(a.hasAny(s));
	}

	@Test
	public void testHasAllFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);

		assertFalse(a.hasAll(s));
		a.flags |= flag1.mask;
		assertFalse(a.hasAll(s));
		a.flags |= flag2.mask;
		assertTrue(a.hasAll(s));
	}

	@Test
	public void testAddRevFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		assertEquals(0, a.flags);

		a.add(flag1);
		assertEquals(flag1.mask, a.flags);

		a.add(flag2);
		assertEquals(flag1.mask | flag2.mask, a.flags);
	}

	@Test
	public void testAddRevFlagSet() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);

		assertEquals(0, a.flags);

		a.add(s);
		assertEquals(flag1.mask | flag2.mask, a.flags);
	}

	@Test
	public void testRemoveRevFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		a.add(flag1);
		a.add(flag2);
		assertEquals(flag1.mask | flag2.mask, a.flags);
		a.remove(flag2);
		assertEquals(flag1.mask, a.flags);
	}

	@Test
	public void testRemoveRevFlagSet() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlag flag3 = rw.newFlag("flag3");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);
		a.add(flag3);
		a.add(s);
		assertEquals(flag1.mask | flag2.mask | flag3.mask, a.flags);
		a.remove(s);
		assertEquals(flag3.mask, a.flags);
	}
}
