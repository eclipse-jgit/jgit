/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.Test;

public class RevObjectTest extends RevWalkTestCase {
	@Test
	void testId() throws Exception {
		final RevCommit a = commit();
		assertSame(a, a.getId());
	}

	@Test
	void testEquals() throws Exception {
		final RevCommit a1 = commit();
		final RevCommit b1 = commit();

		assertEquals(a1, a1);
		Object o = a1;
		assertEquals(a1, o);
		assertNotEquals(a1, b1);

		assertEquals(a1, a1);
		assertEquals(a1, o);
		assertNotEquals(a1, "");

		final RevCommit a2;
		final RevCommit b2;
		try (RevWalk rw2 = new RevWalk(db)) {
			a2 = rw2.parseCommit(a1);
			b2 = rw2.parseCommit(b1);
		}
		assertNotSame(a1, a2);
		assertNotSame(b1, b2);

		assertEquals(a1, a2);
		assertEquals(b1, b2);

		assertEquals(a1.hashCode(), a2.hashCode());
		assertEquals(b1.hashCode(), b2.hashCode());

		assertTrue(AnyObjectId.isEqual(a1, a2));
		assertTrue(AnyObjectId.isEqual(b1, b2));
	}

	@Test
	void testRevObjectTypes() throws Exception {
		assertEquals(Constants.OBJ_TREE, tree().getType());
		assertEquals(Constants.OBJ_COMMIT, commit().getType());
		assertEquals(Constants.OBJ_BLOB, blob("").getType());
		assertEquals(Constants.OBJ_TAG, tag("emptyTree", tree()).getType());
	}

	@Test
	void testHasRevFlag() throws Exception {
		final RevCommit a = commit();
		assertFalse(a.has(RevFlag.UNINTERESTING));
		a.flags |= RevWalk.UNINTERESTING;
		assertTrue(a.has(RevFlag.UNINTERESTING));
	}

	@Test
	void testHasAnyFlag() throws Exception {
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
	void testHasAllFlag() throws Exception {
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
	void testAddRevFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		assertEquals(RevWalk.PARSED, a.flags);

		a.add(flag1);
		assertEquals(RevWalk.PARSED | flag1.mask, a.flags);

		a.add(flag2);
		assertEquals(RevWalk.PARSED | flag1.mask | flag2.mask, a.flags);
	}

	@Test
	void testAddRevFlagSet() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);

		assertEquals(RevWalk.PARSED, a.flags);

		a.add(s);
		assertEquals(RevWalk.PARSED | flag1.mask | flag2.mask, a.flags);
	}

	@Test
	void testRemoveRevFlag() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		a.add(flag1);
		a.add(flag2);
		assertEquals(RevWalk.PARSED | flag1.mask | flag2.mask, a.flags);
		a.remove(flag2);
		assertEquals(RevWalk.PARSED | flag1.mask, a.flags);
	}

	@Test
	void testRemoveRevFlagSet() throws Exception {
		final RevCommit a = commit();
		final RevFlag flag1 = rw.newFlag("flag1");
		final RevFlag flag2 = rw.newFlag("flag2");
		final RevFlag flag3 = rw.newFlag("flag3");
		final RevFlagSet s = new RevFlagSet();
		s.add(flag1);
		s.add(flag2);
		a.add(flag3);
		a.add(s);
		assertEquals(RevWalk.PARSED | flag1.mask | flag2.mask | flag3.mask, a.flags);
		a.remove(s);
		assertEquals(RevWalk.PARSED | flag3.mask, a.flags);
	}
}
