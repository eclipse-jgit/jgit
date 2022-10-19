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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.TestInfoRetriever;
import org.eclipse.jgit.junit.TestRng;
import org.junit.jupiter.api.Test;

public class ObjectLoaderTest extends TestInfoRetriever {
	private TestRng rng;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(getTestMethodName());
		return rng;
	}

	@Test
	void testSmallObjectLoader() throws MissingObjectException,
			IOException {
		final byte[] act = getRng().nextBytes(512);
		final ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act);

		assertEquals(OBJ_BLOB, ldr.getType());
		assertEquals(act.length, ldr.getSize());
		assertFalse(ldr.isLarge(), "not is large");
		assertSame(act, ldr.getCachedBytes());
		assertSame(act, ldr.getCachedBytes(1));
		assertSame(act, ldr.getCachedBytes(Integer.MAX_VALUE));

		byte[] copy = ldr.getBytes();
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");

		copy = ldr.getBytes(1);
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");

		copy = ldr.getBytes(Integer.MAX_VALUE);
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");

		ObjectStream in = ldr.openStream();
		assertNotNull(in, "has stream");
		assertTrue(in instanceof ObjectStream.SmallStream, "is small stream");
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue(in.markSupported(), "mark supported");
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue(Arrays.equals(act, copy), "same content");

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue(Arrays.equals(act, tmp.toByteArray()), "same content");
	}

	@Test
	void testLargeObjectLoader() throws MissingObjectException,
			IOException {
		final byte[] act = getRng().nextBytes(512);
		final ObjectLoader ldr = new ObjectLoader() {
			@Override
			public byte[] getCachedBytes() throws LargeObjectException {
				throw new LargeObjectException();
			}

			@Override
			public long getSize() {
				return act.length;
			}

			@Override
			public int getType() {
				return OBJ_BLOB;
			}

			@Override
			public ObjectStream openStream() throws MissingObjectException,
					IOException {
				return new ObjectStream.Filter(getType(), act.length,
						new ByteArrayInputStream(act));
			}
		};

		assertEquals(OBJ_BLOB, ldr.getType());
		assertEquals(act.length, ldr.getSize());
		assertTrue(ldr.isLarge(), "is large");

		try {
			ldr.getCachedBytes();
			fail("did not throw on getCachedBytes()");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		try {
			ldr.getBytes();
			fail("did not throw on getBytes()");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		try {
			ldr.getCachedBytes(64);
			fail("did not throw on getCachedBytes(64)");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		byte[] copy = ldr.getCachedBytes(1024);
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");

		ObjectStream in = ldr.openStream();
		assertNotNull(in, "has stream");
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue(in.markSupported(), "mark supported");
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue(Arrays.equals(act, copy), "same content");

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue(Arrays.equals(act, tmp.toByteArray()), "same content");
	}

	@Test
	void testLimitedGetCachedBytes() throws LargeObjectException,
			MissingObjectException, IOException {
		byte[] act = getRng().nextBytes(512);
		ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act) {
			@Override
			public boolean isLarge() {
				return true;
			}
		};
		assertTrue(ldr.isLarge(), "is large");

		try {
			ldr.getCachedBytes(10);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}

		byte[] copy = ldr.getCachedBytes(512);
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");

		copy = ldr.getCachedBytes(1024);
		assertNotSame(act, copy);
		assertTrue(Arrays.equals(act, copy), "same content");
	}

	@Test
	void testLimitedGetCachedBytesExceedsJavaLimits()
			throws LargeObjectException, MissingObjectException, IOException {
		ObjectLoader ldr = new ObjectLoader() {
			@Override
			public boolean isLarge() {
				return true;
			}

			@Override
			public byte[] getCachedBytes() throws LargeObjectException {
				throw new LargeObjectException();
			}

			@Override
			public long getSize() {
				return Long.MAX_VALUE;
			}

			@Override
			public int getType() {
				return OBJ_BLOB;
			}

			@Override
			public ObjectStream openStream() throws MissingObjectException,
					IOException {
				return new ObjectStream() {
					@Override
					public long getSize() {
						return Long.MAX_VALUE;
					}

					@Override
					public int getType() {
						return OBJ_BLOB;
					}

					@Override
					public int read() throws IOException {
						fail("never should have reached read");
						return -1;
					}

					@Override
					public int read(byte b[], int off, int len) {
						fail("never should have reached read");
						return -1;
					}
				};
			}
		};
		assertTrue(ldr.isLarge(), "is large");

		try {
			ldr.getCachedBytes(10);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}

		try {
			ldr.getCachedBytes(Integer.MAX_VALUE);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}
	}
}
