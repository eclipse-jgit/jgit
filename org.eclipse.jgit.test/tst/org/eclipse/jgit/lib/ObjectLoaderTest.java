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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.junit.Test;

public class ObjectLoaderTest {
	private TestRng rng;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(JGitTestUtil.getName());
		return rng;
	}

	@Test
	public void testSmallObjectLoader() throws MissingObjectException,
			IOException {
		final byte[] act = getRng().nextBytes(512);
		final ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act);

		assertEquals(OBJ_BLOB, ldr.getType());
		assertEquals(act.length, ldr.getSize());
		assertFalse("not is large", ldr.isLarge());
		assertSame(act, ldr.getCachedBytes());
		assertSame(act, ldr.getCachedBytes(1));
		assertSame(act, ldr.getCachedBytes(Integer.MAX_VALUE));

		byte[] copy = ldr.getBytes();
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getBytes(1);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getBytes(Integer.MAX_VALUE);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		ObjectStream in = ldr.openStream();
		assertNotNull("has stream", in);
		assertTrue("is small stream", in instanceof ObjectStream.SmallStream);
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue("mark supported", in.markSupported());
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue("same content", Arrays.equals(act, copy));

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue("same content", Arrays.equals(act, tmp.toByteArray()));
	}

	@Test
	public void testLargeObjectLoader() throws MissingObjectException,
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
		assertTrue("is large", ldr.isLarge());

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
		assertTrue("same content", Arrays.equals(act, copy));

		ObjectStream in = ldr.openStream();
		assertNotNull("has stream", in);
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue("mark supported", in.markSupported());
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue("same content", Arrays.equals(act, copy));

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue("same content", Arrays.equals(act, tmp.toByteArray()));
	}

	@Test
	public void testLimitedGetCachedBytes() throws LargeObjectException,
			MissingObjectException, IOException {
		byte[] act = getRng().nextBytes(512);
		ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act) {
			@Override
			public boolean isLarge() {
				return true;
			}
		};
		assertTrue("is large", ldr.isLarge());

		try {
			ldr.getCachedBytes(10);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}

		byte[] copy = ldr.getCachedBytes(512);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getCachedBytes(1024);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));
	}

	@Test
	public void testLimitedGetCachedBytesExceedsJavaLimits()
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
		assertTrue("is large", ldr.isLarge());

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
