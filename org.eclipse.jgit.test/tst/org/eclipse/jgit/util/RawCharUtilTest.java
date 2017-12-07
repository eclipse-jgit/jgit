/*
 * Copyright (C) 2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.eclipse.jgit.util.RawCharUtil.isWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimLeadingWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RawCharUtilTest {

	/**
	 * Test method for {@link RawCharUtil#isWhitespace(byte)}.
	 */
	@Test
	public void testIsWhitespace() {
		for (byte c = -128; c < 127; c++) {
			switch (c) {
			case (byte) '\r':
			case (byte) '\n':
			case (byte) '\t':
			case (byte) ' ':
				assertTrue(isWhitespace(c));
				break;
			default:
				assertFalse(isWhitespace(c));
			}
		}
	}

	/**
	 * Test method for
	 * {@link RawCharUtil#trimTrailingWhitespace(byte[], int, int)}.
	 */
	@Test
	public void testTrimTrailingWhitespace() {
		assertEquals(0, trimTrailingWhitespace("".getBytes(US_ASCII), 0, 0));
		assertEquals(0, trimTrailingWhitespace(" ".getBytes(US_ASCII), 0, 1));
		assertEquals(1, trimTrailingWhitespace("a ".getBytes(US_ASCII), 0, 2));
		assertEquals(2, trimTrailingWhitespace(" a ".getBytes(US_ASCII), 0, 3));
		assertEquals(3, trimTrailingWhitespace("  a".getBytes(US_ASCII), 0, 3));
		assertEquals(6,
				trimTrailingWhitespace("  test   ".getBytes(US_ASCII), 2, 9));
	}

	/**
	 * Test method for
	 * {@link RawCharUtil#trimLeadingWhitespace(byte[], int, int)}.
	 */
	@Test
	public void testTrimLeadingWhitespace() {
		assertEquals(0, trimLeadingWhitespace("".getBytes(US_ASCII), 0, 0));
		assertEquals(1, trimLeadingWhitespace(" ".getBytes(US_ASCII), 0, 1));
		assertEquals(0, trimLeadingWhitespace("a ".getBytes(US_ASCII), 0, 2));
		assertEquals(1, trimLeadingWhitespace(" a ".getBytes(US_ASCII), 0, 3));
		assertEquals(2, trimLeadingWhitespace("  a".getBytes(US_ASCII), 0, 3));
		assertEquals(2,
				trimLeadingWhitespace("  test   ".getBytes(US_ASCII),
				2, 9));
	}

}
