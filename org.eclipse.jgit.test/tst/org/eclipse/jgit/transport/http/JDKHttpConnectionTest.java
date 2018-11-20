/*
 * Copyright (C) 2018 Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.transport.http;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class JDKHttpConnectionTest {

	private Map<String, List<String>> headers = new HashMap<>();

	private HttpURLConnection u;

	private JDKHttpConnection c;

	@Before
	public void setup() {
		u = mock(HttpURLConnection.class);
		c = new JDKHttpConnection(u);
		headers.put("ABC", asList("x"));
	}

	@Test
	public void testSingle() {
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("AbC", "x");
	}

	@Test
	public void testMultiple1() {
		headers.put("abc", asList("a"));
		headers.put("aBC", asList("d", "e"));
		headers.put("ABc", Collections.emptyList());
		headers.put("AbC", (List<String>) null);
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("AbC", "a", "d", "e", "x");
	}

	@Test
	public void testMultiple2() {
		headers.put("ab", asList("y", "z", "z"));
		when(u.getHeaderFields()).thenReturn(headers);
		assertValues("ab", "z", "y", "z");
		assertValues("abc", "x");
		assertValues("aBc", "x");
		assertValues("AbCd");
	}

	@Test
	public void testCommaSeparatedList() {
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
		assertTrue("found unexpected entries " + l, l.isEmpty());
	}

}
