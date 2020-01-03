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
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ObjectIdRefTest {
	private static final ObjectId ID_A = ObjectId
			.fromString("41eb0d88f833b558bddeb269b7ab77399cdf98ed");

	private static final ObjectId ID_B = ObjectId
			.fromString("698dd0b8d0c299f080559a1cffc7fe029479a408");

	private static final String name = "refs/heads/a.test.ref";

	@Test
	public void testConstructor_PeeledStatusNotKnown() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertSame(ID_A, r.getObjectId());
		assertFalse("not peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());
		assertSame("leaf is this", r, r.getLeaf());
		assertSame("target is this", r, r.getTarget());
		assertFalse("not symbolic", r.isSymbolic());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, name, ID_A);
		assertSame(Ref.Storage.PACKED, r.getStorage());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE_PACKED, name, ID_A);
		assertSame(Ref.Storage.LOOSE_PACKED, r.getStorage());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, name, null);
		assertSame(Ref.Storage.NEW, r.getStorage());
		assertSame(name, r.getName());
		assertNull("no id on new ref", r.getObjectId());
		assertFalse("not peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());
		assertSame("leaf is this", r, r.getLeaf());
		assertSame("target is this", r, r.getTarget());
		assertFalse("not symbolic", r.isSymbolic());
	}

	@Test
	public void testConstructor_Peeled() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertSame(ID_A, r.getObjectId());
		assertFalse("not peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());
		assertSame("leaf is this", r, r.getLeaf());
		assertSame("target is this", r, r.getTarget());
		assertFalse("not symbolic", r.isSymbolic());

		r = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, name, ID_A);
		assertTrue("is peeled", r.isPeeled());
		assertNull("no peel id", r.getPeeledObjectId());

		r = new ObjectIdRef.PeeledTag(Ref.Storage.LOOSE, name, ID_A, ID_B);
		assertTrue("is peeled", r.isPeeled());
		assertSame(ID_B, r.getPeeledObjectId());
	}

	@Test
	public void testUpdateIndex() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A, 3);
		assertTrue(r.getUpdateIndex() == 3);

		r = new ObjectIdRef.PeeledTag(Ref.Storage.LOOSE, name, ID_A, ID_B, 4);
		assertTrue(r.getUpdateIndex() == 4);

		r = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, name, ID_A, 5);
		assertTrue(r.getUpdateIndex() == 5);
	}

	@Test
	public void testUpdateIndexNotSet() {
		List<ObjectIdRef> r = Arrays.asList(
				new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A),
				new ObjectIdRef.PeeledTag(Ref.Storage.LOOSE, name, ID_A, ID_B),
				new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, name, ID_A));

		for (ObjectIdRef ref : r) {
			try {
				ref.getUpdateIndex();
				fail("Update index wasn't set. It must throw");
			} catch (UnsupportedOperationException u) {
				// Ok
			}
		}
	}


	@Test
	public void testToString() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertEquals("Ref[" + name + "=" + ID_A.name() + "(-1)]",
				r.toString());
	}
}
