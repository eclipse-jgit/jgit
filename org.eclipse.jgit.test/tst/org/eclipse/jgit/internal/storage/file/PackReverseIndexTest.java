/*
 * Copyright (C) 2022, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.PackReverseIndex.PackReverseIndexFactory;
import org.junit.Test;

public class PackReverseIndexTest {
	@Test
	public void read_badMagic() {
		byte[] badMagic = new byte[] { 'R', 'B', 'A', 'D', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				// pack checksum
				'P', 'A', 'C', 'K', 'C', 'H', 'E', 'C', 'K', 'S', 'U', 'M', '3',
				'4', '5', '6', '7', '8', '9', '0',
				// checksum
				0x66, 0x01, (byte) 0xbc, (byte) 0xe8, 0x51, 0x4b, 0x2f,
				(byte) 0xa1, (byte) 0xa9, (byte) 0xcd, (byte) 0xbe, (byte) 0xd6,
				0x4f, (byte) 0xa8, 0x7d, (byte) 0xab, 0x50, (byte) 0xa3,
				(byte) 0xf7, (byte) 0xcc, };
		ByteArrayInputStream in = new ByteArrayInputStream(badMagic);

		assertThrows(IOException.class,
				() -> PackReverseIndexFactory.read(in, 0, () -> null));
	}

	@Test
	public void read_unsupportedVersion2() {
		byte[] version2 = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x02, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				// pack checksum
				'P', 'A', 'C', 'K', 'C', 'H', 'E', 'C', 'K', 'S', 'U', 'M', '3',
				'4', '5', '6', '7', '8', '9', '0',
				// checksum
				0x70, 0x17, 0x10, 0x51, (byte) 0xfe, (byte) 0xab, (byte) 0x9b,
				0x68, (byte) 0xed, 0x3a, 0x3f, 0x27, 0x1d, (byte) 0xce,
				(byte) 0xff, 0x38, 0x09, (byte) 0x9b, 0x29, 0x58, };
		ByteArrayInputStream in = new ByteArrayInputStream(version2);

		assertThrows(IOException.class,
				() -> PackReverseIndexFactory.read(in, 0, () -> null));
	}
}
