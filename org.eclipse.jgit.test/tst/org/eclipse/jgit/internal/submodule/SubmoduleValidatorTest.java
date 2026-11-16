/*
 * Copyright (C) 2026, David Ostrovsky and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.submodule;

import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.jgit.internal.submodule.SubmoduleValidator.SubmoduleValidationException;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SubmoduleValidatorTest {

	private File workTree;

	@Before
	public void setUp() throws Exception {
		workTree = Files.createTempDirectory("submodule-validator").toFile();
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(workTree,
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	@Test
	public void acceptsBenignCheckoutPaths() throws Exception {
		for (String path : List.of("sub", "foo/bar", ".config/x", ".github/x",
				"a/b/c")) {
			SubmoduleValidator.assertPathInTree(workTree, path);
		}
	}

	@Test
	public void rejectsDotGitAndTraversalCheckoutPaths() {
		for (String path : List.of(".", "..", "../x", "a/../../b", ".git",
				".git/hooks", ".GIT/x", "git~1/x", "foo/.git", "/abs",
				"a//b")) {
			assertThrows(path, SubmoduleValidationException.class,
					() -> SubmoduleValidator.assertPathInTree(workTree,
							path));
		}
	}

	@Test
	public void rejectsSymlinkedLeadingComponent() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		File realDir = new File(workTree, "realdir");
		realDir.mkdir();
		// "a" is a symlink; a lexically clean path "a/hooks" resolves through
		// it and must be rejected.
		FileUtils.createSymLink(new File(workTree, "a"),
				realDir.getAbsolutePath());
		assertThrows(SubmoduleValidationException.class,
				() -> SubmoduleValidator.assertPathInTree(workTree,
						"a/hooks"));
		SubmoduleValidator.assertPathInTree(workTree, "realdir/hooks");
	}
}
