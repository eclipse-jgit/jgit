/*
 * Copyright (c) 2021 Jörn Guy Süß
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.transport.SideBandInputStream.trimmedLines;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class SideBandInputStreamTest {

	@Test
	public void testCheckMessageBreaking() throws Exception {

		// System independent line seperator
		final String lf = lineSeparator();

		// Raw test strings
		final String s1 = "La Li Lu";
		final String s2 = "Conga Eel";
		final String s3 = "Congo Ale";
		final String s4 = "Crash Boom Bang";

		// A composite with some whitespace in the middle
		final String badDrinksCombo = s2 + " " + s3;

		// List of messages to join together
		final List<String> messageStrings = asList(" " + s1 + "\t " + lf,
				badDrinksCombo + "  \t " + lf,
				s4);

		// Joint-up test input
		final String messageString = messageStrings.stream().collect(joining());

		// Invocation
		final List<String> lines = trimmedLines(messageString);

		// Starting whitespace is gone
		assertEquals(s1, lines.get(0));

		// Inner whitespace is retained
		assertEquals(badDrinksCombo, lines.get(1));

		// Final line is retained, despite not ending in line separator.
		assertEquals(s4, lines.get(2));

	}
}
