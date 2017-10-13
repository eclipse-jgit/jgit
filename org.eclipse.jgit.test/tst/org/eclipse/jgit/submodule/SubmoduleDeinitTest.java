/*
 * Copyright (C) 2017, Two Sigma Open Source
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
		SubmoduleDeinitCommand command = new SubmoduleDeinitCommand(db);
		Collection<SubmoduleDeinitResult> modules = command.call();
		assertNotNull(modules);
		assertTrue(modules.isEmpty());
	}

	@Test
	public void alreadyClosedSubmodule() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(db);

		commitSubmoduleCreation(path, git);

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(db).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.ALREADY_DEINITIALIZED, result.getStatus());
	}

	@Test
	public void dirtySubmoduleBecauseUntracked() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(db);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(db).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();
		SubmoduleWalk generator;

		write(new File(submoduleDir, "untracked"), "untracked");

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(db).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.DIRTY, result.getStatus());

		generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertTrue(submoduleDir.isDirectory());
		assertNotEquals(0, submoduleDir.list().length);
	}

	@Test
	public void dirtySubmoduleBecauseNewCommit() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(db);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(db).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();
		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		generator.next();

		//want to create a commit inside the repo...
		Repository submoduleLocalRepo = generator.getRepository();
		JGitTestUtil.writeTrashFile(submoduleLocalRepo, "file.txt", "new data");
		Git.wrap(submoduleLocalRepo).commit().setAll(true).setMessage("local commit").call();

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(db).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.DIRTY, result.getStatus());

		generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertTrue(submoduleDir.isDirectory());
		assertNotEquals(0, submoduleDir.list().length);
	}

	private File assertSubmoduleIsInitialized() throws IOException {
		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		File submoduleDir = new File(db.getWorkTree(), generator.getPath());
		assertTrue(submoduleDir.isDirectory());
		assertNotEquals(0, submoduleDir.list().length);
		return submoduleDir;
	}

	@Test
	public void dirtySubmoduleWithForce() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(db);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(db).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();

		write(new File(submoduleDir, "untracked"), "untracked");

		SubmoduleDeinitCommand command = new SubmoduleDeinitCommand(db).addPath("sub").setForce(true);
		SubmoduleDeinitResult result = runDeinit(command);
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.FORCED, result.getStatus());

		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertTrue(submoduleDir.isDirectory());
		assertEquals(0, submoduleDir.list().length);
	}

	@Test
	public void cleanSubmodule() throws Exception {
		final String path = "sub";
		Git git = Git.wrap(db);

		commitSubmoduleCreation(path, git);

		Collection<String> updated = new SubmoduleUpdateCommand(db).addPath(path).setFetch(false).call();
		assertEquals(1, updated.size());

		File submoduleDir = assertSubmoduleIsInitialized();

		SubmoduleDeinitResult result = runDeinit(new SubmoduleDeinitCommand(db).addPath("sub"));
		assertEquals(path, result.getPath());
		assertEquals(SubmoduleDeinitCommand.SubmoduleDeinitStatus.SUCCESS, result.getStatus());

		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
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

		new File(db.getWorkTree(), "sub").mkdir();
		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		git.commit().setMessage("create submodule").call();
		return commit;
	}
}
