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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link org.eclipse.jgit.api.SubmoduleAddCommand}
 */
public class SubmoduleAddTest extends RepositoryTestCase {

	@Test
	public void commandWithNullPath() throws GitAPIException {
		try {
			new SubmoduleAddCommand(db).setURI("uri").call().close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			assertEquals(JGitText.get().pathNotConfigured, e.getMessage());
		}
	}

	@Test
	public void commandWithEmptyPath() throws GitAPIException {
		try {
			new SubmoduleAddCommand(db).setPath("").setURI("uri").call()
					.close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			assertEquals(JGitText.get().pathNotConfigured, e.getMessage());
		}
	}

	@Test
	public void commandWithNullUri() throws GitAPIException {
		try {
			new SubmoduleAddCommand(db).setPath("sub").call().close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			assertEquals(JGitText.get().uriNotConfigured, e.getMessage());
		}
	}

	@Test
	public void commandWithEmptyUri() throws GitAPIException {
		try {
			new SubmoduleAddCommand(db).setPath("sub").setURI("").call()
					.close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			assertEquals(JGitText.get().uriNotConfigured, e.getMessage());
		}
	}

	@Test
	public void addSubmodule() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit = git.commit().setMessage("create file").call();

			SubmoduleAddCommand command = new SubmoduleAddCommand(db);
			String path = "sub";
			command.setPath(path);
			String uri = db.getDirectory().toURI().toString();
			command.setURI(uri);
			Repository repo = command.call();
			assertNotNull(repo);
			ObjectId subCommit = repo.resolve(Constants.HEAD);
			repo.close();

			SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
			assertTrue(generator.next());
			assertEquals(path, generator.getPath());
			assertEquals(commit, generator.getObjectId());
			assertEquals(uri, generator.getModulesUrl());
			assertEquals(path, generator.getModulesPath());
			assertEquals(uri, generator.getConfigUrl());
			Repository subModRepo = generator.getRepository();
			assertNotNull(subModRepo);
			assertEquals(subCommit, commit);
			subModRepo.close();

			Status status = Git.wrap(db).status().call();
			assertTrue(status.getAdded().contains(Constants.DOT_GIT_MODULES));
			assertTrue(status.getAdded().contains(path));
		}
	}

	@Test
	public void addExistentSubmodule() throws Exception {
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

		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		command.setPath(path);
		command.setURI("git://server/repo.git");
		try {
			command.call().close();
			fail("Exception not thrown");
		} catch (JGitInternalException e) {
			assertEquals(
					MessageFormat.format(JGitText.get().submoduleExists, path),
					e.getMessage());
		}
	}

	@Test
	public void addSubmoduleWithInvalidPath() throws Exception {
		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		command.setPath("-invalid-path");
		// TODO(ms) set name to a valid value in 5.1.0 and adapt expected
		// message below
		command.setURI("http://example.com/repo/x.git");
		try {
			command.call().close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			// TODO(ms) should check for submodule path, but can't set name
			// before 5.1.0
			assertEquals("Invalid submodule name '-invalid-path'",
					e.getMessage());
		}
	}

	@Test
	public void addSubmoduleWithInvalidUri() throws Exception {
		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		command.setPath("valid-path");
		command.setURI("-upstream");
		try {
			command.call().close();
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid submodule URL '-upstream'", e.getMessage());
		}
	}

	@Test
	public void addSubmoduleWithRelativeUri() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit = git.commit().setMessage("create file").call();

			SubmoduleAddCommand command = new SubmoduleAddCommand(db);
			String path = "sub";
			String uri = "./.git";
			command.setPath(path);
			command.setURI(uri);
			Repository repo = command.call();
			assertNotNull(repo);
			addRepoToClose(repo);

			SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
			assertTrue(generator.next());
			assertEquals(path, generator.getPath());
			assertEquals(commit, generator.getObjectId());
			assertEquals(uri, generator.getModulesUrl());
			assertEquals(path, generator.getModulesPath());
			String fullUri = db.getDirectory().getAbsolutePath();
			if (File.separatorChar == '\\') {
				fullUri = fullUri.replace('\\', '/');
			}
			assertEquals(fullUri, generator.getConfigUrl());
			Repository subModRepo = generator.getRepository();
			assertNotNull(subModRepo);
			assertEquals(
					fullUri,
					subModRepo
							.getConfig()
							.getString(ConfigConstants.CONFIG_REMOTE_SECTION,
									Constants.DEFAULT_REMOTE_NAME,
									ConfigConstants.CONFIG_KEY_URL));
			subModRepo.close();
			assertEquals(commit, repo.resolve(Constants.HEAD));

			Status status = Git.wrap(db).status().call();
			assertTrue(status.getAdded().contains(Constants.DOT_GIT_MODULES));
			assertTrue(status.getAdded().contains(path));
		}
	}

	@Test
	public void addSubmoduleWithExistingSubmoduleDefined() throws Exception {
		String path1 = "sub1";
		String url1 = "git://server/repo1.git";
		String path2 = "sub2";

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
				path1, ConfigConstants.CONFIG_KEY_PATH, path1);
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
				path1, ConfigConstants.CONFIG_KEY_URL, url1);
		modulesConfig.save();

		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			assertNotNull(git.commit().setMessage("create file").call());

			SubmoduleAddCommand command = new SubmoduleAddCommand(db);
			command.setPath(path2);
			String url2 = db.getDirectory().toURI().toString();
			command.setURI(url2);
			Repository r = command.call();
			assertNotNull(r);
			addRepoToClose(r);

			modulesConfig.load();
			assertEquals(path1, modulesConfig.getString(
					ConfigConstants.CONFIG_SUBMODULE_SECTION, path1,
					ConfigConstants.CONFIG_KEY_PATH));
			assertEquals(url1, modulesConfig.getString(
					ConfigConstants.CONFIG_SUBMODULE_SECTION, path1,
					ConfigConstants.CONFIG_KEY_URL));
			assertEquals(path2, modulesConfig.getString(
					ConfigConstants.CONFIG_SUBMODULE_SECTION, path2,
					ConfigConstants.CONFIG_KEY_PATH));
			assertEquals(url2, modulesConfig.getString(
					ConfigConstants.CONFIG_SUBMODULE_SECTION, path2,
					ConfigConstants.CONFIG_KEY_URL));
		}
	}
}
