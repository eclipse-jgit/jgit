/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

public class RawParseUtils_FormatTest {
	@Test
	public void testFormatBase10() throws UnsupportedEncodingException {
		byte[] b = new byte[64];
		int p;

		p = RawParseUtils.formatBase10(b, b.length, 0);
		assertEquals("0", new String(b, p, b.length - p, "UTF-8"));

		p = RawParseUtils.formatBase10(b, b.length, 42);
		assertEquals("42", new String(b, p, b.length - p, "UTF-8"));

		p = RawParseUtils.formatBase10(b, b.length, 1234);
		assertEquals("1234", new String(b, p, b.length - p, "UTF-8"));

		p = RawParseUtils.formatBase10(b, b.length, -9876);
		assertEquals("-9876", new String(b, p, b.length - p, "UTF-8"));

		p = RawParseUtils.formatBase10(b, b.length, 123456789);
		assertEquals("123456789", new String(b, p, b.length - p, "UTF-8"));
	}
}
