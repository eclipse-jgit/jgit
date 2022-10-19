/*
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.Test;

public class RawSubStringPatternTest extends RepositoryTestCase {

	@Test
	void testBoundary() {
		assertMatchResult("a", "a", 0);
		assertMatchResult("a", "abcd", 0);
		assertMatchResult("ab", "abcd", 0);
		assertMatchResult("abcd", "abcd", 0);
		assertMatchResult("bc", "abcd", 1);
		assertMatchResult("bcd", "abcd", 1);
		assertMatchResult("cd", "abcd", 2);
		assertMatchResult("d", "abcd", 3);
		assertMatchResult("abab", "abaabab", 3);
	}

	@Test
	void testNoMatches() {
		assertMatchResult("a", "", -1);
		assertMatchResult("a", "b", -1);
		assertMatchResult("abab", "abaaba", -1);
		assertMatchResult("ab", "ddda", -1);
	}

	@Test
	void testCaseInsensitive() {
		assertMatchResult("a", "A", 0);
		assertMatchResult("A", "a", 0);
		assertMatchResult("Ab", "aB", 0);
		assertMatchResult("aB", "Ab", 0);
	}

	@Test
	void testEmptyPattern() {
		assertThrows(IllegalArgumentException.class, () -> {
			assertNotNull(new RawSubStringPattern(""));
		});
	}

	private static void assertMatchResult(String pattern, String input, int position) {
		RawSubStringPattern p = new RawSubStringPattern(pattern);
		assertEquals(position, p.match(raw(input)), "Expected match result " + position + " with input "
				+ input + " for pattern " + pattern);
	}

	private static RawCharSequence raw(String text) {
		byte[] bytes = text.getBytes(UTF_8);
		return new RawCharSequence(bytes, 0, bytes.length);
	}
}
