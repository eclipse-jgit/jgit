/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

public class MidxMetadataReaderTest {
	@Test
	public void load_validFile_basic_upstream() throws Exception {
		MidxMetadataReader.MidxMetadata meta = MidxMetadataReader
				.read(JGitTestUtil.getTestResourceFile("multi-pack-index.v1"));
		assertEquals(26, meta.packNames().size());

		byte[] expected = new byte[] { 15, -10, -96, 97, 76, 13, -72, 95, 106,
				15, -55, -12, 18, -13, 50, 33, 120, -113, 71, -119 };
		byte[] decoded = Base64.decode(meta.checksumB64());
		assertArrayEquals(expected, decoded);
	}
}
