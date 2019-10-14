/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.submodule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleSyncCommand;
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
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleSyncCommand}
 */
public class SubmoduleSyncTest extends RepositoryTestCase {

	@Test
	public void repositoryWithNoSubmodules() throws GitAPIException {
		SubmoduleSyncCommand command = new SubmoduleSyncCommand(db);
		Map<String, String> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void repositoryWithSubmodule() throws Exception {
		writeTrashFile("file.txt", "content");
		Git git = Git.wrap(db);
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

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
		modulesConfig.save();

		Repository subRepo = Git.cloneRepository()
				.setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository();
		addRepoToClose(subRepo);
		assertNotNull(subRepo);

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			assertNull(generator.getConfigUrl());
			assertEquals(url, generator.getModulesUrl());
		}
		SubmoduleSyncCommand command = new SubmoduleSyncCommand(db);
		Map<String, String> synced = command.call();
		assertNotNull(synced);
		assertEquals(1, synced.size());
		Entry<String, String> module = synced.entrySet().iterator().next();
		assertEquals(path, module.getKey());
		assertEquals(url, module.getValue());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			assertEquals(url, generator.getConfigUrl());
			try (Repository subModRepository = generator.getRepository()) {
				StoredConfig submoduleConfig = subModRepository.getConfig();
				assertEquals(url,
						submoduleConfig.getString(
								ConfigConstants.CONFIG_REMOTE_SECTION,
								Constants.DEFAULT_REMOTE_NAME,
								ConfigConstants.CONFIG_KEY_URL));
			}
		}
	}

	@Test
	public void repositoryWithRelativeUriSubmodule() throws Exception {
		writeTrashFile("file.txt", "content");
		Git git = Git.wrap(db);
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

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

		String base = "git://server/repo.git";
		FileBasedConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,
				Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL,
				base);
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		String current = "git://server/repo.git";
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, current);
		modulesConfig.save();

		Repository subRepo = Git.cloneRepository()
				.setURI(db.getDirectory().toURI().toString())
				.setDirectory(new File(db.getWorkTree(), path)).call()
				.getRepository();
		assertNotNull(subRepo);
		addRepoToClose(subRepo);

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			assertNull(generator.getConfigUrl());
			assertEquals(current, generator.getModulesUrl());
		}
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, "../sub.git");
		modulesConfig.save();

		SubmoduleSyncCommand command = new SubmoduleSyncCommand(db);
		Map<String, String> synced = command.call();
		assertNotNull(synced);
		assertEquals(1, synced.size());
		Entry<String, String> module = synced.entrySet().iterator().next();
		assertEquals(path, module.getKey());
		assertEquals("git://server/sub.git", module.getValue());

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(db)) {
			assertTrue(generator.next());
			assertEquals("git://server/sub.git", generator.getConfigUrl());
			try (Repository subModRepository1 = generator.getRepository()) {
				StoredConfig submoduleConfig = subModRepository1.getConfig();
				assertEquals("git://server/sub.git",
						submoduleConfig.getString(
								ConfigConstants.CONFIG_REMOTE_SECTION,
								Constants.DEFAULT_REMOTE_NAME,
								ConfigConstants.CONFIG_KEY_URL));
			}
		}
	}
}
