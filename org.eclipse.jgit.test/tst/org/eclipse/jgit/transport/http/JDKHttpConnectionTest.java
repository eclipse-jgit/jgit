/*
 * Copyright (C) 2018 Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JDKHttpConnectionTest {

	private Map<String, List<String>> headers = new HashMap<>();

	private HttpURLConnection u;

	private JDKHttpConnection c;

	@BeforeEach
	public void setup() {
		u = mock(HttpURLConnection.class);
		c = new JDKHttpConnection(u);
		headers.put("ABC", asList("x"));
	}

	@Test
	void testSingle() {
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("AbC", "x");
	}

	@Test
	void testMultiple1() {
		headers.put("abc", asList("a"));
		headers.put("aBC", asList("d", "e"));
		headers.put("ABc", Collections.emptyList());
		headers.put("AbC", (List<String>) null);
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("AbC", "a", "d", "e", "x");
	}

	@Test
	void testMultiple2() {
		headers.put("ab", asList("y", "z", "z"));
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("ab", "z", "y", "z");
		assertValues("abc", "x");
		assertValues("aBc", "x");
		assertValues("AbCd");
	}

	@Test
	void testCommaSeparatedList() {
		headers.put("abc", asList("a,b,c", "d"));
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("Abc", "a,b,c", "x", "d");
	}

	private void assertValues(String key, String... values) {
		List<String> l = new LinkedList<>();
		List<String> hf = c.getHeaderFields(key);
		if (hf != null) {
			l.addAll(hf);
		}
		for (String v : values) {
			if (!l.remove(v)) {
				fail("value " + v + " not found");
			}
		}
		assertTrue(l.isEmpty(), "found unexpected entries " + l);
	}

}
