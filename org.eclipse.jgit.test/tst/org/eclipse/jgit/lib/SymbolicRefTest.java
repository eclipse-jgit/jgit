/*
 * Copyright (C) 2010, Google Inc.
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
