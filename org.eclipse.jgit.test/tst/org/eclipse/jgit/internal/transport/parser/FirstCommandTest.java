/*
 * Copyright (C) 2022, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.transport.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class FirstCommandTest {
	@Test
	public void testClientSID() {
		String oldStr = "0000000000000000000000000000000000000000";
		String newStr = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
		String refName = "refs/heads/master";
		String command = oldStr + " " + newStr + " " + refName;
		String fl = command + "\0"
				+ "some capabilities session-id=the-clients-SID and more unknownCap=some-value";
		FirstCommand fc = FirstCommand.fromLine(fl);

		Map<String, String> options = fc.getCapabilities();

		assertEquals("the-clients-SID", options.get("session-id"));
		assertEquals(command, fc.getLine());
		assertTrue(options.containsKey("unknownCap"));
		assertEquals(6, options.size());
	}
}
