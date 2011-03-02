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

package org.eclipse.jgit.storage.dht;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.dht.spi.memory.MemoryDatabase;
import org.eclipse.jgit.util.IO;
import org.junit.Before;
import org.junit.Test;

public class LargeNonDeltaObjectTest {
	private MemoryDatabase db;

	@Before
	public void setUpDatabase() {
		db = new MemoryDatabase();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testInsertRead() throws IOException {
		DhtInserterOptions insopt = new DhtInserterOptions();
		insopt.setChunkSize(128);
		insopt.setCompression(Deflater.NO_COMPRESSION);

		Repository repo = new DhtRepositoryBuilder() //
				.setDatabase(db) //
				.setInserterOptions(insopt) //
				.setRepositoryName("test.git") //
				.setMustExist(false) //
				.build();
		repo.create(true);

		byte[] data = new byte[insopt.getChunkSize() * 3];
		Arrays.fill(data, (byte) 0x42);

		ObjectInserter ins = repo.newObjectInserter();
		ObjectId id = ins.insert(Constants.OBJ_BLOB, data);
		ins.flush();
		ins.release();

		ObjectReader reader = repo.newObjectReader();
		ObjectLoader ldr = reader.open(id);
		assertEquals(Constants.OBJ_BLOB, ldr.getType());
		assertEquals(data.length, ldr.getSize());
		assertTrue(ldr.isLarge());

		byte[] dst = new byte[data.length];
		ObjectStream in = ldr.openStream();
		IO.readFully(in, dst, 0, dst.length);
		assertTrue(Arrays.equals(data, dst));
		in.close();

		// Reading should still work, even though initial chunk is gone.
		dst = new byte[data.length];
		in = ldr.openStream();
		IO.readFully(in, dst, 0, dst.length);
		assertTrue(Arrays.equals(data, dst));
		in.close();

		reader.release();
	}
}
