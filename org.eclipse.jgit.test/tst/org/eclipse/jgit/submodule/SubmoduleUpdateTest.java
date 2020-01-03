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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleUpdateCommand}
 */
public class SubmoduleUpdateTest extends RepositoryTestCase {

	@Test
	public void repositoryWithNoSubmodules() throws GitAPIException {
		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void repositoryWithSubmodule() throws Exception {
		writeTrashFile("file.txt", "content");
		Git git = Git.wrap(db);
		git.add().addFilepattern("file.txt").call();
		final RevCommit commit = git.commit().setMessage("create file").call();

		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(commit);
			}
		});
		editor.commit();

		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, db.getDirectory().toURI()
						.toString());
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.save();

		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> updated = command.call();
		assertNotNull(updated);
		assertEquals(1, updated.size());
		assertEquals(path, updated.iterator().next());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			try (Repository subRepo = generator.getRepository()) {
				assertNotNull(subRepo);
				assertEquals(commit, subRepo.resolve(Constants.HEAD));
			}
		}
	}

	@Test
	public void repositoryWithUnconfiguredSubmodule() throws IOException,
			GitAPIException {
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

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		String url = "git://server/repo.git";
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		String update = "rebase";
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE, update);
		modulesConfig.save();

		SubmoduleUpdateCommand command = new SubmoduleUpdateCommand(db);
		Collection<String> updated = command.call();
		assertNotNull(updated);
		assertTrue(updated.isEmpty());
	}

	@Test
	public void repositoryWithInitializedSubmodule() throws IOException,
			GitAPIException {
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
}
