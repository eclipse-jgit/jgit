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

import org.bouncycastle.util.encoders.Base64;
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
		String signature = new String(Base64.decode(
				"LS0tLS1CRUdJTiBQR1AgU0lHTkFUVVJFLS0tLS0KVmVyc2lvbjogQkNQRyB2"
						+ "MS42MAoKaVFFY0JBQUJDQUFHQlFKYjljVmhBQW9KRUtYKzZBeGcvNlRaZUZz"
						+ "SC8wQ1kwV1gvejdVOCs3UzVnaUZYNHdINApvcHZCd3F5dDZPWDhsZ053VHdC"
						+ "R0hGTnQ4TGRtRENDbUtvcS9Yd2tOaTNBUlZqTGhlM2dCY0tYTm9hdnZQazJa"
						+ "CmdJZzVDaGV2R2tVNGFmV0NPTUxWRVlua0NCR3cyKzg2WGhySzFQN2dUSEVr"
						+ "MVJkK1l2MVpSREpCWStmRk83eXoKdVNCdUY1UnBFWTJzSmlJdnAyN0d1Yi9y"
						+ "WTNCNU5UUi9mZU8veitiOW9pUC9mTVVocFJ3RzVLdVdVc245TlBqdwozdHZi"
						+ "Z2F3WXBVLzJVblMreG5hdk1ZNHQyZmpSWWpzb3huZFBMYjJNVVg4WDd2QzdG"
						+ "Z1dMQmxtSS9ycXVMWlZNCklRRUtram5BK2xoZWpqSzFydit1bHE0a0daSkZL"
						+ "R1lXWVloUkR3Rmc1UFRremh1ZGhOMlNHVXE1V3hxMUVnND0KPWI5T0kKLS0t"
						+ "LS1FTkQgUEdQIFNJR05BVFVSRS0tLS0t"),
				US_ASCII);
		String expectedOutcome = new String(Base64.decode(
			"LS0tLS1CRUdJTiBQR1AgU0lHTkFUVVJFLS0tLS0KIFZlcnNpb246IEJDUEcg"
					+ "djEuNjAKIAogaVFFY0JBQUJDQUFHQlFKYjljVmhBQW9KRUtYKzZBeGcvNlRa"
					+ "ZUZzSC8wQ1kwV1gvejdVOCs3UzVnaUZYNHdINAogb3B2QndxeXQ2T1g4bGdO"
					+ "d1R3QkdIRk50OExkbURDQ21Lb3EvWHdrTmkzQVJWakxoZTNnQmNLWE5vYXZ2"
					+ "UGsyWgogZ0lnNUNoZXZHa1U0YWZXQ09NTFZFWW5rQ0JHdzIrODZYaHJLMVA3"
					+ "Z1RIRWsxUmQrWXYxWlJESkJZK2ZGTzd5egogdVNCdUY1UnBFWTJzSmlJdnAy"
					+ "N0d1Yi9yWTNCNU5UUi9mZU8veitiOW9pUC9mTVVocFJ3RzVLdVdVc245TlBq"
					+ "dwogM3R2Ymdhd1lwVS8yVW5TK3huYXZNWTR0MmZqUllqc294bmRQTGIyTVVY"
					+ "OFg3dkM3RmdXTEJsbUkvcnF1TFpWTQogSVFFS2tqbkErbGhlampLMXJ2K3Vs"
					+ "cTRrR1pKRktHWVdZWWhSRHdGZzVQVGt6aHVkaE4yU0dVcTVXeHExRWc0PQog"
						+ "PWI5T0kKIC0tLS0tRU5EIFBHUCBTSUdOQVRVUkUtLS0tLQ=="),
				US_ASCII);
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
