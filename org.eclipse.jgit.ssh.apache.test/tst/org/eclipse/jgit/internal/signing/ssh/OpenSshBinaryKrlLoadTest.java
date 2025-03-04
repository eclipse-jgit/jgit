/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.signing.ssh;

import static org.junit.Assert.assertNotNull;

import java.io.BufferedInputStream;
import java.io.InputStream;

import org.junit.Test;

/**
 * Tests loading an {@link OpenSshBinaryKrl}.
 */
public class OpenSshBinaryKrlLoadTest {

	@Test
	public void testLoad() throws Exception {
		try (InputStream in = new BufferedInputStream(
				this.getClass().getResourceAsStream("krl/krl"))) {
			OpenSshBinaryKrl krl = OpenSshBinaryKrl.load(in, false);
			assertNotNull(krl);
		}
	}
}
