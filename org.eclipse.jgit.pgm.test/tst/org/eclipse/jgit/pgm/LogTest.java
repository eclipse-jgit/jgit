/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class LogTest extends CLIRepositoryTestCase {

	@Before
	public void setup() throws Exception {
		writeTrashFile("a", "a");
		writeTrashFile("b", "a");
		execute("git add a b");
		execute("git commit -m added");
	}

	@Test
	public void testLogCommitNewFile() throws Exception {
		String result = toString(execute("git log"));
		assertEquals(
				toString("commit b4680f542095a8b41ea4258a5c03b548543a817c",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330", "added"),
				result);
	}

	@Test
	public void testLogNameOnly() throws Exception {
		String result = toString(execute("git log --name-only"));
		assertEquals(
				toString("commit b4680f542095a8b41ea4258a5c03b548543a817c",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330", "added", "a",
						"b"),
				result);
	}

	@Test
	public void testDiffCommitModifiedFileNameStatus() throws Exception {
		String result = toString(execute("git log --name-status"));
		assertEquals(toString("commit b4680f542095a8b41ea4258a5c03b548543a817c",
				"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
				"Date:   Sat Aug 15 20:12:58 2009 -0330", "added", "A\ta",
				"A\tb"),
				result);
	}

	@Test
	public void testLogRaw() throws Exception {
		String result = toString(execute("git log --raw"));
		assertEquals(
				toString("commit b4680f542095a8b41ea4258a5c03b548543a817c",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330", "added",
						":000000 100644 000000000 2e65efe2a A\ta",
						":000000 100644 000000000 2e65efe2a A\tb"),
				result);
	}

	@Test
	public void testLogRawNoAbbrev() throws Exception {
		String result = toString(execute("git log --raw --no-abbrev"));
		assertEquals(
				toString("commit b4680f542095a8b41ea4258a5c03b548543a817c",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330", "added",
						":000000 100644 0000000000000000000000000000000000000000 2e65efe2a145dda7ee51d1741299f848e5bf752e A\ta",
						":000000 100644 0000000000000000000000000000000000000000 2e65efe2a145dda7ee51d1741299f848e5bf752e A\tb"),
				result);
	}
}
