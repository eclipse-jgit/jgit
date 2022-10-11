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

import java.util.Map;

import org.junit.Test;

public class FirstCommandTest {
	@Test
	public void testClientSID() {
		String o = "0000000000000000000000000000000000000000";
		String n = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
		String r = "refs/heads/master";
		String command = o + " " + n + " " + r;
		String fl = command + "\0"
				+ "some capabilities session-id=the-clients-SID and more capabilities";
		FirstCommand fc = FirstCommand.fromLine(fl);

		Map<String, String> options = fc.getCapabilitiesWithValues();

		assertEquals("the-clients-SID", options.get("session-id"));
		assertEquals(command, fc.getLine());
	}
}
