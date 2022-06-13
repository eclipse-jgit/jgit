/*
 * Copyright (c) 2019 Alex Jitianu <alex_jitianu@sync.ro> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Policy;
import java.util.Collections;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that using a SecurityManager does not result in errors logged.
 */
public class SecurityManagerMissingPermissionsTest extends RepositoryTestCase {

	/**
	 * Collects all logging sent to the logging system.
	 */
	private final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

	private SecurityManager originalSecurityManager;

	private PrintStream defaultErrorOutput;

	@Override
	@Before
	public void setUp() throws Exception {
		originalSecurityManager = System.getSecurityManager();

		// slf4j-simple logs to System.err, redirect it to enable asserting
		// logged errors
		defaultErrorOutput = System.err;
		System.setErr(new PrintStream(errorOutput));

		refreshPolicyAllPermission(Policy.getPolicy());
		System.setSecurityManager(new SecurityManager());
		super.setUp();
	}

	/**
	 * If a SecurityManager is active a lot of {@link java.io.FilePermission}
	 * errors are thrown and logged while initializing a repository.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCreateNewRepos_MissingPermissions() throws Exception {
		File wcTree = new File(getTemporaryDirectory(),
				"CreateNewRepositoryTest_testCreateNewRepos");

		File marker = new File(getTemporaryDirectory(), "marker");
		Files.write(marker.toPath(), Collections.singletonList("Can write"));
		assertTrue("Can write in test directory", marker.isFile());
		FileUtils.delete(marker);
		assertFalse("Can delete in test direcory", marker.exists());

		Git git = Git.init().setBare(false)
				.setDirectory(new File(wcTree.getAbsolutePath())).call();

		addRepoToClose(git.getRepository());

		assertEquals("", errorOutput.toString());
	}

	@Override
	@After
	public void tearDown() throws Exception {
		System.setSecurityManager(originalSecurityManager);
		System.setErr(defaultErrorOutput);
		super.tearDown();
	}

	/**
	 * Refresh the Java Security Policy.
	 *
	 * @param policy
	 *            the policy object
	 *
	 * @throws IOException
	 *             if the temporary file that contains the policy could not be
	 *             created
	 */
	private static void refreshPolicyAllPermission(Policy policy)
			throws IOException {
		// Starting with an all permissions policy.
		String policyString = "grant { permission java.security.AllPermission; };";

		// Do not use TemporaryFilesFactory, it will create a dependency cycle
		Path policyFile = Files.createTempFile("testpolicy", ".txt");

		try {
			Files.write(policyFile, Collections.singletonList(policyString));
			System.setProperty("java.security.policy",
					policyFile.toUri().toURL().toString());
			policy.refresh();
		} finally {
			try {
				Files.delete(policyFile);
			} catch (IOException e) {
				// Do not log; the test tests for no logging having occurred
				e.printStackTrace();
			}
		}
	}

}
