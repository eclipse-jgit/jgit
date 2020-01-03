/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class RawParseUtils_MatchTest {
	@Test
	public void testMatch_Equal() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("foo differ\n");
		assertTrue(RawParseUtils.match(dst, 3, src) == 3 + src.length);
	}

	@Test
	public void testMatch_NotEqual() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("a differ\n");
		assertTrue(RawParseUtils.match(dst, 2, src) < 0);
	}

	@Test
	public void testMatch_Prefix() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author A. U. Thor");
		assertTrue(RawParseUtils.match(dst, 0, src) == src.length);
		assertTrue(RawParseUtils.match(dst, 1, src) < 0);
	}

	@Test
	public void testMatch_TooSmall() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author autho");
		assertTrue(RawParseUtils.match(dst, src.length + 1, src) < 0);
	}
}
