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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnpackedObjectTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

	private TestRng rng;

	private FileRepository repo;

	private WindowCursor wc;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(JGitTestUtil.getName());
		return rng;
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();

		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setStreamFileThreshold(streamThreshold);
		WindowCache.reconfigure(cfg);

		repo = createBareRepository();
		wc = (WindowCursor) repo.newObjectReader();
	}

	@After
	public void tearDown() throws Exception {
		if (wc != null)
			wc.release();
		WindowCache.reconfigure(new WindowCacheConfig());
		super.tearDown();
	}

	@Test
	public void testStandardFormat_SmallObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(300);
		byte[] gz = compressStandardFormat(type, data);
		ObjectId id = ObjectId.zeroId();

		ObjectLoader ol = UnpackedObject.open(new ByteArrayInputStream(gz),
				path(id), id, wc);
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

	@Test
	public void testStandardFormat_LargeObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		ObjectId id = new ObjectInserter.Formatter().idFor(type, data);
		write(id, compressStandardFormat(type, data));

		ObjectLoader ol;
		{
			FileInputStream fs = new FileInputStream(path(id));
			try {
				ol = UnpackedObject.open(fs, path(id), id, wc);
			} finally {
				fs.close();
			}
		}

		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertTrue("is large", ol.isLarge());
		try {
			ol.getCachedBytes();
			fail("Should have thrown LargeObjectException");
		} catch (LargeObjectException tooBig) {
			assertEquals(MessageFormat.format(
					JGitText.get().largeObjectException, id.name()), tooBig
					.getMessage());
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

	@Test
	public void testStandardFormat_NegativeSize() throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat("blob", "-1", data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectNegativeSize), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_InvalidType() throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat("not.a.type", "1", data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectInvalidType), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_NoHeader() throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = {};

		try {
			byte[] gz = compressStandardFormat("", "", data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectNoHeader), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_GarbageAfterSize() throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat("blob", "1foo", data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectGarbageAfterSize),
					coe.getMessage());
		}
	}

	@Test
	public void testStandardFormat_SmallObject_CorruptZLibStream()
			throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat(Constants.OBJ_BLOB, data);
			for (int i = 5; i < gz.length; i++)
				gz[i] = 0;
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_SmallObject_TruncatedZLibStream()
			throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat(Constants.OBJ_BLOB, data);
			byte[] tr = new byte[gz.length - 1];
			System.arraycopy(gz, 0, tr, 0, tr.length);
			UnpackedObject.open(new ByteArrayInputStream(tr), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_SmallObject_TrailingGarbage()
			throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressStandardFormat(Constants.OBJ_BLOB, data);
			byte[] tr = new byte[gz.length + 1];
			System.arraycopy(gz, 0, tr, 0, gz.length);
			UnpackedObject.open(new ByteArrayInputStream(tr), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_LargeObject_CorruptZLibStream()
			throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		ObjectId id = new ObjectInserter.Formatter().idFor(type, data);
		byte[] gz = compressStandardFormat(type, data);
		gz[gz.length - 1] = 0;
		gz[gz.length - 2] = 0;

		write(id, gz);

		ObjectLoader ol;
		{
			FileInputStream fs = new FileInputStream(path(id));
			try {
				ol = UnpackedObject.open(fs, path(id), id, wc);
			} finally {
				fs.close();
			}
		}

		try {
			byte[] tmp = new byte[data.length];
			InputStream in = ol.openStream();
			try {
				IO.readFully(in, tmp, 0, tmp.length);
			} finally {
				in.close();
			}
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_LargeObject_TruncatedZLibStream()
			throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		ObjectId id = new ObjectInserter.Formatter().idFor(type, data);
		byte[] gz = compressStandardFormat(type, data);
		byte[] tr = new byte[gz.length - 1];
		System.arraycopy(gz, 0, tr, 0, tr.length);

		write(id, tr);

		ObjectLoader ol;
		{
			FileInputStream fs = new FileInputStream(path(id));
			try {
				ol = UnpackedObject.open(fs, path(id), id, wc);
			} finally {
				fs.close();
			}
		}

		byte[] tmp = new byte[data.length];
		InputStream in = ol.openStream();
		IO.readFully(in, tmp, 0, tmp.length);
		try {
			in.close();
			fail("close did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testStandardFormat_LargeObject_TrailingGarbage()
			throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		ObjectId id = new ObjectInserter.Formatter().idFor(type, data);
		byte[] gz = compressStandardFormat(type, data);
		byte[] tr = new byte[gz.length + 1];
		System.arraycopy(gz, 0, tr, 0, gz.length);

		write(id, tr);

		ObjectLoader ol;
		{
			FileInputStream fs = new FileInputStream(path(id));
			try {
				ol = UnpackedObject.open(fs, path(id), id, wc);
			} finally {
				fs.close();
			}
		}

		byte[] tmp = new byte[data.length];
		InputStream in = ol.openStream();
		IO.readFully(in, tmp, 0, tmp.length);
		try {
			in.close();
			fail("close did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectBadStream), coe
					.getMessage());
		}
	}

	@Test
	public void testPackFormat_SmallObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(300);
		byte[] gz = compressPackFormat(type, data);
		ObjectId id = ObjectId.zeroId();

		ObjectLoader ol = UnpackedObject.open(new ByteArrayInputStream(gz),
				path(id), id, wc);
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
		assertTrue("same content", Arrays.equals(data, ol.getCachedBytes()));
		in.close();
	}

	@Test
	public void testPackFormat_LargeObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		ObjectId id = new ObjectInserter.Formatter().idFor(type, data);
		write(id, compressPackFormat(type, data));

		ObjectLoader ol;
		{
			FileInputStream fs = new FileInputStream(path(id));
			try {
				ol = UnpackedObject.open(fs, path(id), id, wc);
			} finally {
				fs.close();
			}
		}

		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertTrue("is large", ol.isLarge());
		try {
			ol.getCachedBytes();
			fail("Should have thrown LargeObjectException");
		} catch (LargeObjectException tooBig) {
			assertEquals(MessageFormat.format(
					JGitText.get().largeObjectException, id.name()), tooBig
					.getMessage());
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

	@Test
	public void testPackFormat_DeltaNotAllowed() throws Exception {
		ObjectId id = ObjectId.zeroId();
		byte[] data = getRng().nextBytes(300);

		try {
			byte[] gz = compressPackFormat(Constants.OBJ_OFS_DELTA, data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectInvalidType), coe
					.getMessage());
		}

		try {
			byte[] gz = compressPackFormat(Constants.OBJ_REF_DELTA, data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectInvalidType), coe
					.getMessage());
		}

		try {
			byte[] gz = compressPackFormat(Constants.OBJ_TYPE_5, data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectInvalidType), coe
					.getMessage());
		}

		try {
			byte[] gz = compressPackFormat(Constants.OBJ_EXT, data);
			UnpackedObject.open(new ByteArrayInputStream(gz), path(id), id, wc);
			fail("Did not throw CorruptObjectException");
		} catch (CorruptObjectException coe) {
			assertEquals(MessageFormat.format(JGitText.get().objectIsCorrupt,
					id.name(), JGitText.get().corruptObjectInvalidType), coe
					.getMessage());
		}
	}

	private byte[] compressStandardFormat(int type, byte[] data)
			throws IOException {
		String typeString = Constants.typeString(type);
		String length = String.valueOf(data.length);
		return compressStandardFormat(typeString, length, data);
	}

	private byte[] compressStandardFormat(String type, String length,
			byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DeflaterOutputStream d = new DeflaterOutputStream(out);
		d.write(Constants.encodeASCII(type));
		d.write(' ');
		d.write(Constants.encodeASCII(length));
		d.write(0);
		d.write(data);
		d.finish();
		return out.toByteArray();
	}

	private byte[] compressPackFormat(int type, byte[] data) throws IOException {
		byte[] hdr = new byte[64];
		int rawLength = data.length;
		int nextLength = rawLength >>> 4;
		hdr[0] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (rawLength & 0x0F));
		rawLength = nextLength;
		int n = 1;
		while (rawLength > 0) {
			nextLength >>>= 7;
			hdr[n++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (rawLength & 0x7F));
			rawLength = nextLength;
		}

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(hdr, 0, n);

		DeflaterOutputStream d = new DeflaterOutputStream(out);
		d.write(data);
		d.finish();
		return out.toByteArray();
	}

	private File path(ObjectId id) {
		return repo.getObjectDatabase().fileFor(id);
	}

	private void write(ObjectId id, byte[] data) throws IOException {
		File path = path(id);
		FileUtils.mkdirs(path.getParentFile());
		FileOutputStream out = new FileOutputStream(path);
		try {
			out.write(data);
		} finally {
			out.close();
		}
	}
}
