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

public class DiffTest extends CLIRepositoryTestCase {

	private static final String NO_NEWLINE = "\\ No newline at end of file";

	@Before
	public void setup() throws Exception {
		writeTrashFile("a", "a");
		execute("git add a");
		execute("git commit -m added");
	}

	@Test
	public void testDiffCommitNewFile() throws Exception {
		writeTrashFile("a1", "a");
		String result = toString(execute("git diff"));
		assertEquals(
				toString("diff --git a/a1 b/a1", "new file mode 100644",
						"index 0000000..2e65efe", "--- /dev/null", "+++ b/a1",
						"@@ -0,0 +1 @@", "+a", NO_NEWLINE),
				result);
	}

	@Test
	public void testDiffCommitModifiedFile() throws Exception {
		writeTrashFile("a", "a1");
		String result = toString(execute("git diff"));
		assertEquals(
				toString("diff --git a/a b/a", "index 2e65efe..59ef8d1 100644",
						"--- a/a", "+++ b/a", "@@ -1 +1 @@",
						"-a", NO_NEWLINE, "+a1", NO_NEWLINE),
				result);
	}

	@Test
	public void testDiffCommitModifiedFileNameOnly() throws Exception {
		writeTrashFile("a", "a1");
		writeTrashFile("b", "b");
		String result = toString(execute("git diff --name-only"));
		assertEquals(toString("a", "b"), result);
	}

	@Test
	public void testDiffCommitModifiedFileNameStatus() throws Exception {
		writeTrashFile("a", "a1");
		writeTrashFile("b", "b");
		String result = toString(execute("git diff --name-status"));
		assertEquals(toString("M\ta", "A\tb"), result);
	}

	@Test
	public void testDiffCommitModifiedFileRaw() throws Exception {
		writeTrashFile("a", "a1");
		writeTrashFile("b", "b");
		String result = toString(execute("git diff --raw"));
		assertEquals(toString(
						":100644 100644 2e65efe2a 59ef8d134 M\ta",
						":000000 100644 000000000 63d8dbd40 A\tb"),
				result);
	}

	@Test
	public void testDiffCommitModifiedFileRawNoAbbrev() throws Exception {
		writeTrashFile("a", "a1");
		writeTrashFile("b", "b");
		String result = toString(execute("git diff --raw --no-abbrev"));
		assertEquals(toString(
				":100644 100644 2e65efe2a145dda7ee51d1741299f848e5bf752e " +
						"59ef8d134f97de87ebcac8e3a0c32d78c81e842e M\ta",
				":000000 100644 0000000000000000000000000000000000000000 " +
						"63d8dbd40c23542e740659a7168a0ce3138ea748 A\tb"),
				result);
	}
}
