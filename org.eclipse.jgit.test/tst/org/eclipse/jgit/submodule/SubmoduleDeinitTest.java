/*
 * Copyright (C) 2017, Two Sigma Open Source and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.submodule;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleDeinitCommand;
import org.eclipse.jgit.api.SubmoduleDeinitResult;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests of {@link SubmoduleDeinitCommand}
 */
public class SubmoduleDeinitTest extends RepositoryTestCase {

	@Test
	public void repositoryWithNoSubmodules() throws GitAPIException {
		SubmoduleDeinitCommand command = new SubmoduleDeinitCommand(repository);
		Collection<SubmoduleDeinitResult> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void alreadyClosedSubmodule() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(repository);

		commitSubmoduleCreation(path, git);

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(repository).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.ALREADY_DEINITIALIZED, result.getStatus());
	}

	@Test
	public void dirtySubmoduleBecauseUntracked() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(repository);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(repository).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();

		write(new File(submoduleDir, "untracked"), "untracked");

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(repository).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.DIRTY, result.getStatus());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			assertTrue(generator.next());
		}
		assertTrue(submoduleDir.isDirectory());
		assertNotEquals(0, submoduleDir.list().length);
	}

	@Test
	public void dirtySubmoduleBecauseNewCommit() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(repository);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(repository).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();
		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			generator.next();

			// want to create a commit inside the repo...
			try (Repository submoduleLocalRepo = generator.getRepository()) {
				JGitTestUtil.writeTrashFile(submoduleLocalRepo, "file.txt",
						"new data");
				Git.wrap(submoduleLocalRepo).commit().setAll(true)
						.setMessage("local commit").call();
			}
		}
		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(repository).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.DIRTY, result.getStatus());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			assertTrue(generator.next());
		}
		assertTrue(submoduleDir.isDirectory());
		assertNotEquals(0, submoduleDir.list().length);
	}

	private File assertSubmoduleIsInitialized() throws IOException {
		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			assertTrue(generator.next());
			File submoduleDir = new File(repository.getWorkTree(), generator.getPath());
			assertTrue(submoduleDir.isDirectory());
			assertNotEquals(0, submoduleDir.list().length);
			return submoduleDir;
		}
	}

	@Test
	public void dirtySubmoduleWithForce() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(repository);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(repository).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();

		write(new File(submoduleDir, "untracked"), "untracked");

		SubmoduleDeinitCommand command = new SubmoduleDeinitCommand(repository).addPath("sub").setForce(true);
		SubmoduleDeinitResult result = runDeinit(command);
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.FORCED, result.getStatus());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			assertTrue(generator.next());
		}
		assertTrue(submoduleDir.isDirectory());
		assertEquals(0, submoduleDir.list().length);
	}

	@Test
	public void cleanSubmodule() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(repository);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(repository).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(repository).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.SUCCESS, result.getStatus());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repository)) {
			assertTrue(generator.next());
		}
		assertTrue(submoduleDir.isDirectory());
		assertEquals(0, submoduleDir.list().length);
	}

	private SubmoduleDeinitResult runDeinit(SubmoduleDeinitCommand command) throws GitAPIException {
		Collection<SubmoduleDeinitResult> deinitialized = command.call();
		assertNotNull(deinitialized);
		assertEquals(1, deinitialized.size());
		return deinitialized.iterator().next();
	}


	private RevCommit commitSubmoduleCreation(String path, Git git) throws IOException, GitAPIException {
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		final RevCommit commit = git.commit().setMessage("create file").call();

		DirCache cache = repository.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(commit);
			}
		});
		editor.commit();

		StoredConfig config = repository.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, repository.getDirectory().toURI()
						.toString());
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				repository.getWorkTree(), Constants.DOT_GIT_MODULES), repository.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.save();

		new File(repository.getWorkTree(), "sub").mkdir();
		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		git.commit().setMessage("create submodule").call();
		return commit;
	}
}
