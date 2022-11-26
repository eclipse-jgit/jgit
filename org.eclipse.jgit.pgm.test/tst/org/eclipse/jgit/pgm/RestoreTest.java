/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com>
 * Copyright (C) 2012 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class RestoreTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testRestoreNothingFromWorkingDir() throws Exception {
		try {
			execute("git restore");
			fail("Must die");
		} catch (Die e) {
			// expected, requires argument
		}
	}

	@Test
	public void testRestoreUsage() throws Exception {
		execute("git restore --help");
	}

	@Test
	public void testRestoreAFileFromIndex() throws Exception {
		writeTrashFile("greeting", "Hello, world!");

		assertArrayEquals(new String[] { "On branch master", //
						"Untracked files:", //
						"", //
				 		"\tgreeting",
						"", //
				 },	execute("git status -uall"));

		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		assertArrayEquals(new String[] { "On branch master", //
				"Changes to be committed:", //
				"", //
				"\tnew file:   greeting",
				"", //
		},	execute("git status -uall"));

		assertArrayEquals(new String[] { "" },
				execute("git restore --staged greeting"));

		assertArrayEquals(new String[] { "On branch master", //
				"Untracked files:", //
				"", //
				"\tgreeting",
				"", //
		},	execute("git status -uall"));

	}

	@Test
	public void testRestoreAFileFromWorkingDir() throws Exception {
		writeTrashFile("greeting", "Hello, world!");

		assertArrayEquals(new String[] { "On branch master", //
				"Untracked files:", //
				"", //
				"\tgreeting",
				"", //
		},	execute("git status -uall"));

		assertArrayEquals(new String[] { "" }, //
				execute("git add greeting"));

		assertArrayEquals(new String[] { "On branch master", //
				"Changes to be committed:", //
				"", //
				"\tnew file:   greeting",
				"", //
		},	execute("git status -uall"));

		execute("git commit -m \"test\"");

		assertArrayEquals(new String[] { "On branch master", //
				"", //
		},	execute("git status -uall"));

		writeTrashFile("greeting", "Hello!");

		assertArrayEquals(new String[] { "On branch master", //
				"Changes not staged for commit:", //
				"", //
				"\tmodified:   greeting",
				"", //
		},	execute("git status -uall"));

		assertArrayEquals(new String[] { "" },
				execute("git restore greeting"));

		assertArrayEquals(new String[] { "On branch master", //
				"", //
		},	execute("git status -uall"));

	}

}
