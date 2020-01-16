/*
 * Copyright (C) 2016, Rüdiger Herrmann <ruediger.herrmann@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;

import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InitTest extends CLIRepositoryTestCase {

	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testInitBare() throws Exception {
		File directory = tempFolder.getRoot();

		String[] result = execute(
				"git init '" + directory.getCanonicalPath() + "' --bare");

		String[] expecteds = new String[] {
				"Initialized empty Git repository in "
						+ directory.getCanonicalPath(),
				"" };
		assertArrayEquals(expecteds, result);
	}

	@Test
	public void testInitDirectory() throws Exception {
		File workDirectory = tempFolder.getRoot();
		File gitDirectory = new File(workDirectory, Constants.DOT_GIT);

		String[] result = execute(
				"git init '" + workDirectory.getCanonicalPath() + "'");

		String[] expecteds = new String[] {
				"Initialized empty Git repository in "
						+ gitDirectory.getCanonicalPath(),
				"" };
		assertArrayEquals(expecteds, result);
	}

}
