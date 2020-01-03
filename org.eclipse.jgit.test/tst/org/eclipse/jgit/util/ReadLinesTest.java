/*
 * Copyright (C) 2011-2012, IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ReadLinesTest {
	List<String> l = new ArrayList<>();

	@Before
	public void clearList() {
		l.clear();
	}

	@Test
	public void testReadLines_singleLine() {
		l.add("[0]");
		assertEquals(l, IO.readLines("[0]"));
	}

	@Test
	public void testReadLines_LF() {
		l.add("[0]");
		l.add("[1]");
		assertEquals(l, IO.readLines("[0]\n[1]"));
	}

	@Test
	public void testReadLines_CRLF() {
		l.add("[0]");
		l.add("[1]");
		assertEquals(l, IO.readLines("[0]\r\n[1]"));
	}

	@Test
	public void testReadLines_endLF() {
		l.add("[0]");
		l.add("");
		assertEquals(l, IO.readLines("[0]\n"));
	}

	@Test
	public void testReadLines_endCRLF() {
		l.add("[0]");
		l.add("");
		assertEquals(l, IO.readLines("[0]\r\n"));
	}

	@Test
	public void testReadLines_mixed() {
		l.add("[0]");
		l.add("[1]");
		l.add("[2]");
		assertEquals(l, IO.readLines("[0]\r\n[1]\n[2]"));
	}
}
