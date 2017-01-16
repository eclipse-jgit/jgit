/*
 * Copyright (C) 2012, GitHub Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link RenameBranchCommand}
 */
public class RenameBranchCommandTest extends RepositoryTestCase {

	private static final String PATH = "file.txt";

	private RevCommit head;

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
		writeTrashFile(PATH, "content");
		git.add().addFilepattern(PATH).call();
		head = git.commit().setMessage("add file").call();
		assertNotNull(head);
	}

	@Test
	public void renameBranchNoConfigValues() throws Exception {
		StoredConfig config = git.getRepository().getConfig();
		config.unsetSection(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER);
		config.save();

		String branch = "b1";
		assertTrue(config.getNames(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER).isEmpty());
		assertNotNull(git.branchRename().setNewName(branch).call());

		config = git.getRepository().getConfig();
		assertTrue(config.getNames(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER).isEmpty());
		assertTrue(config.getNames(ConfigConstants.CONFIG_BRANCH_SECTION,
				branch).isEmpty());
	}

	@Test
	public void renameBranchSingleConfigValue() throws Exception {
		StoredConfig config = git.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
				ConfigConstants.CONFIG_KEY_REBASE, BranchRebaseMode.REBASE);
		config.save();

		String branch = "b1";

		assertEquals(BranchRebaseMode.REBASE,
				config.getEnum(BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));
		assertNull(config.getEnum(BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, branch,
				ConfigConstants.CONFIG_KEY_REBASE, null));

		assertNotNull(git.branchRename().setNewName(branch).call());

		config = git.getRepository().getConfig();
		assertNull(config.getEnum(BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
				ConfigConstants.CONFIG_KEY_REBASE, null));
		assertEquals(BranchRebaseMode.REBASE,
				config.getEnum(BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, branch,
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));
	}

	@Test
	public void renameBranchExistingSection() throws Exception {
		String branch = "b1";
		StoredConfig config = git.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
				ConfigConstants.CONFIG_KEY_REBASE, BranchRebaseMode.REBASE);
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER, "a", "a");
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, "a",
				"b");
		config.save();

		assertNotNull(git.branchRename().setNewName(branch).call());

		config = git.getRepository().getConfig();
		assertArrayEquals(new String[] { "b", "a" }, config.getStringList(
				ConfigConstants.CONFIG_BRANCH_SECTION, branch, "a"));
	}

	@Test
	public void renameBranchMultipleConfigValues() throws Exception {
		StoredConfig config = git.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
				ConfigConstants.CONFIG_KEY_REBASE, BranchRebaseMode.REBASE);
		config.setBoolean(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, true);
		config.save();

		String branch = "b1";

		assertEquals(BranchRebaseMode.REBASE,
				config.getEnum(BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));
		assertNull(config.getEnum(BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, branch,
				ConfigConstants.CONFIG_KEY_REBASE, null));
		assertTrue(config.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, true));
		assertFalse(config.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION,
				branch, ConfigConstants.CONFIG_KEY_MERGE, false));

		assertNotNull(git.branchRename().setNewName(branch).call());

		config = git.getRepository().getConfig();
		assertNull(config.getEnum(BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER,
				ConfigConstants.CONFIG_KEY_REBASE, null));
		assertEquals(BranchRebaseMode.REBASE,
				config.getEnum(BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, branch,
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));
		assertFalse(config.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, false));
		assertTrue(config.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION,
				branch, ConfigConstants.CONFIG_KEY_MERGE, false));
	}
}
