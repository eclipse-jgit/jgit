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
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleInitCommand}
 */
public class SubmoduleInitTest extends RepositoryTestCase {

	@Test(expected = NoWorkTreeException.class)
	public void baseRepositoryShouldFail() throws IOException {
		db = createBareRepository();
		SubmoduleInitCommand command = new SubmoduleInitCommand(db);
		command.call();
	}

	@Test
	public void repositoryWithNoSubmodules() {
		SubmoduleInitCommand command = new SubmoduleInitCommand(db);
		Collection<String> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void repositoryWithUninitializedModuleAbsoluteUrl()
			throws IOException, ConfigInvalidException {
		String absoluteUrl = "git://server/repo.git";
		checkSubmoduleInit(absoluteUrl, absoluteUrl);
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathNoRemote()
			throws IOException, ConfigInvalidException {
		String url = "../a/b";
		checkSubmoduleInit(url, fixWindowsPath(new File(db.getWorkTree()
				.getParentFile(), "a/b").getAbsolutePath())); // TODO windows only
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathBranchSpecificRemote()
			throws CorruptObjectException, IOException, ConfigInvalidException {
		String remoteUrl = "../x/y";
		String myremote = "myremote";
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, myremote,
				ConfigConstants.CONFIG_KEY_URL, remoteUrl);
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				Constants.MASTER, ConfigConstants.CONFIG_KEY_REMOTE, myremote);
		config.save();
		checkSubmoduleInit(
				"../a/b",
				fixWindowsPath(new File(new File(db.getWorkTree(), remoteUrl)
						.getParentFile(), "a/b").getAbsolutePath()));
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathDefaultRemote()
			throws CorruptObjectException, IOException, ConfigInvalidException {
		String remoteUrl = "../x/y";
		addDefaultRemote(remoteUrl);
		checkSubmoduleInit(
				"../a/b",
				fixWindowsPath(new File(new File(db.getWorkTree(), remoteUrl)
						.getParentFile(), "a/b").getAbsolutePath()));
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathDefaultRemoteWithProtocol()
			throws CorruptObjectException, IOException, ConfigInvalidException {
		String remoteUrl = "file:../x/y";
		addDefaultRemote(remoteUrl);
		checkSubmoduleInit(
				"../a/b",
				"file:"
						+ fixWindowsPath(new File(new File(db.getWorkTree(),
								remoteUrl).getParentFile(), "a/b")
								.getAbsolutePath()));
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathDefaultRemoteWithProtocolPort()
			throws CorruptObjectException, IOException, ConfigInvalidException {
		addDefaultRemote("ssh://x/y:29418");
		checkSubmoduleInit("../a/b", "ssh://x/a/b");
	}

	@Test
	public void repositoryWithUninitializedModuleRelativePathDefaultRemoteWithProtocolPort2()
			throws CorruptObjectException, IOException, ConfigInvalidException {
		addDefaultRemote("ssh://x/y:29418");
		checkSubmoduleInit("../a/b:1234", "ssh://x/a/b:1234");
	}

	private void addDefaultRemote(String remoteUrl) throws IOException,
			CorruptObjectException {
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,
				Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL,
				remoteUrl);
		config.save();
	}

	private void checkSubmoduleInit(String url, String resolvedUrl)
			throws CorruptObjectException, IOException, ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertNull(generator.getConfigUrl());
		assertNull(generator.getConfigUpdate());

		FileBasedConfig modulesConfig = new FileBasedConfig(new File(
				db.getWorkTree(), Constants.DOT_GIT_MODULES), db.getFS());
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL, url);
		String update = "rebase";
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_UPDATE, update);
		modulesConfig.save();

		SubmoduleInitCommand command = new SubmoduleInitCommand(db);
		Collection<String> modules = command.call();
		assertNotNull(modules);
		assertEquals(1, modules.size());
		assertEquals(path, modules.iterator().next());

		generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertEquals(resolvedUrl, generator.getConfigUrl());
		assertEquals(update, generator.getConfigUpdate());
	}

	private String fixWindowsPath(String path) {
		if (System.getProperty("os.name").toLowerCase().contains("windows"))
			return path.replace("\\", "/");
		else
			return path;
	}

}
