/*
 * Copyright (C) 2011, Robin Rosenberg
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

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.junit.Before;
import org.junit.Test;

public class GitDateFormatterTest {

	private MockSystemReader mockSystemReader;

	private PersonIdent ident;

	@Before
	public void setUp() {
		mockSystemReader = new MockSystemReader() {
			@Override
			public long getCurrentTime() {
				return 1318125997291L;
			}
		};
		SystemReader.setInstance(mockSystemReader);
		ident = RawParseUtils
				.parsePersonIdent("A U Thor <author@example.com> 1316560165 -0400");
	}

	@Test
	public void DEFAULT() {
		assertEquals("Tue Sep 20 19:09:25 2011 -0400", new GitDateFormatter(
				Format.DEFAULT).formatDate(ident));
	}

	@Test
	public void RELATIVE() {
		assertEquals("3 weeks ago",
				new GitDateFormatter(Format.RELATIVE).formatDate(ident));
	}

	@Test
	public void LOCAL() {
		assertEquals("Tue Sep 20 19:39:25 2011", new GitDateFormatter(
				Format.LOCAL).formatDate(ident));
	}

	@Test
	public void ISO() {
		assertEquals("2011-09-20 19:09:25 -0400", new GitDateFormatter(
				Format.ISO).formatDate(ident));
	}

	@Test
	public void RFC() {
		assertEquals("Tue, 20 Sep 2011 19:09:25 -0400", new GitDateFormatter(
				Format.RFC).formatDate(ident));
	}

	@Test
	public void SHORT() {
		assertEquals("2011-09-20",
				new GitDateFormatter(Format.SHORT).formatDate(ident));
	}

	@Test
	public void RAW() {
		assertEquals("1316560165 -0400",
				new GitDateFormatter(Format.RAW).formatDate(ident));
	}

	@Test
	public void LOCALE() {
		assertEquals("Sep 20, 2011 7:09:25 PM -0400", new GitDateFormatter(
				Format.LOCALE).formatDate(ident));
	}

	@Test
	public void LOCALELOCAL() {
		assertEquals("Sep 20, 2011 7:39:25 PM", new GitDateFormatter(
				Format.LOCALELOCAL).formatDate(ident));
	}
}
