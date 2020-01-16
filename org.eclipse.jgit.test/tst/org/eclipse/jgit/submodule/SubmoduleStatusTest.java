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
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleStatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleStatusCommand}
 */
public class SubmoduleStatusTest extends RepositoryTestCase {

	@Test
	public void repositoryWithNoSubmodules() throws GitAPIException {
		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertTrue(statuses.isEmpty());
	}

	@Test
	public void repositoryWithMissingSubmodule() throws IOException,
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

		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertEquals(1, statuses.size());
		Entry<String, SubmoduleStatus> module = statuses.entrySet().iterator()
				.next();
		assertNotNull(module);
		assertEquals(path, module.getKey());
		SubmoduleStatus status = module.getValue();
		assertNotNull(status);
		assertEquals(path, status.getPath());
		assertEquals(id, status.getIndexId());
		assertEquals(SubmoduleStatusType.MISSING, status.getType());
	}

	@Test
	public void repositoryWithUninitializedSubmodule() throws IOException,
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
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, "git://server/repo.git");
		modulesConfig.save();

		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertEquals(1, statuses.size());
		Entry<String, SubmoduleStatus> module = statuses.entrySet().iterator()
				.next();
		assertNotNull(module);
		assertEquals(path, module.getKey());
		SubmoduleStatus status = module.getValue();
		assertNotNull(status);
		assertEquals(path, status.getPath());
		assertEquals(id, status.getIndexId());
		assertEquals(SubmoduleStatusType.UNINITIALIZED, status.getType());
	}

	@Test
	public void repositoryWithNoHeadInSubmodule() throws IOException,
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

		String url = "git://server/repo.git";
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		modulesConfig.save();

		Repository subRepo = Git.init().setBare(false)
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository();
		assertNotNull(subRepo);

		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertEquals(1, statuses.size());
		Entry<String, SubmoduleStatus> module = statuses.entrySet().iterator()
				.next();
		assertNotNull(module);
		assertEquals(path, module.getKey());
		SubmoduleStatus status = module.getValue();
		assertNotNull(status);
		assertEquals(path, status.getPath());
		assertEquals(id, status.getIndexId());
		assertEquals(SubmoduleStatusType.UNINITIALIZED, status.getType());
	}

	@Test
	public void repositoryWithNoSubmoduleRepository() throws IOException,
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

		String url = "git://server/repo.git";
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		modulesConfig.save();

		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertEquals(1, statuses.size());
		Entry<String, SubmoduleStatus> module = statuses.entrySet().iterator()
				.next();
		assertNotNull(module);
		assertEquals(path, module.getKey());
		SubmoduleStatus status = module.getValue();
		assertNotNull(status);
		assertEquals(path, status.getPath());
		assertEquals(id, status.getIndexId());
		assertEquals(SubmoduleStatusType.UNINITIALIZED, status.getType());
	}

	@Test
	public void repositoryWithInitializedSubmodule() throws Exception {
		String path = "sub";
		Repository subRepo = Git.init().setBare(false)
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository();
		assertNotNull(subRepo);

		ObjectId id;
		try (TestRepository<?> subTr = new TestRepository<>(subRepo)) {
			id = subTr.branch(Constants.HEAD).commit().create().copy();
		}

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

		String url = "git://server/repo.git";
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		modulesConfig.save();

		SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
		Map<String, SubmoduleStatus> statuses = command.call();
		assertNotNull(statuses);
		assertEquals(1, statuses.size());
		Entry<String, SubmoduleStatus> module = statuses.entrySet().iterator()
				.next();
		assertNotNull(module);
		assertEquals(path, module.getKey());
		SubmoduleStatus status = module.getValue();
		assertNotNull(status);
		assertEquals(path, status.getPath());
		assertEquals(id, status.getIndexId());
		assertEquals(SubmoduleStatusType.INITIALIZED, status.getType());
	}

	@Test
	public void repositoryWithDifferentRevCheckedOutSubmodule() throws Exception {
		String path = "sub";
		Repository subRepo = Git.init().setBare(false)
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository();
		assertNotNull(subRepo);

		try (TestRepository<?> subTr = new TestRepository<>(subRepo)) {
			ObjectId id = subTr.branch(Constants.HEAD).commit().create().copy();
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

			String url = "git://server/repo.git";
			StoredConfig config = db.getConfig();
			config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
					ConfigConstants.CONFIG_KEY_URL, url);
			config.save();

			FileBasedConfig modulesConfig = new FileBasedConfig(
					new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
					db.getFS());
			modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
					path, ConfigConstants.CONFIG_KEY_PATH, path);
			modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
					path, ConfigConstants.CONFIG_KEY_URL, url);
			modulesConfig.save();

			ObjectId newId = subTr.branch(Constants.HEAD).commit().create()
					.copy();

			SubmoduleStatusCommand command = new SubmoduleStatusCommand(db);
			Map<String, SubmoduleStatus> statuses = command.call();
			assertNotNull(statuses);
			assertEquals(1, statuses.size());
			Entry<String, SubmoduleStatus> module = statuses.entrySet()
					.iterator().next();
			assertNotNull(module);
			assertEquals(path, module.getKey());
			SubmoduleStatus status = module.getValue();
			assertNotNull(status);
			assertEquals(path, status.getPath());
			assertEquals(id, status.getIndexId());
			assertEquals(newId, status.getHeadId());
			assertEquals(SubmoduleStatusType.REV_CHECKED_OUT, status.getType());
		}
	}
}
