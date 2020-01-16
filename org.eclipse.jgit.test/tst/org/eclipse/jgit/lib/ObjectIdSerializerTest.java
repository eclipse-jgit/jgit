/*
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
