/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class HttpParserTest {

	private static final String STATUS_LINE = "HTTP/1.1. 407 Authentication required";

	@Test
	void testEmpty() throws Exception {
		String[] lines = { STATUS_LINE };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertTrue(challenges.isEmpty(), "No challenges expected");
	}

	@Test
	void testRFC7235Example() throws Exception {
		// The example from RFC 7235, sec. 4.1, slightly modified ("kind"
		// argument with whitespace around '=')
		String[] lines = { STATUS_LINE,
				"WWW-Authenticate: Newauth realm=\"apps\", type=1  , kind = \t2 ",
				"   \t  title=\"Login to \\\"apps\\\"\", Basic realm=\"simple\"" };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertEquals(2, challenges.size(), "Unexpected number of challenges");
		assertNull(challenges.get(0).getToken(), "No token expected");
		assertNull(challenges.get(1).getToken(), "No token expected");
		assertEquals("Newauth", challenges.get(0).getMechanism(),
				"Unexpected mechanism");
		assertEquals("Basic", challenges.get(1).getMechanism(),
				"Unexpected mechanism");
		Map<String, String> expectedArguments = new LinkedHashMap<>();
		expectedArguments.put("realm", "apps");
		expectedArguments.put("type", "1");
		expectedArguments.put("kind", "2");
		expectedArguments.put("title", "Login to \"apps\"");
		assertEquals(expectedArguments, challenges.get(0).getArguments(),
				"Unexpected arguments");
		expectedArguments.clear();
		expectedArguments.put("realm", "simple");
		assertEquals(expectedArguments, challenges.get(1).getArguments(),
				"Unexpected arguments");
	}

	@Test
	void testMultipleHeaders() {
		String[] lines = { STATUS_LINE, "Server: Apache",
				"WWW-Authenticate: Newauth realm=\"apps\", type=1  , kind = \t2 ",
				"   \t  title=\"Login to \\\"apps\\\"\", Basic realm=\"simple\"",
				"Content-Type: text/plain",
				"WWW-Authenticate: Other 0123456789===  , YetAnother, ",
				"WWW-Authenticate: Negotiate   ",
				"WWW-Authenticate: Negotiate a87421000492aa874209af8bc028" };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertEquals(6, challenges.size(), "Unexpected number of challenges");
		assertEquals("Other", challenges.get(2).getMechanism(),
				"Mismatched challenge");
		assertEquals("0123456789===", challenges.get(2).getToken(),
				"Token expected");
		assertEquals("YetAnother", challenges.get(3).getMechanism(),
				"Mismatched challenge");
		assertNull(challenges.get(3).getToken(), "No token expected");
		assertTrue(challenges.get(3).getArguments().isEmpty(),
				"No arguments expected");
		assertEquals("Negotiate", challenges.get(4).getMechanism(),
				"Mismatched challenge");
		assertNull(challenges.get(4).getToken(), "No token expected");
		assertEquals("Negotiate", challenges.get(5).getMechanism(),
				"Mismatched challenge");
		assertEquals("a87421000492aa874209af8bc028",
				challenges.get(5).getToken(), "Token expected");
	}

	@Test
	void testStopOnEmptyLine() {
		String[] lines = { STATUS_LINE, "Server: Apache",
				"WWW-Authenticate: Newauth realm=\"apps\", type=1  , kind = \t2 ",
				"   \t  title=\"Login to \\\"apps\\\"\", Basic realm=\"simple\"",
				"Content-Type: text/plain",
				"WWW-Authenticate: Other 0123456789===", "",
				// Not headers anymore; this would be the body
				"WWW-Authenticate: Negotiate   ",
				"WWW-Authenticate: Negotiate a87421000492aa874209af8bc028" };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertEquals(3, challenges.size(), "Unexpected number of challenges");
	}
}
