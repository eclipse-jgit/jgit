/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2009, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.test.resources;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;


/** Test case which includes C Git generated pack files for testing. */
public abstract class SampleDataRepositoryTestCase extends RepositoryTestCase {
	@Override
	public void setUp() throws Exception {
		super.setUp();
		copyCGitTestPacks(db);
	}

	/**
	 * Copy C Git generated pack files into given repository for testing
	 *
	 * @param repo
	 *            test repository to receive packfile copies
	 * @throws IOException
	 */
	public static void copyCGitTestPacks(FileRepository repo) throws IOException {
		final String[] packs = {
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f",
				"pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371",
				"pack-9fb5b411fe6dfa89cc2e6b89d2bd8e5de02b5745",
				"pack-546ff360fe3488adb20860ce3436a2d6373d2796",
				"pack-cbdeda40019ae0e6e789088ea0f51f164f489d14",
				"pack-e6d07037cbcf13376308a0a995d1fa48f8f76aaa",
				"pack-3280af9c07ee18a87705ef50b0cc4cd20266cf12"
		};
		final File packDir = repo.getObjectDatabase().getPackDirectory();
		for (String n : packs) {
			JGitTestUtil.copyTestResource(n + ".pack", new File(packDir, n + ".pack"));
			JGitTestUtil.copyTestResource(n + ".idx", new File(packDir, n + ".idx"));
		}

		JGitTestUtil.copyTestResource("packed-refs",
				new File(repo.getDirectory(), "packed-refs"));
	}
}
