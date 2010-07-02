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

package org.eclipse.jgit.storage.file;

import java.util.Arrays;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.util.IO;

public class PackFileTest extends LocalDiskRepositoryTestCase {
	private TestRng rng;

	private FileRepository repo;

	private TestRepository<FileRepository> tr;

	private WindowCursor wc;

	protected void setUp() throws Exception {
		super.setUp();
		rng = new TestRng(getName());
		repo = createBareRepository();
		tr = new TestRepository<FileRepository>(repo);
		wc = (WindowCursor) repo.newObjectReader();
	}

	protected void tearDown() throws Exception {
		if (wc != null)
			wc.release();
		super.tearDown();
	}

	public void testWhole_SmallObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = rng.nextBytes(300);
		RevBlob id = tr.blob(data);
		tr.branch("master").commit().add("A", id).create();
		tr.packAndPrune();
		assertTrue("has blob", wc.has(id));

		ObjectLoader ol = wc.open(id);
		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertFalse("is not large", ol.isLarge());
		assertTrue("same content", Arrays.equals(data, ol.getCachedBytes()));

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(type, in.getType());
		assertEquals(data.length, in.getSize());
		byte[] data2 = new byte[data.length];
		IO.readFully(in, data2, 0, data.length);
		assertTrue("same content", Arrays.equals(data2, data));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}

	public void testWhole_LargeObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = rng.nextBytes(UnpackedObject.LARGE_OBJECT + 5);
		RevBlob id = tr.blob(data);
		tr.branch("master").commit().add("A", id).create();
		tr.packAndPrune();
		assertTrue("has blob", wc.has(id));

		ObjectLoader ol = wc.open(id);
		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertTrue("is large", ol.isLarge());
		try {
			ol.getCachedBytes();
			fail("Should have thrown LargeObjectException");
		} catch (LargeObjectException tooBig) {
			assertEquals(id.name(), tooBig.getMessage());
		}

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(type, in.getType());
		assertEquals(data.length, in.getSize());
		byte[] data2 = new byte[data.length];
		IO.readFully(in, data2, 0, data.length);
		assertTrue("same content", Arrays.equals(data2, data));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}
}
