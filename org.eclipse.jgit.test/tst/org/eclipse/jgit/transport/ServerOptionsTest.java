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

package org.eclipse.jgit.transport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerOptionsTest {
	@Test
	public void testServerOptionLineValue() {
		ServerOptions so = new ServerOptions();
		so.add("server-option=aValue");

		assertEquals(so.getServerOptions().size(), 1);
		assertThat(so.getServerOptions(), hasItem("aValue"));
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testServerOptionLineKeyValue() {
		ServerOptions so = new ServerOptions();
		so.add("server-option=aKey=aValue");

		assertEquals(so.getServerOptions().size(), 1);
		assertThat(so.getServerOptions(), hasItem("aKey=aValue"));
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testMultipleServerOptionLines() {
		ServerOptions so = new ServerOptions();
		so.add("server-option=a");
		so.add("server-option=b");
		so.add("server-option=c");

		assertEquals(so.getServerOptions().size(), 3);
		assertThat(so.getServerOptions(), hasItems("a", "b", "c"));
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testAgentLine() {
		ServerOptions so = new ServerOptions();
		so.add("agent=unit-test");

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), is("unit-test"));
	}

	@Test
	public void testUnknownCapabilityLine() {
		ServerOptions so = new ServerOptions();
		so.add("unknown-capability");

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testEmptyServerOptionLine() {
		ServerOptions so = new ServerOptions();
		so.add("server-option=");

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testServerOptionWithoutEquals() {
		ServerOptions so = new ServerOptions();
		so.add("server-option");

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testNullLine() {
		ServerOptions so = new ServerOptions();
		so.add(null);

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), nullValue());
	}

	@Test
	public void testEmptyLine() {
		ServerOptions so = new ServerOptions();
		so.add("");

		assertEquals(so.getServerOptions().size(), 0);
		assertThat(so.getAgent(), nullValue());
	}
}
