/*
 * Copyright (C) 2011, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.junit.After;
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

	@After
	public void tearDown() {
		SystemReader.setInstance(null);
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
		String date = new GitDateFormatter(Format.LOCALE).formatDate(ident);
		assertTrue("Sep 20, 2011 7:09:25 PM -0400".equals(date)
				|| "Sep 20, 2011, 7:09:25 PM -0400".equals(date)); // JDK-8206961
	}

	@Test
	public void LOCALELOCAL() {
		String date = new GitDateFormatter(Format.LOCALELOCAL)
				.formatDate(ident);
		assertTrue("Sep 20, 2011 7:39:25 PM".equals(date)
				|| "Sep 20, 2011, 7:39:25 PM".equals(date)); // JDK-8206961
	}
}
