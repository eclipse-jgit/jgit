/*
 * Copyright (C) 2014, Rüdiger Herrmann <ruediger.herrmann@gmx.de>
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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.transport.PackParser;
import org.junit.Before;
import org.junit.Test;

public class ObjectInserterTest {

	private ObjectInserter objectInserter;

	@Test
	public void testIdForWithCorrectLength() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		ObjectId objectId = objectInserter.idFor(OBJ_BLOB, bytes.length,
				asInputStream(bytes));
		assertNotNull(objectId);
	}

	@Test(expected = IOException.class)
	public void testIdForWithLengthGreaterThanStreamSize() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, bytes.length + 1, asInputStream(bytes));
	}

	@Test(expected = IOException.class)
	public void testIdForWithLengthLessThanStreamSize() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, bytes.length - 1, asInputStream(bytes));
	}

	@Test(expected = IOException.class)
	public void testIdForWithNegativeLength() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, -1, asInputStream(bytes));
	}

	@Before
	public void setUp() {
		objectInserter = new TestObjectInserter();
	}

	private ByteArrayInputStream asInputStream(byte[] bytes) {
		return new ByteArrayInputStream(bytes);
	}

	private static class TestObjectInserter extends ObjectInserter {
		@Override
		public ObjectId insert(int objectType, long length, InputStream in) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PackParser newPackParser(InputStream in) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectReader newReader() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void release() {
			throw new UnsupportedOperationException();
		}
	}
}
