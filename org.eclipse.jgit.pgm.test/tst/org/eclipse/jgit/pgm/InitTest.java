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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class InitTest extends CLIRepositoryTestCase {

	@TempDir
	public File tempFolder;

	@Test
	void testInitBare() throws Exception {
		File directory = tempFolder;

		String[] result = execute(
				"git init '" + directory.getCanonicalPath() + "' --bare");

		String[] expecteds = new String[] {
				"Initialized empty Git repository in "
						+ directory.getCanonicalPath(),
				"" };
		assertArrayEquals(expecteds, result);
	}

	@Test
	void testInitDirectory() throws Exception {
		File workDirectory = tempFolder;
		File gitDirectory = new File(workDirectory, Constants.DOT_GIT);

		String[] result = execute(
				"git init '" + workDirectory.getCanonicalPath() + "'");

		String[] expecteds = new String[] {
				"Initialized empty Git repository in "
						+ gitDirectory.getCanonicalPath(),
				"" };
		assertArrayEquals(expecteds, result);
	}

	@Test
	void testInitDirectoryInitialBranch() throws Exception {
		File workDirectory = tempFolder;
		File gitDirectory = new File(workDirectory, Constants.DOT_GIT);

		String[] result = execute(
				"git init -b main '" + workDirectory.getCanonicalPath() + "'");

		String[] expecteds = new String[] {
				"Initialized empty Git repository in "
						+ gitDirectory.getCanonicalPath(),
				"" };
		assertArrayEquals(expecteds, result);

		try (Repository repo = new FileRepository(gitDirectory)) {
			assertEquals("refs/heads/main", repo.getFullBranch());
		}
	}
}
