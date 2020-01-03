/*
 * Copyright (C) 2015, Andrey Loskutov <loskutov@gmx.de> and others
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
import org.junit.Test;

public class CommitTest extends CLIRepositoryTestCase {

	@Test
	public void testCommitPath() throws Exception {
		writeTrashFile("a", "a");
		writeTrashFile("b", "a");
		String result = toString(execute("git add a"));
		assertEquals("", result);

		result = toString(execute("git status -- a"));
		assertEquals(toString("On branch master", "Changes to be committed:",
				"new file:   a"), result);

		result = toString(execute("git status -- b"));
		assertEquals(toString("On branch master", "Untracked files:", "b"),
				result);

		result = toString(execute("git commit a -m 'added a'"));
		assertEquals(
				"[master 8cb3ef7e5171aaee1792df6302a5a0cd30425f7a] added a",
				result);

		result = toString(execute("git status -- a"));
		assertEquals("On branch master", result);

		result = toString(execute("git status -- b"));
		assertEquals(toString("On branch master", "Untracked files:", "b"),
				result);
	}

	@Test
	public void testCommitAll() throws Exception {
		writeTrashFile("a", "a");
		writeTrashFile("b", "a");
		String result = toString(execute("git add a b"));
		assertEquals("", result);

		result = toString(execute("git status -- a b"));
		assertEquals(toString("On branch master", "Changes to be committed:",
				"new file:   a", "new file:   b"), result);

		result = toString(execute("git commit -m 'added a b'"));
		assertEquals(
				"[master 3c93fa8e3a28ee26690498be78016edcb3a38c73] added a b",
				result);

		result = toString(execute("git status -- a b"));
		assertEquals("On branch master", result);
	}

}
