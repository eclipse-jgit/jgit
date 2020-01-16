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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_SUBMODULE_SECTION;
import static org.eclipse.jgit.lib.Constants.DOT_GIT_MODULES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link SubmoduleWalk}
 */
public class SubmoduleWalkTest extends RepositoryTestCase {
	private TestRepository<Repository> testDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository<>(db);
	}

	@Test
	public void repositoryWithNoSubmodules() throws IOException {
		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
			assertFalse(gen.next());
			assertNull(gen.getPath());
			assertEquals(ObjectId.zeroId(), gen.getObjectId());
		}
	}

	@Test
	public void bareRepositoryWithNoSubmodules() throws IOException {
		FileRepository bareRepo = createBareRepository();
		boolean result = SubmoduleWalk.containsGitModulesFile(bareRepo);
		assertFalse(result);
	}

	@Test
	public void repositoryWithRootLevelSubmodule() throws IOException,
			ConfigInvalidException, NoWorkTreeException, GitAPIException {
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

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
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
		Status status = Git.wrap(db).status().call();
		assertFalse(status.isClean());
	}

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

		File modulesGitDir = new File(db.getDirectory(),
				"modules" + File.separatorChar + path);
		try (BufferedWriter fw = Files.newBufferedWriter(dotGit.toPath(),
				UTF_8)) {
			fw.append("gitdir: " + modulesGitDir.getAbsolutePath());
		}
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setWorkTree(new File(db.getWorkTree(), path));
		builder.build().create();

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

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
			assertTrue(gen.next());
			assertEquals(path, gen.getPath());
			assertEquals(id, gen.getObjectId());
			assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
			assertNull(gen.getConfigUpdate());
			assertNull(gen.getConfigUrl());
			assertNull(gen.getModulesPath());
			assertNull(gen.getModulesUpdate());
			assertNull(gen.getModulesUrl());
			try (Repository subRepo = gen.getRepository()) {
				assertNotNull(subRepo);
				assertEquals(modulesGitDir.getAbsolutePath(),
						subRepo.getDirectory().getAbsolutePath());
				assertEquals(new File(db.getWorkTree(), path).getAbsolutePath(),
						subRepo.getWorkTree().getAbsolutePath());
			}
			assertFalse(gen.next());
		}
	}

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
		try (BufferedWriter fw = Files.newBufferedWriter(dotGit.toPath(),
				UTF_8)) {
			fw.append("gitdir: " + "../" + Constants.DOT_GIT + "/modules/"
					+ path);
		}
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		builder.setWorkTree(new File(db.getWorkTree(), path));
		builder.build().create();

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

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
			assertTrue(gen.next());
			assertEquals(path, gen.getPath());
			assertEquals(id, gen.getObjectId());
			assertEquals(new File(db.getWorkTree(), path), gen.getDirectory());
			assertNull(gen.getConfigUpdate());
			assertNull(gen.getConfigUrl());
			assertNull(gen.getModulesPath());
			assertNull(gen.getModulesUpdate());
			assertNull(gen.getModulesUrl());
			try (Repository subRepo = gen.getRepository()) {
				assertNotNull(subRepo);
				assertEqualsFile(modulesGitDir, subRepo.getDirectory());
				assertEqualsFile(new File(db.getWorkTree(), path),
						subRepo.getWorkTree());
				subRepo.close();
				assertFalse(gen.next());
			}
		}
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

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id);
			}
		});
		editor.commit();

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
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

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id1);
			}
		});
		editor.add(new PathEdit(path2) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(id2);
			}
		});
		editor.commit();

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
			gen.setFilter(PathFilter.create(path1));
			assertTrue(gen.next());
			assertEquals(path1, gen.getPath());
			assertEquals(id1, gen.getObjectId());
			assertFalse(gen.next());
		}
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

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(subId);
			}
		});
		editor.add(new PathEdit(DOT_GIT_MODULES) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
				ent.setObjectId(gitmodulesBlob);
			}
		});
		editor.commit();

		try (SubmoduleWalk gen = SubmoduleWalk.forIndex(db)) {
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

							@Override
							public void apply(DirCacheEntry ent) {
								ent.setFileMode(FileMode.GITLINK);
								ent.setObjectId(subId);
							}
						})
				.create());

		try (SubmoduleWalk gen = SubmoduleWalk.forPath(db, commit.getTree(),
				"sub")) {
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

							@Override
							public void apply(DirCacheEntry ent) {
								ent.setFileMode(FileMode.GITLINK);
								ent.setObjectId(subId);
							}
						})
				.create());

		final CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(testDb.getRevWalk().getObjectReader(), commit.getTree());
		try (SubmoduleWalk gen = SubmoduleWalk.forPath(db, p, "sub")) {
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

	@Test
	public void testTreeIteratorWithGitmodulesNameNotPath() throws Exception {
		final ObjectId subId = ObjectId
				.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
		final String path = "sub";
		final String arbitraryName = "x";

		final Config gitmodules = new Config();
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, arbitraryName,
				CONFIG_KEY_PATH, "sub");
		gitmodules.setString(CONFIG_SUBMODULE_SECTION, arbitraryName,
				CONFIG_KEY_URL, "git://example.com/sub");

		RevCommit commit = testDb.getRevWalk()
				.parseCommit(testDb.commit().noParents()
						.add(DOT_GIT_MODULES, gitmodules.toText())
						.edit(new PathEdit(path) {

							@Override
							public void apply(DirCacheEntry ent) {
								ent.setFileMode(FileMode.GITLINK);
								ent.setObjectId(subId);
							}
						}).create());

		final CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(testDb.getRevWalk().getObjectReader(), commit.getTree());
		try (SubmoduleWalk gen = SubmoduleWalk.forPath(db, p, "sub")) {
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
}
