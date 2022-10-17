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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lfs.LfsPointer;
import org.junit.jupiter.api.Test;

/*
 * Test LfsPointer file abstraction
 */
public class LFSPointerTest {

	private static final String TEST_SHA256 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";

	@Test
	void testEncoding() throws IOException {
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
	void testReadValidLfsPointer() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n' + "size 4\n";
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	void testReadValidLfsPointerUnordered() throws Exception {
		// This is actually not allowed per the spec, but JGit accepts it
		// anyway.
		String ptr = "version https://git-lfs.github.com/spec/v1\n" + "size 4\n"
				+ "oid sha256:" + TEST_SHA256 + '\n';
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	void testReadValidLfsPointerVersionNotFirst() throws Exception {
		// This is actually not allowed per the spec, but JGit accepts it
		// anyway.
		String ptr = "oid sha256:" + TEST_SHA256 + '\n' + "size 4\n"
				+ "version https://git-lfs.github.com/spec/v1\n";
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertEquals(lfs, LfsPointer.parseLfsPointer(in));
		}
	}

	@Test
	void testReadInvalidLfsPointer() throws Exception {
		String cSource = "size_t someFunction(void *ptr); // Fake C source\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				cSource.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInvalidLfsPointer2() throws Exception {
		String cSource = "size_t\nsomeFunction(void *ptr);\n// Fake C source\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				cSource.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInValidLfsPointerVersionWrong() throws Exception {
		String ptr = "version https://git-lfs.example.org/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n' + "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInValidLfsPointerVersionTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "version https://git-lfs.github.com/spec/v1\n" + "oid sha256:"
				+ TEST_SHA256 + '\n' + "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInValidLfsPointerVersionTwice2() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n'
				+ "version https://git-lfs.github.com/spec/v1\n" + "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInValidLfsPointerOidTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n"
				+ "oid sha256:" + TEST_SHA256 + '\n' + "oid sha256:"
				+ TEST_SHA256 + '\n' + "size 4\n";
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testReadInValidLfsPointerSizeTwice() throws Exception {
		String ptr = "version https://git-lfs.github.com/spec/v1\n" + "size 4\n"
				+ "size 4\n" + "oid sha256:" + TEST_SHA256 + '\n';
		try (ByteArrayInputStream in = new ByteArrayInputStream(
				ptr.getBytes(UTF_8))) {
			assertNull(LfsPointer.parseLfsPointer(in), "Is not a LFS pointer");
		}
	}

	@Test
	void testRoundtrip() throws Exception {
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
	void testEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertEquals(lfs, lfs2);
		assertEquals(lfs2, lfs);
	}

	@Test
	void testEqualsNull() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertNotEquals(lfs, null);
	}

	@Test
	void testEqualsSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertEquals(lfs, lfs);
	}

	@Test
	void testEqualsOther() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertNotEquals(lfs, new Object());
	}

	@Test
	void testNotEqualsOid() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId
				.fromString(TEST_SHA256.replace('7', '5'));
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertNotEquals(lfs, lfs2);
		assertNotEquals(lfs2, lfs);
	}

	@Test
	void testNotEqualsSize() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertNotEquals(lfs, lfs2);
		assertNotEquals(lfs2, lfs);
	}

	@Test
	void testCompareToEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertEquals(0, lfs.compareTo(lfs2));
		assertEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	@SuppressWarnings("SelfComparison")
	void testCompareToSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertEquals(0, lfs.compareTo(lfs));
	}

	@Test
	void testCompareToNotEqualsOid() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId
				.fromString(TEST_SHA256.replace('7', '5'));
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertNotEquals(0, lfs.compareTo(lfs2));
		assertNotEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	void testCompareToNotEqualsSize() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertNotEquals(0, lfs.compareTo(lfs2));
		assertNotEquals(0, lfs2.compareTo(lfs));
	}

	@Test
	void testCompareToNull() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertThrows(NullPointerException.class, () -> lfs.compareTo(null));
	}

	@Test
	void testHashcodeEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 4);
		assertEquals(lfs.hashCode(), lfs2.hashCode());
	}

	@Test
	void testHashcodeSame() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		assertEquals(lfs.hashCode(), lfs.hashCode());
	}

	@Test
	void testHashcodeNotEquals() throws Exception {
		AnyLongObjectId id = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs = new LfsPointer(id, 4);
		AnyLongObjectId id2 = LongObjectId.fromString(TEST_SHA256);
		LfsPointer lfs2 = new LfsPointer(id2, 5);
		assertNotEquals(lfs.hashCode(), lfs2.hashCode());
	}
}
