/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.fs.attribute.BranchDiff;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.niofs.internal.op.commands.Commit;
import org.eclipse.jgit.niofs.internal.op.commands.CreateBranch;
import org.eclipse.jgit.niofs.internal.op.commands.CreateRepository;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitFileSystemImplProviderDiffTest extends BaseTest {

	private Logger logger = LoggerFactory.getLogger(JGitFileSystemImplProviderDiffTest.class);

	@Test
	public void testDiffsBetweenBranches() throws IOException {

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, "repo.git");
		final Git origin = new CreateRepository(gitSource).execute().get();
		final Repository gitRepo = origin.getRepository();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt",
								tempFile("temp1\ntemp1\ntemp3\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\nother\n"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1\ntemp2\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\n"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-2", null, null, false,
				new HashMap<String, File>() {
					{
						put("file3.txt", tempFile("temp3"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-3", null, null, false,
				new HashMap<String, File>() {
					{
						put("file4.txt", tempFile("temp4"));
					}
				}).execute();

		new Commit(origin, "develop", "name", "name@example.com", "develop-4", null, null, false,
				new HashMap<String, File>() {
					{
						put("file5.txt", tempFile("temp5"));
					}
				}).execute();

		final URI newRepo = URI.create("git://diff-repo");

		final Map<String, Object> env = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME,
						origin.getRepository().getDirectory().toString());
			}
		};

		provider.newFileSystem(newRepo, env);

		final Path path = provider.getPath(newRepo);
		final BranchDiff branchDiff = (BranchDiff) provider.readAttributes(path, "diff:master,develop").get("diff");

		branchDiff.diffs().forEach(elem -> logger.info(elem.toString()));

		assertThat(branchDiff.diffs().size()).isEqualTo(5);
	}

	@Test
	public void testBranchesDoNotHaveDifferences() throws IOException {

		final File parentFolder = util.createTempDirectory();

		final File gitSource = new File(parentFolder, "repo.git");
		final Git origin = new CreateRepository(gitSource).execute().get();
		final Repository gitRepo = origin.getRepository();

		new Commit(origin, "master", "name", "name@example.com", "master-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt",
								tempFile("temp1\ntemp1\ntemp3\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\nother\n"));
					}
				}).execute();

		new Commit(origin, "master", "name", "name@example.com", "develop-1", null, null, false,
				new HashMap<String, File>() {
					{
						put("file1.txt", tempFile("temp1\ntemp2\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\n"));
					}
				}).execute();

		new CreateBranch((GitImpl) origin, "master", "develop").execute();

		final URI newRepo = URI.create("git://diff-repo");

		final Map<String, Object> env = new HashMap<String, Object>() {
			{
				put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME,
						origin.getRepository().getDirectory().toString());
			}
		};

		provider.newFileSystem(newRepo, env);

		final Path path = provider.getPath(newRepo);
		final BranchDiff branchDiff = (BranchDiff) provider.readAttributes(path, "diff:master,develop").get("diff");

		branchDiff.diffs().forEach(elem -> logger.info(elem.toString()));

		assertThat(branchDiff.diffs().size()).isEqualTo(0);
	}
}
