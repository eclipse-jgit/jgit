/*
 * Copyright (C) 2022, Matthias Fromme <mfromme@dspace.de>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lfs.internal.LfsConnectionFactory;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.util.HttpSupport;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test if the lfs config is used in the correct way during checkout.
 *
 * Two lfs-files are created, one that comes before .gitattributes and
 * .lfsconfig in git order (".aaa.txt") and one that comes after ("zzz.txt").
 *
 * During checkout/reset it is tested if the correct version of the lfs config
 * is used.
 *
 * TODO: The current behavior seems a little bit strange/unintuitive. Some files
 * are checked out before and some after the config files. This leads to the
 * behavior, that during a single command the config changes. Since this seems
 * to be the same way in native git, the behavior is accepted for now.
 *
 */
public class LfsConfigGitTest extends RepositoryTestCase {

	private static final String SMUDGE_NAME = org.eclipse.jgit.lib.Constants.BUILTIN_FILTER_PREFIX
			+ Constants.ATTR_FILTER_DRIVER_PREFIX
			+ org.eclipse.jgit.lib.Constants.ATTR_FILTER_TYPE_SMUDGE;

	private static final String LFS_SERVER_URI1 = "https://lfs.server1/test/uri";

	private static final String EXPECTED_SERVER_URL1 = LFS_SERVER_URI1
			+ Protocol.OBJECTS_LFS_ENDPOINT;

	private static final String LFS_SERVER_URI2 = "https://lfs.server2/test/uri";

	private static final String EXPECTED_SERVER_URL2 = LFS_SERVER_URI2
			+ Protocol.OBJECTS_LFS_ENDPOINT;

	private static final String LFS_SERVER_URI3 = "https://lfs.server3/test/uri";

	private static final String EXPECTED_SERVER_URL3 = LFS_SERVER_URI3
			+ Protocol.OBJECTS_LFS_ENDPOINT;

	private static final String FAKE_LFS_POINTER1 = "version https://git-lfs.github.com/spec/v1\n"
			+ "oid sha256:6ce9fab52ee9a6c4c097def4e049c6acdeba44c99d26e83ba80adec1473c9b2d\n"
			+ "size 253952\n";

	private static final String FAKE_LFS_POINTER2 = "version https://git-lfs.github.com/spec/v1\n"
			+ "oid sha256:a4b711cd989863ae2038758a62672138347abbbae4076a7ad3a545fda7d08f82\n"
			+ "size 67072\n";

	private static List<String> checkoutURLs = new ArrayList<>();

	static class SmudgeFilterMock extends FilterCommand {
		public SmudgeFilterMock(Repository db, InputStream in,
				OutputStream out) throws IOException {
			super(in, out);
			HttpConnection lfsServerConn = LfsConnectionFactory.getLfsConnection(db,
					HttpSupport.METHOD_POST, Protocol.OPERATION_DOWNLOAD);
			checkoutURLs.add(lfsServerConn.getURL().toString());
		}

		@Override
		public int run() throws IOException {
			// Stupid no impl
			in.transferTo(out);
			return -1;
		}
	}

	@BeforeClass
	public static void installLfs() {
		FilterCommandRegistry.register(SMUDGE_NAME, SmudgeFilterMock::new);
	}

	@AfterClass
	public static void removeLfs() {
		FilterCommandRegistry.unregister(SMUDGE_NAME);
	}

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();
		// prepare the config for LFS
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge", SMUDGE_NAME);
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, "false");
		config.save();

		fileBefore = null;
		fileAfter = null;
		configFile = null;
		gitAttributesFile = null;
	}

	File fileBefore;

	File fileAfter;

	File configFile;

	File gitAttributesFile;

	private void createLfsFiles(String lfsPointer) throws Exception {
		//File to be checked out before lfs config
		String fileNameBefore = ".aaa.txt";
		fileBefore = writeTrashFile(fileNameBefore, lfsPointer);
		git.add().addFilepattern(fileNameBefore).call();

		// File to be checked out after lfs config
		String fileNameAfter = "zzz.txt";
		fileAfter = writeTrashFile(fileNameAfter, lfsPointer);
		git.add().addFilepattern(fileNameAfter).call();

		git.commit().setMessage("Commit LFS Pointer files").call();
	}


	private String addLfsConfigFiles(String lfsServerUrl) throws Exception {
		// Add config files to the repo
		String lfsConfig1 = createLfsConfig(lfsServerUrl);
		git.add().addFilepattern(Constants.DOT_LFS_CONFIG).call();
		// Modify gitattributes on second call, to force checkout too.
		if (gitAttributesFile == null) {
			gitAttributesFile = writeTrashFile(".gitattributes",
				"*.txt filter=lfs");
		} else {
			gitAttributesFile = writeTrashFile(".gitattributes",
					"*.txt filter=lfs\n");
		}

		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("Commit config files").call();
		return lfsConfig1;
	}

	private String createLfsConfig(String lfsServerUrl) throws IOException {
		String lfsConfig1 = "[lfs]\n    url = " + lfsServerUrl;
		configFile = writeTrashFile(Constants.DOT_LFS_CONFIG, lfsConfig1);
		return lfsConfig1;
	}

	@Test
	public void checkoutLfsObjects_reset() throws Exception {
		createLfsFiles(FAKE_LFS_POINTER1);
		String lfsConfig1 = addLfsConfigFiles(LFS_SERVER_URI1);

		// Delete files to force action on reset
		assertTrue(configFile.delete());
		assertTrue(fileBefore.delete());
		assertTrue(fileAfter.delete());

		assertTrue(gitAttributesFile.delete());

		// create config file with different url
		createLfsConfig(LFS_SERVER_URI3);

		checkoutURLs.clear();
		git.reset().setMode(ResetType.HARD).call();

		checkFile(configFile, lfsConfig1);
		checkFile(fileBefore, FAKE_LFS_POINTER1);
		checkFile(fileAfter, FAKE_LFS_POINTER1);

		assertEquals(2, checkoutURLs.size());
		// TODO: Should may be EXPECTED_SERVR_URL1
		assertEquals(EXPECTED_SERVER_URL3, checkoutURLs.get(0));
		assertEquals(EXPECTED_SERVER_URL1, checkoutURLs.get(1));
	}

	@Test
	public void checkoutLfsObjects_BranchSwitch() throws Exception {
		// Create a new branch "URL1" and add config files
		git.checkout().setCreateBranch(true).setName("URL1").call();

		createLfsFiles(FAKE_LFS_POINTER1);
		String lfsConfig1 = addLfsConfigFiles(LFS_SERVER_URI1);

		// Create a second new branch "URL2" and add config files
		git.checkout().setCreateBranch(true).setName("URL2").call();

		createLfsFiles(FAKE_LFS_POINTER2);
		String lfsConfig2 = addLfsConfigFiles(LFS_SERVER_URI2);

		checkFile(configFile, lfsConfig2);
		checkFile(fileBefore, FAKE_LFS_POINTER2);
		checkFile(fileAfter, FAKE_LFS_POINTER2);

		checkoutURLs.clear();
		git.checkout().setName("URL1").call();

		checkFile(configFile, lfsConfig1);
		checkFile(fileBefore, FAKE_LFS_POINTER1);
		checkFile(fileAfter, FAKE_LFS_POINTER1);

		assertEquals(2, checkoutURLs.size());
		// TODO: Should may be EXPECTED_SERVR_URL1
		assertEquals(EXPECTED_SERVER_URL2, checkoutURLs.get(0));
		assertEquals(EXPECTED_SERVER_URL1, checkoutURLs.get(1));

		checkoutURLs.clear();
		git.checkout().setName("URL2").call();

		checkFile(configFile, lfsConfig2);
		checkFile(fileBefore, FAKE_LFS_POINTER2);
		checkFile(fileAfter, FAKE_LFS_POINTER2);

		assertEquals(2, checkoutURLs.size());
		// TODO: Should may be EXPECTED_SERVR_URL2
		assertEquals(EXPECTED_SERVER_URL1, checkoutURLs.get(0));
		assertEquals(EXPECTED_SERVER_URL2, checkoutURLs.get(1));
	}

	@Test
	public void checkoutLfsObjects_BranchSwitch_ModifiedLocal()
			throws Exception {

		// Create a new branch "URL1" and add config files
		git.checkout().setCreateBranch(true).setName("URL1").call();

		createLfsFiles(FAKE_LFS_POINTER1);
		addLfsConfigFiles(LFS_SERVER_URI1);

		// Create a second new branch "URL2" and add config files
		git.checkout().setCreateBranch(true).setName("URL2").call();

		createLfsFiles(FAKE_LFS_POINTER2);
		addLfsConfigFiles(LFS_SERVER_URI1);

		// create config file with different url
		assertTrue(configFile.delete());
		String lfsConfig3 = createLfsConfig(LFS_SERVER_URI3);

		checkFile(configFile, lfsConfig3);
		checkFile(fileBefore, FAKE_LFS_POINTER2);
		checkFile(fileAfter, FAKE_LFS_POINTER2);

		checkoutURLs.clear();
		git.checkout().setName("URL1").call();

		checkFile(fileBefore, FAKE_LFS_POINTER1);
		checkFile(fileAfter, FAKE_LFS_POINTER1);
		checkFile(configFile, lfsConfig3);

		assertEquals(2, checkoutURLs.size());

		assertEquals(EXPECTED_SERVER_URL3, checkoutURLs.get(0));
		assertEquals(EXPECTED_SERVER_URL3, checkoutURLs.get(1));

		checkoutURLs.clear();
		git.checkout().setName("URL2").call();

		checkFile(fileBefore, FAKE_LFS_POINTER2);
		checkFile(fileAfter, FAKE_LFS_POINTER2);
		checkFile(configFile, lfsConfig3);

		assertEquals(2, checkoutURLs.size());
		assertEquals(EXPECTED_SERVER_URL3, checkoutURLs.get(0));
		assertEquals(EXPECTED_SERVER_URL3, checkoutURLs.get(1));
	}
}
