/*
 * Copyright (C) 2022 Nail Samatov <sanail@yandex.ru> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.internal;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lfs.CleanFilter;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.SmudgeFilter;
import org.eclipse.jgit.lfs.errors.LfsConfigInvalidException;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class LfsConnectionFactoryTest extends RepositoryTestCase {
	private static final String SMUDGE_NAME = org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
			+ Constants.ATTR_FILTER_DRIVER_PREFIX
			+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_SMUDGE;

	private static final String CLEAN_NAME = org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
			+ Constants.ATTR_FILTER_DRIVER_PREFIX
			+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_CLEAN;

	private Git git;

	@BeforeClass
	public static void installLfs() {
		FilterCommandRegistry.register(SMUDGE_NAME, SmudgeFilter.FACTORY);
		FilterCommandRegistry.register(CLEAN_NAME, CleanFilter.FACTORY);
	}

	@AfterClass
	public static void removeLfs() {
		FilterCommandRegistry.unregister(SMUDGE_NAME);
		FilterCommandRegistry.unregister(CLEAN_NAME);
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void lfsUrlFromRemoteUrlWithDotGit() throws Exception {
		addRemoteUrl("https://localhost/repo.git");

		String lfsUrl = LfsConnectionFactory.getLfsUrl(db,
				Protocol.OPERATION_DOWNLOAD,
				new TreeMap<>());
		assertEquals("https://localhost/repo.git/info/lfs", lfsUrl);
	}

	@Test
	public void lfsUrlFromRemoteUrlWithoutDotGit() throws Exception {
		addRemoteUrl("https://localhost/repo");

		String lfsUrl = LfsConnectionFactory.getLfsUrl(db,
				Protocol.OPERATION_DOWNLOAD,
				new TreeMap<>());
		assertEquals("https://localhost/repo.git/info/lfs", lfsUrl);
	}

	@Test
	public void lfsUrlFromLocalConfig() throws Exception {
		addRemoteUrl("https://localhost/repo");

		StoredConfig cfg = db.getConfig();
		cfg.setString(ConfigConstants.CONFIG_SECTION_LFS,
				null,
				ConfigConstants.CONFIG_KEY_URL,
				"https://localhost/repo/lfs");
		cfg.save();

		String lfsUrl = LfsConnectionFactory.getLfsUrl(db,
				Protocol.OPERATION_DOWNLOAD,
				new TreeMap<>());
		assertEquals("https://localhost/repo/lfs", lfsUrl);
	}

	@Test
	public void lfsUrlFromOriginConfig() throws Exception {
		addRemoteUrl("https://localhost/repo");

		StoredConfig cfg = db.getConfig();
		cfg.setString(ConfigConstants.CONFIG_SECTION_LFS,
				org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME,
				ConfigConstants.CONFIG_KEY_URL,
				"https://localhost/repo/lfs");
		cfg.save();

		String lfsUrl = LfsConnectionFactory.getLfsUrl(db,
				Protocol.OPERATION_DOWNLOAD,
				new TreeMap<>());
		assertEquals("https://localhost/repo/lfs", lfsUrl);
	}

	@Test(expected = LfsConfigInvalidException.class)
	public void lfsUrlNotConfigured() throws Exception {
		String lfsUrl = LfsConnectionFactory.getLfsUrl(db,
				Protocol.OPERATION_DOWNLOAD,
				new TreeMap<>());
		assertEquals("https://localhost/repo/lfs", lfsUrl);
	}

	private void addRemoteUrl(String remotUrl) throws URISyntaxException, GitAPIException {
		RemoteAddCommand add = git.remoteAdd();
		add.setUri(new URIish(remotUrl));
		add.setName(org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME);
		add.call();
	}
}
