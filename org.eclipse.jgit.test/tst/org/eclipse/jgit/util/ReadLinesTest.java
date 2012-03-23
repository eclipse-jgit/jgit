/*
 * Copyright (C) 2011-2012, IBM Corporation and others.
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ReadLinesTest {
	List<String> l = new ArrayList<String>();

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
