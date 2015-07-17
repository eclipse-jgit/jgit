/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.lib;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.MutableObjectIdTest
 */
public class MutableLongObjectIdTest {

	@Test
	public void testFromRawLong() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(new LongObjectId(1L, 2L, 3L, 4L), m);
	}

	@Test
	public void testFromString() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromString(id.name());
		assertEquals(id, m);
	}

	@Test
	public void testFromStringByte() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		byte[] buf = new byte[64];
		id.copyTo(buf, 0);
		m.fromString(buf, 0);
		assertEquals(id, m);
	}

	@Test
	public void testCopy() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, new MutableLongObjectId(m));
	}

	@Test
	public void testToObjectId() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, m.toObjectId());
	}
}
