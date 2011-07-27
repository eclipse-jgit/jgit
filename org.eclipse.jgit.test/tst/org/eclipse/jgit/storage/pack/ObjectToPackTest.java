/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.pack;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ObjectToPackTest {
	private final ObjectId id1 = ObjectId
			.fromString("1000000000000000000000000000000000000000");

	private final ObjectId id2 = ObjectId
			.fromString("2000000000000000000000000000000000000000");

	private final ObjectId id3 = ObjectId
			.fromString("3000000000000000000000000000000000000000");

	private final ObjectId id4 = ObjectId
			.fromString("4000000000000000000000000000000000000000");

	@Test
	public void testDeltaBase() {
		ObjectToPack base1 = new ObjectToPack(id1, OBJ_BLOB);
		ObjectToPack obj1 = new ObjectToPack(id2, OBJ_BLOB);
		ObjectToPack obj2 = new ObjectToPack(id3, OBJ_BLOB);

		obj1.setDeltaBase(base1);
		obj2.setDeltaBase(base1);

		assertSame(base1, obj1.getDeltaBase());
		assertSame(base1, obj2.getDeltaBase());

		assertNull(base1.deltaNext);
		assertSame(obj2, base1.deltaChild);
		assertSame(obj1, obj2.deltaNext);
		assertNull(obj1.deltaNext);

		ObjectToPack base2 = new ObjectToPack(id4, OBJ_BLOB);
		obj1.setDeltaBase(base2);

		assertSame(obj1, base2.deltaChild);
		assertSame(obj2, base1.deltaChild);
		assertNull(obj1.deltaNext);
		assertNull(obj2.deltaNext);

		obj2.setDeltaBase(base2);
		assertNull(base1.deltaChild);
		assertSame(obj2, base2.deltaChild);
		assertSame(obj1, obj2.deltaNext);
		assertNull(obj1.deltaNext);

		base1.setDeltaBase(base2);
		obj1.clearDeltaBase();
		assertSame(base1, base2.deltaChild);
		assertSame(obj2, base1.deltaNext);
		assertNull(obj2.deltaNext);
		assertNull(obj1.deltaNext);
	}
}
