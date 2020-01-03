/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.PackProtocolException;
import org.junit.Test;

public class FirstWantTest {

	@Test
	public void testFirstWantWithOptions() throws PackProtocolException {
		String line = "want b9d4d1eb2f93058814480eae9e1b67550f46ee38 "
				+ "no-progress include-tag ofs-delta agent=JGit/unknown";

		FirstWant r = FirstWant.fromLine(line);
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
		Set<String> capabilities = r.getCapabilities();
		Set<String> expectedCapabilities = new HashSet<>(
				Arrays.asList("no-progress", "include-tag", "ofs-delta"));
		assertEquals(expectedCapabilities, capabilities);
		assertEquals("JGit/unknown", r.getAgent());
	}

	@Test
	public void testFirstWantWithoutOptions() throws PackProtocolException {
		String line = "want b9d4d1eb2f93058814480eae9e1b67550f46ee38";

		FirstWant r = FirstWant.fromLine(line);
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
		assertTrue(r.getCapabilities().isEmpty());
		assertNull(r.getAgent());
	}

	private String makeFirstWantLine(String capability) {
		return String.format("want b9d4d1eb2f93058814480eae9e1b67550f46ee38 %s", capability);
	}

	@Test
	public void testFirstWantNoWhitespace() {
		try {
			FirstWant.fromLine(
					"want b9d4d1eb2f93058814480eae9e1b67550f400000capability");
			fail("Accepting first want line without SP between oid and first capability");
		} catch (PackProtocolException e) {
			// pass
		}
	}

	@Test
	public void testFirstWantOnlyWhitespace() throws PackProtocolException {
		FirstWant r = FirstWant
				.fromLine("want b9d4d1eb2f93058814480eae9e1b67550f46ee38 ");
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
	}

	@Test
	public void testFirstWantValidCapabilityNames()
			throws PackProtocolException {
		List<String> validNames = Arrays.asList(
				"c", "cap", "C", "CAP", "1", "1cap", "cap-64k_test",
				"-", "-cap",
				"_", "_cap");

		for (String capability: validNames) {
			FirstWant r = FirstWant.fromLine(makeFirstWantLine(capability));
			assertEquals(r.getCapabilities().size(), 1);
			assertTrue(r.getCapabilities().contains(capability));
		}
	}

	@Test
	public void testFirstWantValidAgentName() throws PackProtocolException {
		FirstWant r = FirstWant.fromLine(makeFirstWantLine("agent=pack.age/Version"));
		assertEquals(r.getCapabilities().size(), 0);
		assertEquals("pack.age/Version", r.getAgent());
	}
}
