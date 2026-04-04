/*
 * Copyright (C) 2026, hanweiwei and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FetchResultTest {

	@Test
	public void toStringEmpty() {
		FetchResult result = new FetchResult();
		String s = result.toString();
		assertNotNull(s);
		assertTrue(s.startsWith("FetchResult["));
		assertTrue(s.contains("0 ref update(s)"));
		assertTrue(s.endsWith("]"));
	}

	@Test
	public void toStringWithUri() throws Exception {
		FetchResult result = new FetchResult();
		result.setAdvertisedRefs(new URIish("https://example.com/repo.git"),
				java.util.Collections.emptyMap());
		String s = result.toString();
		assertTrue(s.contains("example.com"));
	}

	@Test
	public void toStringWithSubmodule() {
		FetchResult result = new FetchResult();
		result.addSubmodule("sub/path", new FetchResult());
		String s = result.toString();
		assertTrue(s.contains("1 submodule(s)"));
	}
}
