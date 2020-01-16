/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SymbolicRefTest {
	private static final ObjectId ID_A = ObjectId
			.fromString("41eb0d88f833b558bddeb269b7ab77399cdf98ed");

	private static final ObjectId ID_B = ObjectId
			.fromString("698dd0b8d0c299f080559a1cffc7fe029479a408");

	private static final String targetName = "refs/heads/a.test.ref";

	private static final String name = "refs/remotes/origin/HEAD";

	@Test
	public void testConstructor() {
		Ref t;
		SymbolicRef r;

		t = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, targetName, null);
		r = new SymbolicRef(name, t, 1);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertNull("no id on new ref", r.getObjectId());
		assertFalse("not peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());
		assertSame("leaf is t", t, r.getLeaf());
		assertSame("target is t", t, r.getTarget());
		assertTrue("is symbolic", r.isSymbolic());
		assertTrue("holds update index", r.getUpdateIndex() == 1);

		t = new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, targetName, ID_A);
		r = new SymbolicRef(name, t, 2);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertSame(ID_A, r.getObjectId());
		assertFalse("not peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());
		assertSame("leaf is t", t, r.getLeaf());
		assertSame("target is t", t, r.getTarget());
		assertTrue("is symbolic", r.isSymbolic());
		assertTrue("holds update index", r.getUpdateIndex() == 2);
	}

	@Test
	public void testLeaf() {
		Ref a;
		SymbolicRef b, c, d;

		a = new ObjectIdRef.PeeledTag(Ref.Storage.PACKED, targetName, ID_A, ID_B);
		b = new SymbolicRef("B", a);
		c = new SymbolicRef("C", b);
		d = new SymbolicRef("D", c);

		assertSame(c, d.getTarget());
		assertSame(b, c.getTarget());
		assertSame(a, b.getTarget());

		assertSame(a, d.getLeaf());
		assertSame(a, c.getLeaf());
		assertSame(a, b.getLeaf());
		assertSame(a, a.getLeaf());

		assertSame(ID_A, d.getObjectId());
		assertSame(ID_A, c.getObjectId());
		assertSame(ID_A, b.getObjectId());

		assertTrue(d.isPeeled());
		assertTrue(c.isPeeled());
		assertTrue(b.isPeeled());

		assertSame(ID_B, d.getPeeledObjectId());
		assertSame(ID_B, c.getPeeledObjectId());
		assertSame(ID_B, b.getPeeledObjectId());
	}

	@Test
	public void testToString() {
		Ref a;
		SymbolicRef b, c, d;

		a = new ObjectIdRef.PeeledTag(Ref.Storage.PACKED, targetName, ID_A, ID_B);
		b = new SymbolicRef("B", a);
		c = new SymbolicRef("C", b);
		d = new SymbolicRef("D", c);

		assertEquals("SymbolicRef[D -> C -> B -> " + targetName + "="
				+ ID_A.name() + "(-1)]", d.toString());
	}
}
