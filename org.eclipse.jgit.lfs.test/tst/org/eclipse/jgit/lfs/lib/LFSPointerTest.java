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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lfs.LfsPointer;
import org.junit.Test;

/*
 * Test LfsPointer file abstraction
 */
public class LFSPointerTest {
	@Test
	public void testEncoding() throws IOException {
		final String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		AnyLongObjectId id = LongObjectId.fromString(s);
		LfsPointer ptr = new LfsPointer(id, 4);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ptr.encode(baos);
			assertEquals(
					"version https://git-lfs.github.com/spec/v1\noid sha256:"
							+ s + "\nsize 4\n",
					baos.toString(UTF_8.name()));
		}
	}
}
