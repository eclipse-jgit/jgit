/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.submodule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleUpdateCommand}
 */
public class SubmoduleUpdateTest extends RepositoryTestCase {

	private Repository submoduleRepo;

	private Git git;

	private AnyObjectId subRepoCommit2;

	private void createSubmoduleRepo() throws IOException, GitAPIException {
		File directory = createTempDirectory("submodule_repo");
		InitCommand init = Git.init();
		init.setDirectory(directory);
		init.call();
		submoduleRepo = Git.open(directory).getRepository();
		try (Git sub = Git.wrap(submoduleRepo)) {
			// commit something
			JGitTestUtil.writeTrashFile(submoduleRepo, "commit1.txt",
					"commit 1");
			sub.add().addFilepattern("commit1.txt").call();
			sub.commit().setMessage("commit 1").call().getId();

			JGitTestUtil.writeTrashFile(submoduleRepo, "commit2.txt",
					"commit 2");
			sub.add().addFilepattern("commit2.txt").call();
			subRepoCommit2 = sub.commit().setMessage("commit 2").call().getId();
		}
	}

	private void addSubmodule(String path) throws GitAPIException {
		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		command.setPath(path);
		String uri = submoduleRepo.getDirectory().toURI().toString();
		command.setURI(uri);
		try (Repository repo = command.call()) {
			assertNotNull(repo);
		}
		git.add().addFilepattern(path).addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		git.commit().setMessage("adding submodule").call();
		recursiveDelete(new File(git.getRepository().getWorkTree(), path));
		recursiveDelete(
				new File(new File(git.getRepository().getCommonDirectory(),
						Constants.MODULES), path));
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		createSubmoduleRepo();

		git = Git.wrap(db);
		// commit something
		writeTrashFile("initial.txt", "initial");
		git.add().addFilepattern("initial.txt").call();
		git.commit().setMessage("initial commit").call();
	}

	public void updateModeClonedRestoredSubmoduleTemplate(String mode)
			throws Exception {
		String path = "sub";
		addSubmodule(path);

		StoredConfig cfg = git.getRepository().getConfig();
		if (mode != null) {
			cfg.load();
			cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
					ConfigConstants.CONFIG_KEY_UPDATE, mode);
			cfg.save();
		}
		SubmoduleUpdateCommand update = new SubmoduleUpdateCommand(db);
		update.call();
		try (Git subGit = Git.open(new File(db.getWorkTree(), path))) {
			update.call();
			assertEquals(subRepoCommit2.getName(),
					subGit.getRepository().getBranch());
		}

		recursiveDelete(new File(db.getWorkTree(), path));

		update.call();
		try (Git subGit = Git.open(new File(db.getWorkTree(), path))) {
			update.call();
			assertEquals(subRepoCommit2.getName(),
					subGit.getRepository().getBranch());
		}
	}

	@Test
	public void repositoryWithNoSubmodules() throws GitAPIException {
		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void repositoryWithSubmodule() throws Exception {

		final String path = "sub";
		addSubmodule(path);

		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> updated = command.call();
		assertNotNull(updated);
		assertEquals(1, updated.size());
		assertEquals(path, updated.iterator().next());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			try (Repository subRepo = generator.getRepository()) {
				assertNotNull(subRepo);
				assertEquals(subRepoCommit2, subRepo.resolve(Constants.HEAD));
				String worktreeDir = subRepo.getConfig().getString(
						ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_WORKTREE);
				assertEquals("../../../sub", worktreeDir);
				String gitdir = read(
						new File(subRepo.getWorkTree(), Constants.DOT_GIT));
				assertEquals("gitdir: ../.git/modules/sub", gitdir);

			}
		}
	}

	@Test
	public void repositoryWithUnconfiguredSubmodule()
			throws IOException, GitAPIException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		FileBasedConfig modulesConfig = new FileBasedConfig(
				new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
				db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		String url = "git://server/repo.git";
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		modulesConfig.save();

		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> updated = command.call();
		assertNotNull(updated);
		assertTrue(updated.isEmpty());
	}

	@Test
	public void repositoryWithInitializedSubmodule()
			throws IOException, GitAPIException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		try (Repository subRepo = Git.init().setBare(false)
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository()) {
			assertNotNull(subRepo);
		}

		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> updated = command.call();
		assertNotNull(updated);
		assertTrue(updated.isEmpty());
	}

	@Test
	public void updateModeMergeClonedRestoredSubmodule() throws Exception {
		updateModeClonedRestoredSubmoduleTemplate(
				ConfigConstants.CONFIG_KEY_MERGE);
	}

	@Test
	public void updateModeRebaseClonedRestoredSubmodule() throws Exception {
		updateModeClonedRestoredSubmoduleTemplate(
				ConfigConstants.CONFIG_KEY_REBASE);
	}

	@Test
	public void updateModeCheckoutClonedRestoredSubmodule() throws Exception {
		updateModeClonedRestoredSubmoduleTemplate(
				ConfigConstants.CONFIG_KEY_CHECKOUT);
	}

	@Test
	public void updateModeMissingClonedRestoredSubmodule() throws Exception {
		updateModeClonedRestoredSubmoduleTemplate(null);
	}

	@Test
	public void updateMode() throws Exception {
		String path = "sub";
		addSubmodule(path);

		StoredConfig cfg = git.getRepository().getConfig();
		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE,
				ConfigConstants.CONFIG_KEY_REBASE);
		cfg.save();

		SubmoduleUpdateCommand update = new SubmoduleUpdateCommand(db);
		update.call();
		try (Git subGit = Git.open(new File(db.getWorkTree(), path))) {
			CheckoutCommand checkout = subGit.checkout();
			checkout.setName("master");
			checkout.call();
			update.call();
			assertEquals("master", subGit.getRepository().getBranch());
		}

		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE,
				ConfigConstants.CONFIG_KEY_CHECKOUT);
		cfg.save();

		update.call();
		try (Git subGit = Git.open(new File(db.getWorkTree(), path))) {
			assertEquals(subRepoCommit2.getName(),
					subGit.getRepository().getBranch());
		}

		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE,
				ConfigConstants.CONFIG_KEY_MERGE);
		cfg.save();

		update.call();
		try (Git subGit = Git.open(new File(db.getWorkTree(), path))) {
			CheckoutCommand checkout = subGit.checkout();
			checkout.setName("master");
			checkout.call();
			update.call();
			assertEquals("master", subGit.getRepository().getBranch());
		}
	}
}
