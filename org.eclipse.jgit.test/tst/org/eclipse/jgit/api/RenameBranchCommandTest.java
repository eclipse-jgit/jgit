/*
 * Copyright (C) 2012, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
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
	public void renameToExisting() throws Exception {
		assertNotNull(git.branchCreate().setName("foo").call());
		assertThrows(RefAlreadyExistsException.class, () -> git.branchRename()
				.setOldName("master").setNewName("foo").call());
	}

	@Test
	public void renameToTag() throws Exception {
		Ref ref = git.tag().setName("foo").call();
		assertNotNull(ref);
		assertEquals("Unexpected tag name", Constants.R_TAGS + "foo",
				ref.getName());
		ref = git.branchRename().setNewName("foo").call();
		assertNotNull(ref);
		assertEquals("Unexpected ref name", Constants.R_HEADS + "foo",
				ref.getName());
		// Check that we can rename it back
		ref = git.branchRename().setOldName("foo").setNewName(Constants.MASTER)
				.call();
		assertNotNull(ref);
		assertEquals("Unexpected ref name",
				Constants.R_HEADS + Constants.MASTER, ref.getName());
	}

	@Test
	public void renameToStupidName() throws Exception {
		Ref ref = git.branchRename().setNewName(Constants.R_HEADS + "foo")
				.call();
		assertEquals("Unexpected ref name",
				Constants.R_HEADS + Constants.R_HEADS + "foo",
				ref.getName());
		// And check that we can rename it back to a sane name
		ref = git.branchRename().setNewName("foo").call();
		assertNotNull(ref);
		assertEquals("Unexpected ref name", Constants.R_HEADS + "foo",
				ref.getName());
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
