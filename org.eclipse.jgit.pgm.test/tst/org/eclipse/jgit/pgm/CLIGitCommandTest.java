/*
 * Copyright (C) 2011-2012, IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.pgm.CLIGitCommand.split;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class CLIGitCommandTest {

	@Test
	public void testSplit() throws Exception {
		assertArrayEquals(new String[0], split(""));
		assertArrayEquals(new String[] { "a" }, split("a"));
		assertArrayEquals(new String[] { "a", "b" }, split("a b"));
		assertArrayEquals(new String[] { "a", "b c" }, split("a 'b c'"));
		assertArrayEquals(new String[] { "a", "b c" }, split("a \"b c\""));
		assertArrayEquals(new String[] { "a", "b\\c" }, split("a \"b\\c\""));
	}
}
