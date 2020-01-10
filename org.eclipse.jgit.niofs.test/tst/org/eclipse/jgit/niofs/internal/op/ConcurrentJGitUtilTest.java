/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Date;
import java.util.HashMap;

import org.eclipse.jgit.niofs.internal.AbstractTestInfra;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(BMUnitRunner.class)
@BMUnitConfig(loadDirectory = "target/test-classes", debug = true) // set "debug=true to see debug output
public class ConcurrentJGitUtilTest extends AbstractTestInfra {

	@BeforeClass
	public static void setup() {
		GitImpl.setRetryTimes(5);
	}

	@Test
	@BMScript(value = "byteman/retry/resolve_path.btm")
	public void testRetryResolvePath() throws IOException {
		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		try {
			assertNotNull(git.getPathInfo("master", "path/to/file1.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file1.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file1.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file1.txt"));
		} catch (Exception ex) {
			fail();
		}

		try {
			git.getPathInfo("master", "path/to/file1.txt");
			fail("forced to fail!");
		} catch (RuntimeException ex) {
		}
	}

	@Test
	@BMScript(value = "byteman/retry/resolve_inputstream.btm")
	public void testRetryResolveInputStream() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		try {
			assertNotNull(git.blobAsInputStream("master", "path/to/file1.txt"));
			assertNotNull(git.blobAsInputStream("master", "path/to/file1.txt"));
			assertNotNull(git.blobAsInputStream("master", "path/to/file1.txt"));
			assertNotNull(git.blobAsInputStream("master", "path/to/file1.txt"));
		} catch (Exception ex) {
			fail();
		}

		try {
			assertNotNull(git.blobAsInputStream("master", "path/to/file1.txt"));
			fail("forced to fail!");
		} catch (NoSuchFileException ex) {
		}
	}

	@Test
	@BMScript(value = "byteman/retry/list_path_content.btm")
	public void testRetryListPathContent() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		try {
			assertNotNull(git.listPathContent("master", "path/to/"));
			assertNotNull(git.listPathContent("master", "path/to/"));
			assertNotNull(git.listPathContent("master", "path/to/"));
			assertNotNull(git.listPathContent("master", "path/to/"));
		} catch (Exception ex) {
			fail();
		}

		try {
			assertNotNull(git.listPathContent("master", "path/to/"));
			fail("forced to fail!");
		} catch (RuntimeException ex) {
		}
	}

	@Test
	@BMScript(value = "byteman/retry/check_path.btm")
	public void testRetryCheckPath() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		try {
			assertNotNull(git.getPathInfo("master", "path/to/file2.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file2.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file2.txt"));
			assertNotNull(git.getPathInfo("master", "path/to/file2.txt"));
		} catch (Exception ex) {
			fail();
		}

		try {
			assertNotNull(git.getPathInfo("master", "path/to/file2.txt"));
			fail("forced to fail!");
		} catch (RuntimeException ex) {
		}
	}

	@Test
	@BMScript(value = "byteman/retry/get_last_commit.btm")
	public void testRetryGetLastCommit() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		try {
			assertNotNull(git.getLastCommit("master"));
			assertNotNull(git.getLastCommit("master"));
			assertNotNull(git.getLastCommit("master"));
			assertNotNull(git.getLastCommit("master"));
		} catch (Exception ex) {
			fail();
		}

		try {
			assertNotNull(git.getLastCommit("master"));
			fail("forced to fail!");
		} catch (RuntimeException ex) {
		}
	}

	@Test
	@BMScript(value = "byteman/retry/get_commits.btm")
	public void testRetryGetCommits() throws IOException {

		final File parentFolder = createTempDirectory();
		final File gitFolder = new File(parentFolder, "mytest.git");

		final Git git = new CreateRepository(gitFolder).execute().get();

		new Commit(git, "master", "name", "name@example.com", "1st commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file1.txt", tempFile("temp2222"));
					}
				}).execute();
		new Commit(git, "master", "name", "name@example.com", "2nd commit", null, new Date(), false,
				new HashMap<String, File>() {
					{
						put("path/to/file2.txt", tempFile("temp2222"));
					}
				}).execute();

		final RevCommit commit = git.getLastCommit("master");
		try {
			assertNotNull(git.listCommits(null, commit));
			assertNotNull(git.listCommits(null, commit));
			assertNotNull(git.listCommits(null, commit));
		} catch (Exception ex) {
			fail();
		}

		try {
			assertNotNull(git.listCommits(null, commit));
			fail("forced to fail!");
		} catch (RuntimeException ex) {
		}
	}
}