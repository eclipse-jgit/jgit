/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.lib;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lfs.LfsPointer;
import org.junit.Test;

/*
 * Test LfsPointer file abstraction
 */
public class LFSPointerTest {

	private static final String TEST_SHA256 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";

	@Test
	public void testEncoding() throws IOException {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer ptr = new LfsPointer(id, 4);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ptr.encode(baos);
			assertEquals(
					"version https://git-lfs.github.com/spec/v1\noid sha256:"
							+ TEST_SHA256 + "\nsize 4\n",
					baos.toString(UTF_8.name()));
		}
	}

	@Test
	public void testReadValidLfsPointer() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "size 4\n";
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadValidLfsPointerUnordered() throws Exception {
		// This is actually not allowed per the spec, but JGit accepts it
		// anyway.
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "size 4\n"
				+ "oid sha256:" + TEST_SHA256 + '\n';
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadValidLfsPointerVersionNotFirst() throws Exception {
		// This is actually not allowed per the spec, but JGit accepts it
		// anyway.
		String ptr = "oid sha256:" + TEST_SHA256 + '\n'
				+ "size 4\n"
				+ "version https://git-lfs.github.com/spec/v1\n";
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInvalidLfsPointer() throws Exception {
		String cSource = "size_t someFunction(void *ptr); // Fake C source\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				cSource.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInvalidLfsPointer2() throws Exception {
		String cSource = "size_t\nsomeFunction(void *ptr);\n// Fake C source\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				cSource.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInValidLfsPointerVersionWrong() throws Exception {
		String ptr = "version https://git-lfs.example.org/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInValidLfsPointerVersionTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInValidLfsPointerVersionTwice2() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "version https://git-lfs.github.com/spec/v1\n"
				+ "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInValidLfsPointerOidTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testReadInValidLfsPointerSizeTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "size 4\n"
				+ "size 4\n"
				+ "oid sha256:" + TEST_SHA256 + '\n';
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull("Is not a LFS pointer", LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	public void testRoundtrip() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer ptr = new LfsPointer(id, 4);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ptr.encode(baos);
			try (ByteArrayInputStream in = new ByteArrayInputStream(
					baos.toByteArray())) {
				assertEquals(ptr, LfsPointer.parseLfsPointer(in));
			}
		}
	}

	@Test
	public void testEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertTrue(lfs.equals(lfs2));
		assertTrue(lfs2.equals(lfs));
	}

	@Test
	public void testEqualsNull() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertFalse(lfs.equals(null));
	}

	@Test
	public void testEqualsSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertTrue(lfs.equals(lfs));
	}

	@Test
	public void testEqualsOther() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertFalse(lfs.equals(new Object()));
	}

	@Test
	public void testNotEqualsOid() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId
				.fromString(TEST_SHA256.replace('7', '5'));
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertFalse(lfs.equals(lfs2));
		assertFalse(lfs2.equals(lfs));
	}

	@Test
	public void testNotEqualsSize() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertFalse(lfs.equals(lfs2));
		assertFalse(lfs2.equals(lfs));
	}

	@Test
	public void testCompareToEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertEquals(0, lfs.compareTo(lfs2));
		assertEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	@SuppressWarnings("SelfComparison")
	public void testCompareToSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertEquals(0, lfs.compareTo(lfs));
	}

	@Test
	public void testCompareToNotEqualsOid() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId
				.fromString(TEST_SHA256.replace('7', '5'));
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertNotEquals(0, lfs.compareTo(lfs2));
		assertNotEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	public void testCompareToNotEqualsSize() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertNotEquals(0, lfs.compareTo(lfs2));
		assertNotEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	public void testCompareToNull() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertThrows(NullPointerException.class, () -> lfs.compareTo(null));
	}

	@Test
	public void testHashcodeEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertEquals(lfs.hashCode(), lfs2.hashCode());
	}

	@Test
	public void testHashcodeSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertEquals(lfs.hashCode(), lfs.hashCode());
	}

	@Test
	public void testHashcodeNotEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertNotEquals(lfs.hashCode(), lfs2.hashCode());
	}
}
