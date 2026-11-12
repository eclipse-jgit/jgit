/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

/** Tests the disposable archive's exact-range and owner-lifetime contracts. */
public class IncomingPackSpoolTest extends RepositoryTestCase {
	@Test
	public void rangesExcludeNeighborsAtLongOffsets() throws Exception {
		try (IncomingPackSpool spool = new IncomingPackSpool(db.getObjectDatabase())) {
			long offset = Integer.MAX_VALUE + 37L;
			try (RandomAccessFile sparse = new RandomAccessFile(spool.getFile(), "rw")) {
				sparse.seek(offset);
				sparse.write(new byte[] { 9, 1, 2, 3, 8 });
			}
			try (InputStream range = spool.openRange(offset + 1, 3)) {
				byte[] bytes = new byte[3];
				assertEquals(3, range.read(bytes));
				assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
				assertEquals(-1, range.read());
			}
			try (InputStream neighbor = spool.openRange(offset + 4, 1)) {
				assertEquals(8, neighbor.read());
				assertEquals(-1, neighbor.read());
			}
			assertTrue(spool.getFile().isFile());
		}
	}

	@Test
	public void exactRangesRejectOverflowAndPostOpenTruncation() throws Exception {
		try (IncomingPackSpool spool = new IncomingPackSpool(db.getObjectDatabase())) {
			spool.append(new byte[] { 0, 1, 2, 3, 4 }, 0, 5);
			assertThrows(IllegalArgumentException.class, () -> spool.openRange(-1, 1));
			assertThrows(IllegalArgumentException.class, () -> spool.openRange(1, -1));
			assertThrows(IllegalArgumentException.class,
					() -> spool.openRange(Long.MAX_VALUE - 1, 3));
			assertThrows(EOFException.class, () -> spool.openRange(3, 3));
			try (InputStream range = spool.openRange(2, 3);
					RandomAccessFile truncator = new RandomAccessFile(spool.getFile(), "rw")) {
				truncator.setLength(3);
				assertThrows(EOFException.class, range::readAllBytes);
			}
		}
	}

	@Test
	public void closingOwnerDeletesSpoolAndInvalidatesOpenRanges() throws Exception {
		IncomingPackSpool spool = new IncomingPackSpool(db.getObjectDatabase());
		try {
			spool.append(new byte[] { 1, 2, 3 }, 0, 3);
			try (InputStream range = spool.openRange(0, 3)) {
				spool.close();
				spool.close();
				assertFalse(spool.getFile().exists());
				assertThrows(IOException.class, range::read);
				assertThrows(IOException.class, () -> spool.openRange(0, 1));
			}
		} finally {
			spool.close();
		}
	}
}
