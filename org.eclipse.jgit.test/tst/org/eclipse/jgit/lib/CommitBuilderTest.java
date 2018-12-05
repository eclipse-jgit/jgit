/*
 * Copyright (C) 2018, Salesforce.
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
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.junit.Test;

public class CommitBuilderTest {

	private void assertGpgSignatureStringOutcome(String signature,
			String expectedOutcome) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CommitBuilder.writeGpgSignatureString(signature, out);
		String formatted_signature = new String(out.toByteArray(), US_ASCII);
		assertEquals(expectedOutcome, formatted_signature);
	}

	@Test
	public void writeGpgSignatureString_1() throws Exception {
		// @formatter:off
		String signature = "-----BEGIN PGP SIGNATURE-----\n" +
				"Version: BCPG v1.60\n" +
				"\n" +
				"iQEcBAABCAAGBQJb9cVhAAoJEKX+6Axg/6TZeFsH/0CY0WX/z7U8+7S5giFX4wH4\n" +
				"opvBwqyt6OX8lgNwTwBGHFNt8LdmDCCmKoq/XwkNi3ARVjLhe3gBcKXNoavvPk2Z\n" +
				"gIg5ChevGkU4afWCOMLVEYnkCBGw2+86XhrK1P7gTHEk1Rd+Yv1ZRDJBY+fFO7yz\n" +
				"uSBuF5RpEY2sJiIvp27Gub/rY3B5NTR/feO/z+b9oiP/fMUhpRwG5KuWUsn9NPjw\n" +
				"3tvbgawYpU/2UnS+xnavMY4t2fjRYjsoxndPLb2MUX8X7vC7FgWLBlmI/rquLZVM\n" +
				"IQEKkjnA+lhejjK1rv+ulq4kGZJFKGYWYYhRDwFg5PTkzhudhN2SGUq5Wxq1Eg4=\n" +
				"=b9OI\n" +
				"-----END PGP SIGNATURE-----";
		String expectedOutcome = "-----BEGIN PGP SIGNATURE-----\n" +
				" Version: BCPG v1.60\n" +
				" \n" +
				" iQEcBAABCAAGBQJb9cVhAAoJEKX+6Axg/6TZeFsH/0CY0WX/z7U8+7S5giFX4wH4\n" +
				" opvBwqyt6OX8lgNwTwBGHFNt8LdmDCCmKoq/XwkNi3ARVjLhe3gBcKXNoavvPk2Z\n" +
				" gIg5ChevGkU4afWCOMLVEYnkCBGw2+86XhrK1P7gTHEk1Rd+Yv1ZRDJBY+fFO7yz\n" +
				" uSBuF5RpEY2sJiIvp27Gub/rY3B5NTR/feO/z+b9oiP/fMUhpRwG5KuWUsn9NPjw\n" +
				" 3tvbgawYpU/2UnS+xnavMY4t2fjRYjsoxndPLb2MUX8X7vC7FgWLBlmI/rquLZVM\n" +
				" IQEKkjnA+lhejjK1rv+ulq4kGZJFKGYWYYhRDwFg5PTkzhudhN2SGUq5Wxq1Eg4=\n" +
				" =b9OI\n" +
				" -----END PGP SIGNATURE-----";
		// @formatter:on
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void writeGpgSignatureString_failsForNonAscii() throws Exception {
		String signature = "Ü Ä";
		try {
			CommitBuilder.writeGpgSignatureString(signature,
					new ByteArrayOutputStream());
			fail("Exception expected");
		} catch (IllegalArgumentException e) {
			// good
			String message = MessageFormat.format(JGitText.get().notASCIIString,
					signature);
			assertEquals(message, e.getMessage());
		}
	}

	@Test
	public void writeGpgSignatureString_oneLineNotModified() throws Exception {
		String signature = "    A string   ";
		String expectedOutcome = signature;
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void writeGpgSignatureString_preservesRandomWhitespace()
			throws Exception {
		// @formatter:off
		String signature = "    String with    \n"
				+ "Line 2\n"
				+ " Line 3\n"
				+ "Line 4   \n"
				+ "  Line 5  ";
		String expectedOutcome = "    String with    \n"
				+ " Line 2\n"
				+ "  Line 3\n"
				+ " Line 4   \n"
				+ "   Line 5  ";
		// @formatter:on
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void writeGpgSignatureString_replaceCR() throws Exception {
		// @formatter:off
		String signature = "String with \r"
				+ "Line 2\r"
				+ "Line 3\r"
				+ "Line 4\r"
				+ "Line 5";
		String expectedOutcome = "String with \n"
				+ " Line 2\n"
				+ " Line 3\n"
				+ " Line 4\n"
				+ " Line 5";
		// @formatter:on
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void writeGpgSignatureString_replaceCRLF() throws Exception {
		// @formatter:off
		String signature = "String with \r\n"
				+ "Line 2\r\n"
				+ "Line 3\r\n"
				+ "Line 4\r\n"
				+ "Line 5";
		String expectedOutcome = "String with \n"
				+ " Line 2\n"
				+ " Line 3\n"
				+ " Line 4\n"
				+ " Line 5";
		// @formatter:on
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void writeGpgSignatureString_replaceCRLFMixed() throws Exception {
		// @formatter:off
		String signature = "String with \r"
				+ "Line 2\r\n"
				+ "Line 3\r"
				+ "Line 4\r\n"
				+ "Line 5";
		String expectedOutcome = "String with \n"
				+ " Line 2\n"
				+ " Line 3\n"
				+ " Line 4\n"
				+ " Line 5";
		// @formatter:on
		assertGpgSignatureStringOutcome(signature, expectedOutcome);
	}

	@Test
	public void setGpgSignature() throws Exception {
		GpgSignature dummy = new GpgSignature(new byte[0]);

		CommitBuilder builder = new CommitBuilder();
		assertNull(builder.getGpgSignature());

		builder.setGpgSignature(dummy);
		assertSame(dummy, builder.getGpgSignature());

		builder.setGpgSignature(null);
		assertNull(builder.getGpgSignature());
	}
}
