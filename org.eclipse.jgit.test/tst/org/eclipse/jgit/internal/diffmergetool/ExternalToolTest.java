/*
 * Copyright (C) 2020-2021, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.diffmergetool;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS_POSIX;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

/**
 * Base test case for external merge and diff tool tests.
 */
public abstract class ExternalToolTest extends RepositoryTestCase {

	protected static final String DEFAULT_CONTENT = "line1";

	protected File localFile;

	protected File remoteFile;

	protected File mergedFile;

	protected File baseFile;

	protected File commandResult;

	protected FileElement local;

	protected FileElement remote;

	protected FileElement merged;

	protected FileElement base;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		localFile = writeTrashFile("localFile.txt", DEFAULT_CONTENT + "\n");
		localFile.deleteOnExit();
		remoteFile = writeTrashFile("remoteFile.txt", DEFAULT_CONTENT + "\n");
		remoteFile.deleteOnExit();
		mergedFile = writeTrashFile("mergedFile.txt", "");
		mergedFile.deleteOnExit();
		baseFile = writeTrashFile("baseFile.txt", "");
		baseFile.deleteOnExit();
		commandResult = writeTrashFile("commandResult.txt", "");
		commandResult.deleteOnExit();

		local = new FileElement(localFile.getAbsolutePath(), "LOCAL");
		remote = new FileElement(remoteFile.getAbsolutePath(), "REMOTE");
		merged = new FileElement(mergedFile.getAbsolutePath(), "MERGED");
		base = new FileElement(baseFile.getAbsolutePath(), "BASE");
	}

	@After
	@Override
	public void tearDown() throws Exception {
		Files.delete(localFile.toPath());
		Files.delete(remoteFile.toPath());
		Files.delete(mergedFile.toPath());
		Files.delete(baseFile.toPath());
		Files.delete(commandResult.toPath());

		super.tearDown();
	}


	protected static void assumePosixPlatform() {
		Assume.assumeTrue(
				"This test can run only in Linux tests",
				FS.DETECTED instanceof FS_POSIX);
	}
}
