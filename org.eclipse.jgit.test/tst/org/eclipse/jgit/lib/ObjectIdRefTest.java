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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ObjectIdRefTest {
	private static final ObjectId ID_A = ObjectId
			.fromString("41eb0d88f833b558bddeb269b7ab77399cdf98ed");

	private static final ObjectId ID_B = ObjectId
			.fromString("698dd0b8d0c299f080559a1cffc7fe029479a408");

	private static final String name = "refs/heads/a.test.ref";

	@Test
	void testConstructor_PeeledStatusNotKnown() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertSame(ID_A, r.getObjectId());
		assertFalse(r.isPeeled(), "not peeled");
		assertNull(r.getPeeledObjectId(), "no peel id");
		assertSame(r, r.getLeaf(), "leaf is this");
		assertSame(r, r.getTarget(), "target is this");
		assertFalse(r.isSymbolic(), "not symbolic");

		r = new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, name, ID_A);
		assertSame(Ref.Storage.PACKED, r.getStorage());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE_PACKED, name, ID_A);
		assertSame(Ref.Storage.LOOSE_PACKED, r.getStorage());

		r = new ObjectIdRef.Unpeeled(Ref.Storage.NEW, name, null);
		assertSame(Ref.Storage.NEW, r.getStorage());
		assertSame(name, r.getName());
		assertNull(r.getObjectId(), "no id on new ref");
		assertFalse(r.isPeeled(), "not peeled");
		assertNull(r.getPeeledObjectId(), "no peel id");
		assertSame(r, r.getLeaf(), "leaf is this");
		assertSame(r, r.getTarget(), "target is this");
		assertFalse(r.isSymbolic(), "not symbolic");
	}

	@Test
	void testConstructor_Peeled() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertSame(Ref.Storage.LOOSE, r.getStorage());
		assertSame(name, r.getName());
		assertSame(ID_A, r.getObjectId());
		assertFalse(r.isPeeled(), "not peeled");
		assertNull(r.getPeeledObjectId(), "no peel id");
		assertSame(r, r.getLeaf(), "leaf is this");
		assertSame(r, r.getTarget(), "target is this");
		assertFalse(r.isSymbolic(), "not symbolic");

		r = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, name, ID_A);
		assertTrue(r.isPeeled(), "is peeled");
		assertNull(r.getPeeledObjectId(), "no peel id");

		r = new ObjectIdRef.PeeledTag(Ref.Storage.LOOSE, name, ID_A, ID_B);
		assertTrue(r.isPeeled(), "is peeled");
		assertSame(ID_B, r.getPeeledObjectId());
	}

	@Test
	void testUpdateIndex() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A, 3);
		assertEquals(r.getUpdateIndex(), 3);

		r = new ObjectIdRef.PeeledTag(Ref.Storage.LOOSE, name, ID_A, ID_B, 4);
		assertEquals(r.getUpdateIndex(), 4);

		r = new ObjectIdRef.PeeledNonTag(Ref.Storage.LOOSE, name, ID_A, 5);
		assertEquals(r.getUpdateIndex(), 5);
	}

	@Test
	void testUpdateIndexNotSet() {
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
	void testToString() {
		ObjectIdRef r;

		r = new ObjectIdRef.Unpeeled(Ref.Storage.LOOSE, name, ID_A);
		assertEquals("Ref[" + name + "=" + ID_A.name() + "(-1)]",
				r.toString());
	}
}
