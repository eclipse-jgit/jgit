/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/** Test managing the gitweb description file. */
public class DescriptionTest extends LocalDiskRepositoryTestCase {
	private static final String UNCONFIGURED = "Unnamed repository; edit this file to name it for gitweb.";

	@Test
	public void description() throws IOException {
		Repository git = createBareRepository();
		File path = new File(git.getDirectory(), "description");
		assertNull("description", git.getGitwebDescription());

		String desc = "a test repo\nfor jgit";
		git.setGitwebDescription(desc);
		assertEquals(desc + '\n', read(path));
		assertEquals(desc, git.getGitwebDescription());

		git.setGitwebDescription(null);
		assertEquals("", read(path));

		desc = "foo";
		git.setGitwebDescription(desc);
		assertEquals(desc + '\n', read(path));
		assertEquals(desc, git.getGitwebDescription());

		git.setGitwebDescription("");
		assertEquals("", read(path));

		git.setGitwebDescription(UNCONFIGURED);
		assertEquals(UNCONFIGURED + '\n', read(path));
		assertNull("description", git.getGitwebDescription());
	}
}
