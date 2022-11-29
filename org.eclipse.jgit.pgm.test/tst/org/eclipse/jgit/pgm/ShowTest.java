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

public class ShowTest extends CLIRepositoryTestCase {

	private static final String NO_NEWLINE = "\\ No newline at end of file";

	@Before
	public void setup() throws Exception {
		writeTrashFile("a", "a");
		writeTrashFile("b", "b");
		execute("git add a b");
		execute("git commit -m added");
		writeTrashFile("a", "a1");
		execute("git add a");
		execute("git commit -m modified");
	}

	@Test
	public void testShow() throws Exception {
		String result = toString(execute("git show"));
		assertEquals(
				toString("commit ecdf62e777b7413fc463c20e935403d424410ab2",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330", "",
						"    modified", "", "diff --git a/a b/a",
						"index 2e65efe..59ef8d1 100644", "--- a/a", "+++ b/a",
						"@@ -1 +1 @@", "-a", NO_NEWLINE, "+a1", NO_NEWLINE),
				result);
	}

	@Test
	public void testShowNameOnly() throws Exception {
		String result = toString(execute("git show --name-only"));
		assertEquals(toString("commit ecdf62e777b7413fc463c20e935403d424410ab2",
				"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
				"Date:   Sat Aug 15 20:12:58 2009 -0330", "", "    modified",
				"a"), result);
	}

	@Test
	public void testShowNameStatus() throws Exception {
		String result = toString(execute("git show --name-status"));
		assertEquals(toString("commit ecdf62e777b7413fc463c20e935403d424410ab2",
				"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
				"Date:   Sat Aug 15 20:12:58 2009 -0330", "", "    modified",
				"M\ta"), result);
	}

	@Test
	public void testShowRaw() throws Exception {
		String result = toString(execute("git show --raw"));
		assertEquals(toString("commit ecdf62e777b7413fc463c20e935403d424410ab2",
				"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
				"Date:   Sat Aug 15 20:12:58 2009 -0330", "", "    modified",
				":100644 100644 2e65efe2a 59ef8d134 M\ta"), result);
	}

	@Test
	public void testShowRawNoAbbrev() throws Exception {
		String result = toString(execute("git show --raw --no-abbrev"));
		assertEquals(toString("commit ecdf62e777b7413fc463c20e935403d424410ab2",
				"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
				"Date:   Sat Aug 15 20:12:58 2009 -0330", "", "    modified",
				":100644 100644 2e65efe2a145dda7ee51d1741299f848e5bf752e 59ef8d134f97de87ebcac8e3a0c32d78c81e842e M\ta")
				, result);
	}
}
