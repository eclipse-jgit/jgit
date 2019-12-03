/*
 * Copyright (c) 2019 Alex Jitianu <alex_jitianu@sync.ro>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.Policy;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SecurityManagerMissingPermissionsTest extends RepositoryTestCase {
	/**
	 * Collects all logging sent to the logging system.
	 */
	private StringWriter errorOutputWriter = new StringWriter();
	/**
	 * Appender to intercept all logging sent to the logging system.
	 */
	private WriterAppender appender;

	@Override
	@Before
	public void setUp() throws Exception {
		refreshPolicy(Policy.getPolicy());
		System.setSecurityManager(new SecurityManager());

		appender = new WriterAppender(
				new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN),
				errorOutputWriter);

		org.apache.log4j.Logger.getRootLogger().addAppender(appender);

		super.setUp();
	}

	/**
	 * If a SecurityManager is active a lot of {@link java.io.FilePermission}
	 * are thrown while initializing a repository.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCreateNewRepos_MissingPermissions() throws Exception {
		File wcTree = new File(getTemporaryDirectory(), "CreateNewRepositoryTest_testCreateNewRepos");

		Git git = Git.init().setBare(false)
				.setDirectory(new File(wcTree.getAbsolutePath())).call();

		addRepoToClose(git.getRepository());

		assertEquals("", errorOutputWriter.toString());
	}

	@Override
	@After
	public void tearDown() throws Exception {
		super.tearDown();

		org.apache.log4j.Logger.getRootLogger().removeAppender(appender);
	}

	/**
	 * Refresh the Java Security Policy.
	 *
	 * @param policy
	 *            The policy object.
	 *
	 * @throws IOException
	 *             If the temporary file that contains the policy could not be
	 *             created.
	 */
	private static void refreshPolicy(Policy policy) throws IOException {
		// Starting with an all permissions policy.
		String policyString = "grant { permission java.security.AllPermission; };";

		// Do not use TemporaryFilesFactory, it will create a dependency cycle
		File policyFile = File.createTempFile("oxy_policy", ".txt");

		// Write the policy
		try (OutputStream fos = java.nio.file.Files
				.newOutputStream(policyFile.toPath())) {
			fos.write(policyString.getBytes("UTF-8"));
		}

		System.setProperty("java.security.policy",
				policyFile.toURI().toURL().toString());
		// Refresh the policy
		policy.refresh();
	}

}
