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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class HttpParserTest {

	private static final String STATUS_LINE = "HTTP/1.1. 407 Authentication required";

	@Test
	public void testEmpty() throws Exception {
		String[] lines = { STATUS_LINE };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertTrue("No challenges expected", challenges.isEmpty());
	}

	@Test
	public void testRFC7235Example() throws Exception {
		// The example from RFC 7235, sec. 4.1, slightly modified ("kind"
		// argument with whitespace around '=')
		String[] lines = { STATUS_LINE,
				"WWW-Authenticate: Newauth realm=\"apps\", type=1  , kind = \t2 ",
				"   \t  title=\"Login to \\\"apps\\\"\", Basic realm=\"simple\"" };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertEquals("Unexpected number of challenges", 2, challenges.size());
		assertNull("No token expected", challenges.get(0).getToken());
		assertNull("No token expected", challenges.get(1).getToken());
		assertEquals("Unexpected mechanism", "Newauth",
				challenges.get(0).getMechanism());
		assertEquals("Unexpected mechanism", "Basic",
				challenges.get(1).getMechanism());
		Map<String, String> expectedArguments = new LinkedHashMap<>();
		expectedArguments.put("realm", "apps");
		expectedArguments.put("type", "1");
		expectedArguments.put("kind", "2");
		expectedArguments.put("title", "Login to \"apps\"");
		assertEquals("Unexpected arguments", expectedArguments,
				challenges.get(0).getArguments());
		expectedArguments.clear();
		expectedArguments.put("realm", "simple");
		assertEquals("Unexpected arguments", expectedArguments,
				challenges.get(1).getArguments());
	}

	@Test
	public void testMultipleHeaders() {
		String[] lines = { STATUS_LINE,
				"Server: Apache",
				"WWW-Authenticate: Newauth realm=\"apps\", type=1  , kind = \t2 ",
				"   \t  title=\"Login to \\\"apps\\\"\", Basic realm=\"simple\"",
				"Content-Type: text/plain",
				"WWW-Authenticate: Other 0123456789===  , YetAnother, ",
				"WWW-Authenticate: Negotiate   ",
				"WWW-Authenticate: Negotiate a87421000492aa874209af8bc028" };
		List<AuthenticationChallenge> challenges = HttpParser
				.getAuthenticationHeaders(Arrays.asList(lines),
						"WWW-Authenticate:");
		assertEquals("Unexpected number of challenges", 6, challenges.size());
		assertEquals("Mismatched challenge", "Other",
				challenges.get(2).getMechanism());
		assertEquals("Token expected", "0123456789===",
				challenges.get(2).getToken());
		assertEquals("Mismatched challenge", "YetAnother",
				challenges.get(3).getMechanism());
		assertNull("No token expected", challenges.get(3).getToken());
		assertTrue("No arguments expected",
				challenges.get(3).getArguments().isEmpty());
		assertEquals("Mismatched challenge", "Negotiate",
				challenges.get(4).getMechanism());
		assertNull("No token expected", challenges.get(4).getToken());
		assertEquals("Mismatched challenge", "Negotiate",
				challenges.get(5).getMechanism());
		assertEquals("Token expected", "a87421000492aa874209af8bc028",
				challenges.get(5).getToken());
	}

	@Test
	public void testStopOnEmptyLine() {
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
		assertEquals("Unexpected number of challenges", 3, challenges.size());
	}
}
