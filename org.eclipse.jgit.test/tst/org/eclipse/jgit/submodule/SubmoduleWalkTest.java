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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_SUBMODULE_SECTION;
import static org.eclipse.jgit.lib.Constants.DOT_GIT_MODULES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleWalk}
 */
public class SubmoduleWalkTest extends RepositoryTestCase {
	private TestRepository<FileRepository> testDb;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository<FileRepository>(db);
	}

	@Test
	public void repositoryWithNoSubmodules() throws IOException {
		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertFalse(gen.next());
		assertNull(gen.getPath());
		assertEquals(ObjectId.zeroId(), gen.getObjectId());
	}

	@Test
	public void repositoryWithRootLevelSubmodule() throws IOException,
			ConfigInvalidException {
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

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@SuppressWarnings("resource" /* java 7 */)
	@Test
	public void repositoryWithRootLevelSubmoduleAbsoluteRef()
			throws IOException, ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		File dotGit = new File(db.getWorkTree(), path + File.separatorChar
				+ Constants.DOT_GIT);
		if (!dotGit.getParentFile().exists())
			dotGit.getParentFile().mkdirs();

		File modulesGitDir = new File(db.getDirectory(), "modules"
				+ File.separatorChar + path);
		new FileWriter(dotGit).append(
				"gitdir: " + modulesGitDir.getAbsolutePath()).close();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setWorkTree(new File(db.getWorkTree(), path));
		builder.build().create();

		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		Repository subRepo = gen.getRepository();
		addRepoToClose(subRepo);
		assertNotNull(subRepo);
		assertEquals(modulesGitDir, subRepo.getDirectory());
		assertEquals(new File(db.getWorkTree(), path), subRepo.getWorkTree());
		assertFalse(gen.next());
	}

	@SuppressWarnings("resource" /* java 7 */)
	@Test
	public void repositoryWithRootLevelSubmoduleRelativeRef()
			throws IOException, ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		File dotGit = new File(db.getWorkTree(), path + File.separatorChar
				+ Constants.DOT_GIT);
		if (!dotGit.getParentFile().exists())
			dotGit.getParentFile().mkdirs();

		File modulesGitDir = new File(db.getDirectory(), "modules"
				+ File.separatorChar + path);
		new FileWriter(dotGit).append(
				"gitdir: " + "../" + Constants.DOT_GIT + "/modules/" + path)
				.close();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setWorkTree(new File(db.getWorkTree(), path));
		builder.build().create();

		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		Repository subRepo = gen.getRepository();
		addRepoToClose(subRepo);
		assertNotNull(subRepo);
		assertEquals(modulesGitDir, subRepo.getDirectory());
		assertEquals(new File(db.getWorkTree(), path), subRepo.getWorkTree());
		assertFalse(gen.next());
	}

	@Test
	public void repositoryWithNestedSubmodule() throws IOException,
			ConfigInvalidException {
		final ObjectId id = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub/dir/final";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(id, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertNull(gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertNull(gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@Test
	public void generatorFilteredToOneOfTwoSubmodules() throws IOException {
		final ObjectId id1 = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path1 = "sub1";
		final ObjectId id2 = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1235");
		final String path2 = "sub2";
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path1) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id1);
			}
		});
		editor.add(new PathEdit(path2) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id2);
			}
		});
		editor.commit();

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		gen.setFilter(PathFilter.create(path1));
		assertTrue(gen.next());
		assertEquals(path1, gen.getPath());
		assertEquals(id1, gen.getObjectId());
		assertFalse(gen.next());
	}

	@Test
	public void indexWithGitmodules() throws Exception {
		final ObjectId subId = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";

		final Config gitmodules = new Config();
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_PATH,
				"sub");
		// Different config in the index should be overridden by the working tree.
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_URL,
				"git://example.com/bad");
		final RevBlob gitmodulesBlob = testDb.blob(gitmodules.toText());

		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_URL,
				"git://example.com/sub");
		writeTrashFile(DOT_GIT_MODULES, gitmodules.toText());

		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new PathEdit(path) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(subId);
			}
		});
		editor.add(new PathEdit(DOT_GIT_MODULES) {

			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
				ent.setObjectId(gitmodulesBlob);
			}
		});
		editor.commit();

		SubmoduleWalk gen = SubmoduleWalk.forIndex(db);
		assertTrue(gen.next());
		assertEquals(path, gen.getPath());
		assertEquals(subId, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertEquals("sub", gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertEquals("git://example.com/sub", gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@Test
	public void treeIdWithGitmodules() throws Exception {
		final ObjectId subId = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";

		final Config gitmodules = new Config();
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_PATH,
				"sub");
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_URL,
				"git://example.com/sub");

		RevCommit commit = testDb.getRevWalk().parseCommit(testDb.commit()
				.noParents()
				.add(DOT_GIT_MODULES, gitmodules.toText())
				.edit(new PathEdit(path) {

							public void apply(DirCacheEntry ent) {
								ent.setFileMode(FileMode.GITLINK);
								ent.setObjectId(subId);
							}
						})
				.create());

		SubmoduleWalk gen = SubmoduleWalk.forPath(db, commit.getTree(), "sub");
		assertEquals(path, gen.getPath());
		assertEquals(subId, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertEquals("sub", gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertEquals("git://example.com/sub", gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}

	@Test
	public void testTreeIteratorWithGitmodules() throws Exception {
		final ObjectId subId = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";

		final Config gitmodules = new Config();
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_PATH,
				"sub");
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, path, CONFIG_KEY_URL,
				"git://example.com/sub");

		RevCommit commit = testDb.getRevWalk().parseCommit(testDb.commit()
				.noParents()
				.add(DOT_GIT_MODULES, gitmodules.toText())
				.edit(new PathEdit(path) {

							public void apply(DirCacheEntry ent) {
								ent.setFileMode(FileMode.GITLINK);
								ent.setObjectId(subId);
							}
						})
				.create());

		final CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(testDb.getRevWalk().getObjectReader(), commit.getTree());
		SubmoduleWalk gen = SubmoduleWalk.forPath(db, p, "sub");
		assertEquals(path, gen.getPath());
		assertEquals(subId, gen.getObjectId());
		assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
		assertNull(gen.getConfigUpdate());
		assertNull(gen.getConfigUrl());
		assertEquals("sub", gen.getModulesPath());
		assertNull(gen.getModulesUpdate());
		assertEquals("git://example.com/sub", gen.getModulesUrl());
		assertNull(gen.getRepository());
		assertFalse(gen.next());
	}
}
