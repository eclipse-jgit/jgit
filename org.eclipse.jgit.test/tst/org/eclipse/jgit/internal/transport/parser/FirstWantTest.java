/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.internal.transport.parser;

import static org.junit.Assert.assertEquals;
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
				Arrays.asList("no-progress", "include-tag", "ofs-delta",
						"agent=JGit/unknown"));
		assertEquals(expectedCapabilities, capabilities);
	}

	@Test
	public void testFirstWantWithoutOptions() throws PackProtocolException {
		String line = "want b9d4d1eb2f93058814480eae9e1b67550f46ee38";

		FirstWant r = FirstWant.fromLine(line);
		assertEquals("want b9d4d1eb2f93058814480eae9e1b67550f46ee38",
				r.getLine());
		assertTrue(r.getCapabilities().isEmpty());
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
				"_", "_cap", "agent=pack.age/Version");

		for (String capability: validNames) {
			FirstWant r = FirstWant.fromLine(makeFirstWantLine(capability));
			assertEquals(r.getCapabilities().size(), 1);
			assertTrue(r.getCapabilities().contains(capability));
		}
	}
}
