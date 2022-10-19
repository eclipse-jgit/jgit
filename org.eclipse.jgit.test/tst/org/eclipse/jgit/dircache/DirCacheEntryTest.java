/*
 * Copyright (C) 2009, 2020 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import static java.time.Instant.EPOCH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.dircache.DirCache.DirCacheVersion;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.MutableInteger;
import org.junit.jupiter.api.Test;

public class DirCacheEntryTest {
	@Test
	void testIsValidPath() {
		assertTrue(isValidPath("a"));
		assertTrue(isValidPath("a/b"));
		assertTrue(isValidPath("ab/cd/ef"));

		assertFalse(isValidPath(""));
		assertFalse(isValidPath("/a"));
		assertFalse(isValidPath("a//b"));
		assertFalse(isValidPath("ab/cd//ef"));
		assertFalse(isValidPath("a/"));
		assertFalse(isValidPath("ab/cd/ef/"));
		assertFalse(isValidPath("a\u0000b"));
	}

	@SuppressWarnings("unused")
	private static boolean isValidPath(String path) {
		try {
			new DirCacheEntry(path);
			return true;
		} catch (InvalidPathException e) {
			return false;
		}
	}

	private static void checkPath(DirCacheVersion indexVersion,
			DirCacheEntry previous, String name) throws IOException {
		DirCacheEntry dce = new DirCacheEntry(name);
		long now = System.currentTimeMillis();
		long anHourAgo = now - TimeUnit.HOURS.toMillis(1);
		dce.setLastModified(Instant.ofEpochMilli(anHourAgo));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		dce.write(out, indexVersion, previous);
		byte[] raw = out.toByteArray();
		MessageDigest md0 = Constants.newMessageDigest();
		md0.update(raw);
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		MutableInteger infoAt = new MutableInteger();
		byte[] sharedInfo = new byte[raw.length];
		MessageDigest md = Constants.newMessageDigest();
		DirCacheEntry read = new DirCacheEntry(sharedInfo, infoAt, in, md,
				Instant.ofEpochMilli(now), indexVersion, previous);
		assertEquals(name,
				read.getPathString(),
				"Paths of length " + name.length() + " should match");
		assertEquals(-1, in.read(), "Should have been fully read");
		assertArrayEquals(md0.digest(),
				md.digest(),
				"Digests should match");
	}

	@Test
	void testLongPath() throws Exception {
		StringBuilder name = new StringBuilder(4094 + 16);
		for (int i = 0; i < 4094; i++) {
			name.append('a');
		}
		for (int j = 0; j < 16; j++) {
			checkPath(DirCacheVersion.DIRC_VERSION_EXTENDED, null,
					name.toString());
			name.append('b');
		}
	}

	@Test
	void testLongPathV4() throws Exception {
		StringBuilder name = new StringBuilder(4094 + 16);
		for (int i = 0; i < 4094; i++) {
			name.append('a');
		}
		DirCacheEntry previous = new DirCacheEntry(name.toString());
		for (int j = 0; j < 16; j++) {
			checkPath(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS, previous,
					name.toString());
			name.append('b');
		}
	}

	@Test
	void testShortPath() throws Exception {
		StringBuilder name = new StringBuilder(1 + 16);
		name.append('a');
		for (int j = 0; j < 16; j++) {
			checkPath(DirCacheVersion.DIRC_VERSION_EXTENDED, null,
					name.toString());
			name.append('b');
		}
	}

	@Test
	void testShortPathV4() throws Exception {
		StringBuilder name = new StringBuilder(1 + 16);
		name.append('a');
		DirCacheEntry previous = new DirCacheEntry(name.toString());
		for (int j = 0; j < 16; j++) {
			checkPath(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS, previous,
					name.toString());
			name.append('b');
		}
	}

	@Test
	void testPathV4() throws Exception {
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < 20; i++) {
			name.append('a');
		}
		DirCacheEntry previous = new DirCacheEntry(name.toString());
		for (int j = 0; j < 20; j++) {
			name.setLength(name.length() - 1);
			String newName = name.toString() + "bbb";
			checkPath(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS, previous,
					newName);
		}
	}

	@SuppressWarnings("unused")
	@Test
	void testCreate_ByStringPath() {
		assertEquals("a", new DirCacheEntry("a").getPathString());
		assertEquals("a/b", new DirCacheEntry("a/b").getPathString());

		try {
			new DirCacheEntry("/a");
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid path: /a", err.getMessage());
		}
	}

	@SuppressWarnings("unused")
	@Test
	void testCreate_ByStringPathAndStage() {
		DirCacheEntry e;

		e = new DirCacheEntry("a", 0);
		assertEquals("a", e.getPathString());
		assertEquals(0, e.getStage());

		e = new DirCacheEntry("a/b", 1);
		assertEquals("a/b", e.getPathString());
		assertEquals(1, e.getStage());

		e = new DirCacheEntry("a/c", 2);
		assertEquals("a/c", e.getPathString());
		assertEquals(2, e.getStage());

		e = new DirCacheEntry("a/d", 3);
		assertEquals("a/d", e.getPathString());
		assertEquals(3, e.getStage());

		try {
			new DirCacheEntry("/a", 1);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid path: /a", err.getMessage());
		}

		try {
			new DirCacheEntry("a", -11);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid stage -11 for path a", err.getMessage());
		}

		try {
			new DirCacheEntry("a", 4);
			fail("Incorrectly created DirCacheEntry");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid stage 4 for path a", err.getMessage());
		}
	}

	@Test
	void testSetFileMode() {
		final DirCacheEntry e = new DirCacheEntry("a");

		assertEquals(0, e.getRawMode());

		e.setFileMode(FileMode.REGULAR_FILE);
		assertSame(FileMode.REGULAR_FILE, e.getFileMode());
		assertEquals(FileMode.REGULAR_FILE.getBits(), e.getRawMode());

		e.setFileMode(FileMode.EXECUTABLE_FILE);
		assertSame(FileMode.EXECUTABLE_FILE, e.getFileMode());
		assertEquals(FileMode.EXECUTABLE_FILE.getBits(), e.getRawMode());

		e.setFileMode(FileMode.SYMLINK);
		assertSame(FileMode.SYMLINK, e.getFileMode());
		assertEquals(FileMode.SYMLINK.getBits(), e.getRawMode());

		e.setFileMode(FileMode.GITLINK);
		assertSame(FileMode.GITLINK, e.getFileMode());
		assertEquals(FileMode.GITLINK.getBits(), e.getRawMode());

		try {
			e.setFileMode(FileMode.MISSING);
			fail("incorrectly accepted FileMode.MISSING");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid mode 0 for path a", err.getMessage());
		}

		try {
			e.setFileMode(FileMode.TREE);
			fail("incorrectly accepted FileMode.TREE");
		} catch (IllegalArgumentException err) {
			assertEquals("Invalid mode 40000 for path a", err.getMessage());
		}
	}

	@Test
	void testSetStage() {
		DirCacheEntry e = new DirCacheEntry("some/path", DirCacheEntry.STAGE_1);
		e.setAssumeValid(true);
		e.setCreationTime(2L);
		e.setFileMode(FileMode.EXECUTABLE_FILE);
		e.setLastModified(EPOCH.plusMillis(3L));
		e.setLength(100L);
		e.setObjectId(ObjectId
				.fromString("0123456789012345678901234567890123456789"));
		e.setUpdateNeeded(true);
		e.setStage(DirCacheEntry.STAGE_2);

		assertTrue(e.isAssumeValid());
		assertEquals(2L, e.getCreationTime());
		assertEquals(
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				e.getObjectId());
		assertEquals(FileMode.EXECUTABLE_FILE, e.getFileMode());
		assertEquals(EPOCH.plusMillis(3L), e.getLastModifiedInstant());
		assertEquals(100L, e.getLength());
		assertEquals(DirCacheEntry.STAGE_2, e.getStage());
		assertTrue(e.isUpdateNeeded());
		assertEquals("some/path", e.getPathString());

		e.setStage(DirCacheEntry.STAGE_0);

		assertTrue(e.isAssumeValid());
		assertEquals(2L, e.getCreationTime());
		assertEquals(
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				e.getObjectId());
		assertEquals(FileMode.EXECUTABLE_FILE, e.getFileMode());
		assertEquals(EPOCH.plusMillis(3L), e.getLastModifiedInstant());
		assertEquals(100L, e.getLength());
		assertEquals(DirCacheEntry.STAGE_0, e.getStage());
		assertTrue(e.isUpdateNeeded());
		assertEquals("some/path", e.getPathString());
	}

	@Test
	void testCopyMetaDataWithStage() {
		copyMetaDataHelper(false);
	}

	@Test
	void testCopyMetaDataWithoutStage() {
		copyMetaDataHelper(true);
	}

	private static void copyMetaDataHelper(boolean keepStage) {
		DirCacheEntry e = new DirCacheEntry("some/path", DirCacheEntry.STAGE_2);
		e.setAssumeValid(false);
		e.setCreationTime(2L);
		e.setFileMode(FileMode.EXECUTABLE_FILE);
		e.setLastModified(EPOCH.plusMillis(3L));
		e.setLength(100L);
		e.setObjectId(ObjectId
				.fromString("0123456789012345678901234567890123456789"));
		e.setUpdateNeeded(true);

		DirCacheEntry f = new DirCacheEntry("someother/path",
				DirCacheEntry.STAGE_1);
		f.setAssumeValid(true);
		f.setCreationTime(10L);
		f.setFileMode(FileMode.SYMLINK);
		f.setLastModified(EPOCH.plusMillis(20L));
		f.setLength(100000000L);
		f.setObjectId(ObjectId
				.fromString("1234567890123456789012345678901234567890"));
		f.setUpdateNeeded(true);

		e.copyMetaData(f, keepStage);
		assertTrue(e.isAssumeValid());
		assertEquals(10L, e.getCreationTime());
		assertEquals(
				ObjectId.fromString("1234567890123456789012345678901234567890"),
				e.getObjectId());
		assertEquals(FileMode.SYMLINK, e.getFileMode());
		assertEquals(EPOCH.plusMillis(20L), e.getLastModifiedInstant());
		assertEquals(100000000L, e.getLength());
		if (keepStage)
			assertEquals(DirCacheEntry.STAGE_2, e.getStage());
		else
			assertEquals(DirCacheEntry.STAGE_1, e.getStage());
		assertTrue(e.isUpdateNeeded());
		assertEquals("some/path", e.getPathString());
	}
}
