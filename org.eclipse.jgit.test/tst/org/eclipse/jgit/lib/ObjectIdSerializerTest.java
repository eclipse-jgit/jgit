/*
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com>
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
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

public class ObjectIdSerializerTest {
	@Test
	public void serialize() throws Exception {
		ObjectId original = new ObjectId(1, 2, 3, 4, 5);
		ObjectId deserialized = writeAndReadBackFromTempFile(original);
		assertEquals(original, deserialized);
	}

	@Test
	public void serializeZeroId() throws Exception {
		ObjectId original = ObjectId.zeroId();
		ObjectId deserialized = writeAndReadBackFromTempFile(original);
		assertEquals(original, deserialized);
	}

	@Test
	public void serializeNull() throws Exception {
		ObjectId deserialized = writeAndReadBackFromTempFile(null);
		assertNull(deserialized);
	}

	private ObjectId writeAndReadBackFromTempFile(ObjectId objectId)
			throws Exception {
		File file = File.createTempFile("ObjectIdSerializerTest_", "");
		try (OutputStream out = new FileOutputStream(file)) {
			if (objectId == null) {
				ObjectIdSerializer.write(out, objectId);
			} else {
				ObjectIdSerializer.writeWithoutMarker(out, objectId);
			}
		}
		try (InputStream in = new FileInputStream(file)) {
			if (objectId == null) {
				return ObjectIdSerializer.read(in);
			}
			return ObjectIdSerializer.readWithoutMarker(in);
		}
	}
}
