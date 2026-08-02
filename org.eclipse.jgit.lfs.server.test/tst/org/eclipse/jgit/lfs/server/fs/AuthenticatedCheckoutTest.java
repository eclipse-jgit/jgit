/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.fs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AppServerBase;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuthenticatedCheckoutTest extends LfsServerTest {

	private static final String CONTENT = "1234567";

	private static final LongObjectId ID = LongObjectId.fromString(
			"8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414");

	private CredentialsProvider defaultProvider;

	private Git git;

	private TestRepository<Repository> tdb;

	@Override
	protected ServletContextHandler configureContext(
			ServletContextHandler app) {
		return server.authBasic(app, "POST");
	}

	@Override
	@Before
	public void setup() throws Exception {
		defaultProvider = CredentialsProvider.getDefault();
		super.setup();
		CredentialsProvider.setDefault(
				new UsernamePasswordCredentialsProvider(
						AppServerBase.username, AppServerBase.password));

		BuiltinLFS.register();

		Path tmp = Files.createTempDirectory("jgit_test_");
		Repository db = FileRepositoryBuilder
				.create(tmp.resolve(".git").toFile());
		db.create();
		StoredConfig cfg = db.getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, true);
		cfg.setString(ConfigConstants.CONFIG_SECTION_LFS, null,
				ConfigConstants.CONFIG_KEY_URL,
				server.getURI().toString() + "/lfs");
		cfg.save();

		tdb = new TestRepository<>(db);
		tdb.branch("test").commit()
				.add(".gitattributes",
						"*.bin filter=lfs diff=lfs merge=lfs -text ")
				.add("a.bin", "version https://git-lfs.github.com/spec/v1\n"
						+ "oid sha256:" + ID.name() + "\nsize "
						+ CONTENT.length() + "\n")
				.create();
		git = Git.wrap(db);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		try {
			if (tdb != null) {
				tdb.getRepository().close();
				FileUtils.delete(tdb.getRepository().getWorkTree(),
						FileUtils.RECURSIVE);
			}
		} finally {
			CredentialsProvider.setDefault(defaultProvider);
			super.tearDown();
		}
	}

	@Test
	public void checkoutLfsContentWithHttpAuth() throws Exception {
		storeLfsObject();

		git.checkout().setName("test").call();

		assertEquals(CONTENT, JGitTestUtil.read(git.getRepository(), "a.bin"));
		assertEquals("[POST /lfs/objects/batch 401"
				+ ", POST /lfs/objects/batch 200"
				+ ", GET /lfs/objects/" + ID.name() + " 200]",
				server.getRequests().toString());
	}

	private void storeLfsObject() throws Exception {
		try (OutputStream out = repository.getOutputStream(ID)) {
			out.write(CONTENT.getBytes(UTF_8));
		}
	}
}
