/*
 * Copyright (C) 2011, Leonard Broman <leonard.broman@gmail.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RawParseUtilsTest {
	String commit = "tree e3a1035abd2b319bb01e57d69b0ba6cab289297e\n" +
		"parent 54e895b87c0768d2317a2b17062e3ad9f76a8105\n" +
		"committer A U Thor <author@xample.com 1528968566 +0200\n" +
		"gpgsig -----BEGIN PGP SIGNATURE-----\n" +
		" \n" +
		" wsBcBAABCAAQBQJbGB4pCRBK7hj4Ov3rIwAAdHIIAENrvz23867ZgqrmyPemBEZP\n" +
		" U24B1Tlq/DWvce2buaxmbNQngKZ0pv2s8VMc11916WfTIC9EKvioatmpjduWvhqj\n" +
		" znQTFyiMor30pyYsfrqFuQZvqBW01o8GEWqLg8zjf9Rf0R3LlOEw86aT8CdHRlm6\n" +
		" wlb22xb8qoX4RB+LYfz7MhK5F+yLOPXZdJnAVbuyoMGRnDpwdzjL5Hj671+XJxN5\n" +
		" SasRdhxkkfw/ZnHxaKEc4juMz8Nziz27elRwhOQqlTYoXNJnsV//wy5Losd7aKi1\n" +
		" xXXyUpndEOmT0CIcKHrN/kbYoVL28OJaxoBuva3WYQaRrzEe3X02NMxZe9gkSqA=\n" +
		" =TClh\n" +
		" -----END PGP SIGNATURE-----\n" +
		"some other header\n\n" +
		"commit message";

	@Test
	public void testParseEncoding_ISO8859_1_encoding() {
		Charset result = RawParseUtils.parseEncoding(Constants
				.encodeASCII("encoding ISO-8859-1\n"));
		assertNotNull(result);
	}

	@Test
	public void testParseEncoding_Accept_Latin_One_AsISO8859_1() {
		Charset result = RawParseUtils.parseEncoding(Constants
				.encodeASCII("encoding latin-1\n"));
		assertNotNull(result);
		assertEquals("ISO-8859-1", result.name());
	}

	@Test
	public void testParseEncoding_badEncoding() {
		try {
			RawParseUtils.parseEncoding(Constants.encodeASCII("encoding xyz\n"));
			fail("should throw an UnsupportedCharsetException: xyz");
		} catch (UnsupportedCharsetException e) {
			assertEquals("xyz", e.getMessage());
		}
	}

	@Test
	public void testHeaderStart() {
		byte[] headerName = "some".getBytes(UTF_8);
		byte[] commitBytes = commit.getBytes(UTF_8);
		assertEquals(625, RawParseUtils.headerStart(headerName, commitBytes, 0));
		assertEquals(625, RawParseUtils.headerStart(headerName, commitBytes, 4));

		byte[] missingHeaderName = "missing".getBytes(UTF_8);
		assertEquals(-1, RawParseUtils.headerStart(missingHeaderName,
							   commitBytes, 0));

		byte[] fauxHeaderName = "other".getBytes(UTF_8);
		assertEquals(-1, RawParseUtils.headerStart(fauxHeaderName, commitBytes, 625 + 4));
	}

	@Test
	public void testHeaderEnd() {
		byte[] commitBytes = commit.getBytes(UTF_8);
		int[] expected = new int[] {45, 93, 148, 619, 637};
		int start = 0;
		for (int i = 0; i < expected.length; i++) {
			start = RawParseUtils.headerEnd(commitBytes, start);
			assertEquals(expected[i], start);
			start += 1;
		}
	}
}
