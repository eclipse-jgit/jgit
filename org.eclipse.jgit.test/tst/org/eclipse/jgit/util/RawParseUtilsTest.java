/*
 * Copyright (C) 2011, Leonard Broman <leonard.broman@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
